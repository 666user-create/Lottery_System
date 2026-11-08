package org.example.lottery_system.controller;

import org.example.lottery_system.common.pojo.CommonResult;
import org.example.lottery_system.common.utils.JWTUtil;
import org.example.lottery_system.service.ai.FrameworkChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * 智能客服对话接口 — SSE 流式响应
 *
 * <p>2026-11 重构：原先存在三套并行实现（XiaoDongChatService 手写智能体 /
 * AiCustomerService simple 手写 RAG / FrameworkChatService 框架化），两套 RAG 以
 * app.rag.mode 切换。现统一为 framework 一套（RAG Advisor + Jdbc 会话记忆 +
 * MCP 工具 + 敏感词过滤 + 相似度阈值），另两套已删除，避免同一逻辑维护两份、
 * 且切换模式会丢会话记忆（simple 存 Redis、framework 存 MySQL）。
 *
 * <p>若将来需要对照历史实现，见 git 历史提交 95aeb88 之前。
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    /** 框架化对话服务：RAG Advisor + Jdbc 记忆 + MCP 工具 + 敏感词 */
    @Autowired
    private FrameworkChatService frameworkChatService;

    /**
     * RAG 智能客服（SSE 流式）
     * POST /api/ai/chat  body: {"message":"怎么抽奖","sessionId":"sess-001"}
     * header 可选带 token（用于工具取"当前登录用户"）
     */
    @PostMapping(value = "/ai/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> aiChat(@RequestBody Map<String, String> body,
                                                @RequestHeader(value = "token", required = false) String token) {
        String message = body.getOrDefault("message", "");
        String sessionId = body.getOrDefault("sessionId", "");
        log.info("智能客服收到消息: {}", message);
        if (message.isBlank()) {
            return Flux.just(ServerSentEvent.<String>builder()
                    .data("小东：你好！请问有什么可以帮你的吗？😊").build());
        }
        Long userId = parseUserId(token);
        return frameworkChatService.chatStream(message, sessionId, userId)
                .map(chunk -> ServerSentEvent.<String>builder().data(chunk).build())
                .concatWithValues(ServerSentEvent.<String>builder().data("[DONE]").build())
                .onErrorResume(e -> {
                    log.error("SSE 流式输出异常", e);
                    return Flux.just(ServerSentEvent.<String>builder()
                            .data("小东：回答生成中断了，请重试～（" + e.getMessage() + "）").build());
                });
    }

    /** 同步版（备用）POST /api/ai/chat/sync */
    @PostMapping("/ai/chat/sync")
    public CommonResult<String> aiChatSync(@RequestBody Map<String, String> body,
                                           @RequestHeader(value = "token", required = false) String token) {
        String message = body.getOrDefault("message", "");
        String sessionId = body.getOrDefault("sessionId", "");
        Long userId = parseUserId(token);
        return CommonResult.success(frameworkChatService.chat(message, sessionId, userId));
    }

    /** 从 token 解析 userId，失败/未登录返回 null（客服接口允许匿名） */
    private Long parseUserId(String token) {
        return JWTUtil.getUserIdFromToken(token);
    }
}
