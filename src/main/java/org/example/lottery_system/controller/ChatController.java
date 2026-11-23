package org.example.lottery_system.controller;

import org.example.lottery_system.common.pojo.CommonResult;
import org.example.lottery_system.common.utils.JWTUtil;
import org.example.lottery_system.service.ai.FrameworkChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 智能客服对话接口 — 同步返回完整回答。
 *
 * <p>2026-11 起改为同步：原先 /api/ai/chat 返回 SSE 逐 token 推送、
 * 另有一个 /api/ai/chat/sync 备用端点。现在需求是"命中违禁词就返回固定句式"，
 * 内容安全必须在拿到完整回答后统一判定（流式下无法等全文再判，只能边流边打码，
 * 而打码不满足需求）。因此统一为同步返回，删掉 SSE 端点与冗余的 /sync 端点。
 *
 * <p>前端（chat.html / user-lobby.html）相应改为 fetch + resp.json()，逻辑从
 * "按 SSE 帧解析并逐块追加"简化为"取 data 字段一次性渲染"。
 *
 * <p>历史上还并存过 XiaoDongChatService（手写智能体）与 AiCustomerService
 * （simple 模式手写 RAG），均已删除，见 git 提交 ffabb11 之前。
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    /** 框架化对话服务：RAG Advisor + Jdbc 记忆 + MCP 工具 + 敏感词 */
    @Autowired
    private FrameworkChatService frameworkChatService;

    /**
     * RAG 智能客服（同步）
     * POST /api/ai/chat  body: {"message":"怎么抽奖","sessionId":"sess-001"}
     * header 可选带 token（用于工具取"当前登录用户"）
     *
     * 返回 data 为完整回答文本。命中敏感词时 data 为固定提示语，不返回原文。
     */
    @PostMapping("/ai/chat")
    public CommonResult<String> aiChat(@RequestBody Map<String, String> body,
                                       @RequestHeader(value = "token", required = false) String token) {
        String message = body.getOrDefault("message", "");
        String sessionId = body.getOrDefault("sessionId", "");
        log.info("智能客服收到消息: {}", message);
        if (message.isBlank()) {
            return CommonResult.success("小东：你好！请问有什么可以帮你的吗？😊");
        }
        Long userId = parseUserId(token);
        return CommonResult.success(frameworkChatService.chat(message, sessionId, userId));
    }

    /** 从 token 解析 userId，失败/未登录返回 null（客服接口允许匿名） */
    private Long parseUserId(String token) {
        return JWTUtil.getUserIdFromToken(token);
    }
}
