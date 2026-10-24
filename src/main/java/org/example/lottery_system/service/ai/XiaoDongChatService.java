package org.example.lottery_system.service.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.lottery_system.dao.mapper.ActivityMapper;
import org.example.lottery_system.dao.mapper.PrizeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 小东智能体 — 手动实现 Tool Calling，兼容 DeepSeek API
 *
 * ⚠️【保留说明】本类是项目早期的【手写】工具调用实现(TOOLS JSON + 解析循环)，
 * 现已迁移到 MCP 标准方案(见 McpTools + McpConfig + AiCustomerService)。
 * 按项目要求，手写代码保留在仓库中作为参考/回退方案，【不删除】。
 * 新功能开发请基于 MCP 方案，不要再扩展本类的工具。
 */
@Service
public class XiaoDongChatService {

    private static final Logger log = LoggerFactory.getLogger(XiaoDongChatService.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    @Value("${spring.ai.openai.chat.options.model:deepseek-chat}")
    private String model;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired private ActivityMapper activityMapper;
    @Autowired private PrizeMapper prizeMapper;

    // ==================== Tool Definitions (OpenAI Function Calling format) ====================

    /*
     * 安全修复（2026-10-10）：移除了原有的 queryUsers 工具。
     *
     * 原因：/api/chat 在 AppConfig 的免登录白名单内，任何匿名访客都能对话；
     * 而 queryUsers 会返回「ID | 姓名 | 邮箱 | 身份」，邮箱在库中是明文存储，
     * 等于把全量用户邮箱开放给未登录用户（同一份数据在管理端接口是要求 ADMIN 的）。
     * 智能客服面向普通用户，不需要也无法安全地提供用户名单，故整体移除。
     * 管理端如需查用户，请走 /user/base-user/find-list（已受 adminOnly 保护）。
     * */
    private static final List<Map<String, Object>> TOOLS = List.of(
        tool("queryAllActivities", "查询所有活动列表，包含活动名称、描述、状态（进行中/已完成）",
            Map.of("type", "object", "properties", Map.of())),
        tool("queryAllPrizes", "查询所有奖品列表，包含奖品名称、描述、价格",
            Map.of("type", "object", "properties", Map.of()))
    );

    private static Map<String, Object> tool(String name, String desc, Map<String, Object> params) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("type", "function");
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("name", name);
        f.put("description", desc);
        f.put("parameters", params);
        t.put("function", f);
        return t;
    }

    // ==================== Tool Executors ====================

    private String executeTool(String name, Map<String, Object> args) {
        log.info("小东执行工具: {} args={}", name, args);
        return switch (name) {
            case "queryAllActivities" -> queryAllActivities();
            case "queryAllPrizes" -> queryAllPrizes();
            default -> "未知工具: " + name;
        };
    }

    private String queryAllActivities() {
        int total = activityMapper.count();
        if (total == 0) return "暂无任何活动";
        return activityMapper.selectActivityList(0, total).stream()
                .map(a -> String.format("ID:%d | %s | %s | %s", a.getId(), a.getActivityName(), a.getDescription(), a.getStatus()))
                .collect(Collectors.joining("\n"));
    }

    private String queryAllPrizes() {
        int total = prizeMapper.count();
        if (total == 0) return "暂无任何奖品";
        return prizeMapper.selectPrizeList(0, total).stream()
                .map(p -> String.format("ID:%d | %s | %s | ¥%.2f", p.getId(), p.getName(), p.getDescription(), p.getPrice()))
                .collect(Collectors.joining("\n"));
    }

    // ==================== Chat ====================

    private static final String SYSTEM_PROMPT = """
        你是"小东"，抽奖系统的智能客服助手。你可以通过工具函数查询系统数据。

        回答规则：
        1. 用户询问活动/奖品信息时，先调用工具函数获取真实数据再回答
        2. 用友好的语气回复，像热情的客服一样，适当使用表情符号
        3. 不要编造数据，只根据工具返回的真实数据回答
        4. 查询不到时诚实告知"暂时没有相关数据哦～"
        5. 回复简洁清晰
        """;

    /**
     * 同步对话（带工具调用循环）
     */
    public String chat(String userMessage) {
        try {
            List<Map<String, Object>> messages = new ArrayList<>();
            // system
            messages.add(msg("system", SYSTEM_PROMPT));
            // user
            messages.add(msg("user", userMessage));

            // 工具调用循环（最多3轮）
            for (int round = 0; round < 3; round++) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("model", model);
                body.put("messages", messages);
                body.put("tools", TOOLS);
                body.put("temperature", 0.7);
                body.put("max_tokens", 2000);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setBearerAuth(apiKey);

                ResponseEntity<Map> resp = restTemplate.exchange(
                        baseUrl + "/v1/chat/completions",
                        HttpMethod.POST,
                        new HttpEntity<>(body, headers),
                        Map.class);

                Map<String, Object> respBody = resp.getBody();
                if (respBody == null) return "小东：API 返回为空，请稍后再试～";

                List<Map<String, Object>> choices = (List<Map<String, Object>>) respBody.get("choices");
                if (choices == null || choices.isEmpty()) return "小东：API 返回异常，请稍后再试～";

                Map<String, Object> choice = choices.get(0);
                Map<String, Object> message = (Map<String, Object>) choice.get("message");

                // 检查是否有 tool_calls
                List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");
                if (toolCalls != null && !toolCalls.isEmpty()) {
                    // 添加 assistant 消息（含 tool_calls）
                    messages.add(message);
                    // 执行每个工具并添加 tool 消息
                    for (Map<String, Object> tc : toolCalls) {
                        Map<String, Object> func = (Map<String, Object>) tc.get("function");
                        String funcName = (String) func.get("name");
                        String funcArgs = (String) func.get("arguments");
                        Map<String, Object> args = mapper.readValue(funcArgs,
                                new TypeReference<Map<String, Object>>() {});
                        String result = executeTool(funcName, args);
                        // 添加 tool 响应
                        messages.add(Map.of(
                                "role", "tool",
                                "tool_call_id", tc.get("id"),
                                "content", result));
                        log.info("工具 {} 执行完成，结果长度: {}", funcName, result.length());
                    }
                    continue; // 下一轮
                }

                // 普通文本回复
                String content = (String) message.get("content");
                return content != null ? content : "小东：嗯...让我想想～";
            }
            return "小东：查询次数太多了，请简化一下问题吧～";
        } catch (Exception e) {
            log.error("小东对话异常", e);
            return "小东：抱歉，我暂时无法回答～（" + e.getMessage() + "）";
        }
    }

    // ==================== Streaming 透传 ====================

    /**
     * 流式对话 — 按有意义的分块发送，避免中文乱码
     */
    public Flux<String> chatStream(String userMessage) {
        String reply = chat(userMessage);
        // 按句子边界拆分（。！？\n），每个分块作为一个 SSE 事件
        String[] chunks = reply.split("(?<=[。！？\\n])");
        if (chunks.length == 0) {
            return Flux.just(reply);
        }
        return Flux.fromArray(chunks);
    }

    private static Map<String, Object> msg(String role, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }
}
