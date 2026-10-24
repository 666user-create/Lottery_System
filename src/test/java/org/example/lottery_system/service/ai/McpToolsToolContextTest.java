package org.example.lottery_system.service.ai;

import org.example.lottery_system.dao.mapper.ActivityMapper;
import org.example.lottery_system.dao.mapper.FaqMapper;
import org.example.lottery_system.dao.mapper.HumanHandoverMapper;
import org.example.lottery_system.dao.mapper.PrizeMapper;
import org.example.lottery_system.dao.mapper.UserMapper;
import org.example.lottery_system.dao.mapper.WinningRecordMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.util.Arrays;
import java.util.Map;

/**
 * 复核:声明了 ToolContext 参数的工具,在"没有上下文"时框架会怎么表现?
 *
 * 背景:匿名用户对话时 FrameworkChatService 不传 toolContext;而已登录用户才传。
 * 若框架在"方法要求 ToolContext 但上下文为空"时直接抛异常,则匿名用户一旦触发该工具就会报错。
 *
 * 运行:mvnw.cmd test -Dtest=McpToolsToolContextTest
 */
@ExtendWith(MockitoExtension.class)
class McpToolsToolContextTest {

    @Mock private ActivityMapper activityMapper;
    @Mock private PrizeMapper prizeMapper;
    @Mock private UserMapper userMapper;
    @Mock private WinningRecordMapper winningRecordMapper;
    @Mock private HumanHandoverMapper handoverMapper;
    @Mock private FaqMapper faqMapper;

    @InjectMocks private McpTools mcpTools;

    private ToolCallback callbackFor(String name) {
        ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
                .toolObjects(mcpTools)
                .build()
                .getToolCallbacks();
        return Arrays.stream(callbacks)
                .filter(c -> c.getToolDefinition().name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未找到工具:" + name));
    }

    private String safeCall(ToolCallback cb, ToolContext ctx) {
        try {
            return ctx == null ? cb.call("{}") : cb.call("{}", ctx);
        } catch (Exception e) {
            return "【抛异常】" + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    @Test
    void anonymousContextShouldReturnFriendlyMessageInsteadOfThrowing() {
        ToolCallback cb = callbackFor("queryMyWinningRecords");

        // 1) 空上下文:框架会直接抛异常 —— 这是因为方法声明了 ToolContext 参数。
        //    因此 FrameworkChatService 必须"永远注入非空上下文"(见下一个断言)。
        Exception thrown = null;
        try {
            cb.call("{}", new ToolContext(Map.of()));
        } catch (Exception e) {
            thrown = e;
        }
        org.junit.jupiter.api.Assertions.assertNotNull(thrown,
                "空上下文时框架应当抛异常(这是必须永远注入非空上下文的原因)");
        System.out.println("空上下文 → " + thrown.getMessage());

        // 2) 匿名标记:必须返回友好话术,而不是抛异常(修复项)
        String anonymousReply = cb.call("{}", new ToolContext(Map.of("anonymous", Boolean.TRUE)));
        System.out.println("匿名标记 → " + anonymousReply);
        org.junit.jupiter.api.Assertions.assertTrue(anonymousReply.contains("未登录"),
                "匿名场景应提示未登录,实际返回: " + anonymousReply);

        // 3) 已登录:应正常返回查询结果
        String loggedInReply = cb.call("{}", new ToolContext(Map.of("userId", 999L)));
        System.out.println("已登录(userId=999)→ " + loggedInReply);
        org.junit.jupiter.api.Assertions.assertTrue(loggedInReply.contains("暂无中奖记录"),
                "已登录场景应返回查询结果,实际返回: " + loggedInReply);
    }

    @Test
    void toolContextMustNotBeExposedToModel() {
        ToolCallback cb = callbackFor("queryMyWinningRecords");
        String schema = cb.getToolDefinition().inputSchema();
        System.out.println("schema → " + schema);
        org.junit.jupiter.api.Assertions.assertFalse(schema.contains("toolContext"),
                "ToolContext 不应出现在工具参数 schema 中");
    }
}
