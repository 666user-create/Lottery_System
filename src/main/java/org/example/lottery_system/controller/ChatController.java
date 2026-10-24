package org.example.lottery_system.controller;

import io.jsonwebtoken.Claims;
import org.example.lottery_system.common.pojo.CommonResult;
import org.example.lottery_system.common.utils.JWTUtil;
import org.example.lottery_system.service.ai.AiCustomerService;
import org.example.lottery_system.service.ai.FrameworkChatService;
import org.example.lottery_system.service.ai.RetrievalModeResolver;
import org.example.lottery_system.service.ai.XiaoDongChatService;
import org.example.lottery_system.service.ai.rag.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * 小东智能体对话接口 — SSE 流式响应
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    @Autowired
    private XiaoDongChatService xiaoDongService;

    @Autowired
    private AiCustomerService aiCustomerService;

    /** 框架化重构版对话服务(RAG Advisor + Jdbc 记忆 + MCP 工具 + 敏感词) */
    @Autowired
    private FrameworkChatService frameworkChatService;

    /** 检索模式开关:framework=框架化;simple=原手写实现;auto=自动探测(远程向量可用则 simple) */
    @Autowired
    private RagProperties ragProperties;

    /** 模式解析器:auto 模式下探测远程向量服务决定用哪套实现 */
    @Autowired
    private RetrievalModeResolver modeResolver;

    /**
     * SSE 流式聊天接口
     * 前端可用 EventSource 或 fetch + ReadableStream 消费
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(@RequestBody Map<String, String> body) {
        String message = body.getOrDefault("message", "");
        log.info("小东收到消息: {}", message);
        if (message.isBlank()) {
            return Flux.just(ServerSentEvent.<String>builder()
                    .data("小东：你好！请问有什么可以帮你的吗？😊")
                    .build());
        }
        return xiaoDongService.chatStream(message)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build())
                .concatWithValues(ServerSentEvent.<String>builder()
                        .data("[DONE]")
                        .build())
                .onErrorResume(e -> {
                    log.error("SSE流式输出异常", e);
                    return Flux.just(ServerSentEvent.<String>builder()
                            .data("小东：抱歉，回答生成中断了～请重试（" + e.getMessage() + "）")
                            .build());
                });
    }

    /**
     * 普通同步聊天接口（备用）
     */
    @PostMapping("/chat/sync")
    public CommonResult<String> chatSync(@RequestBody Map<String, String> body) {
        String message = body.getOrDefault("message", "");
        log.info("小东收到同步消息: {}", message);
        String reply = xiaoDongService.chat(message);
        return CommonResult.success(reply);
    }

    // ==================== 以下是新增:RAG 知识库客服接口 ====================

    /**
     * RAG 智能客服(SSE 流式)
     * POST /api/ai/chat  body: {"message":"怎么抽奖","sessionId":"sess-001"}
     * header 可选带 token(用于记录 userId)
     *
     * 实现选择:app.rag.mode=framework(默认,框架化) / simple(原手写实现)
     */
    @PostMapping(value = "/ai/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> aiChat(@RequestBody Map<String, String> body,
                                                @RequestHeader(value = "token", required = false) String token) {
        String message = body.getOrDefault("message", "");
        String sessionId = body.getOrDefault("sessionId", "");
        if (message.isBlank()) {
            return Flux.just(ServerSentEvent.<String>builder().data("小东：你好！请问有什么可以帮你的吗？😊").build());
        }
        Long userId = parseUserId(token);
        Flux<String> replyFlux = isFrameworkMode()
                ? frameworkChatService.chatStream(message, sessionId, userId)
                : aiCustomerService.chatStream(message, sessionId, userId);
        return replyFlux
                .map(chunk -> ServerSentEvent.<String>builder().data(chunk).build())
                .concatWithValues(ServerSentEvent.<String>builder().data("[DONE]").build())
                .onErrorResume(e -> Flux.just(ServerSentEvent.<String>builder()
                        .data("小东：回答生成中断了，请重试～（" + e.getMessage() + "）").build()));
    }

    /** 同步版(备用) POST /api/ai/chat/sync */
    @PostMapping("/ai/chat/sync")
    public CommonResult<String> aiChatSync(@RequestBody Map<String, String> body,
                                           @RequestHeader(value = "token", required = false) String token) {
        String message = body.getOrDefault("message", "");
        String sessionId = body.getOrDefault("sessionId", "");
        Long userId = parseUserId(token);
        String reply = isFrameworkMode()
                ? frameworkChatService.chat(message, sessionId, userId)
                : aiCustomerService.chat(message, sessionId, userId);
        return CommonResult.success(reply);
    }

    /**
     * 是否使用框架化检索模式
     *
     * 注意:不再是"读配置字符串"这么简单——app.rag.mode=auto 时会先探测远程向量服务:
     * 可用则 simple(原手写实现),连不上外网则自动回落 framework。
     * 因此统一交给 RetrievalModeResolver 判定(结果有缓存,不会每次请求都探测)。
     */
    private boolean isFrameworkMode() {
        return modeResolver.isFramework();
    }

    /** 从 token 解析 userId,失败返回 null */
    private Long parseUserId(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        try {
            Claims claims = JWTUtil.parseJWT(token);
            Object id = claims.get("id");
            return id != null ? Long.valueOf(id.toString()) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
