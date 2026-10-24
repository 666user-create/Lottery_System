package org.example.lottery_system.common.config;

import org.example.lottery_system.service.ai.McpTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * MCP Server 配置:把 McpTools 里的 @Tool 方法注册为 MCP 标准工具。
 *
 * 注册后:
 * 1. 本项目通过 spring-ai-starter-mcp-server-webmvc 暴露 MCP 端点,
 *    任何支持 MCP 的 AI 客户端(Claude Desktop、其他 AI 应用)都能调用这些工具。
 * 2. 项目内部的客服实现(AiCustomerService / FrameworkChatService)也用同一批工具,
 *    但走的是 {@code InternalToolCallbacks}(见该类注释),拿到的是【全部】工具。
 *
 * ⚠️ 对外 / 对内工具集不同(本次调整):
 *   - 对外(MCP Server):只暴露【通用数据查询】工具(活动、奖品、中奖名单、FAQ)
 *   - 对内(站内客服):额外包含【个人信息类】工具(如 queryMyWinningRecords)
 *
 * 原因:个人信息类工具靠 ToolContext 里的"当前登录用户"工作,而外部 MCP 客户端
 * 没有登录上下文,调用它只能得到"请先登录",语义混乱且容易误导对方 AI。
 *
 * 约定:个人信息类工具以 {@code McpTools.PERSONAL_TOOL_PREFIX}(queryMy)开头。
 */
@Configuration
public class McpConfig {

    private static final Logger log = LoggerFactory.getLogger(McpConfig.class);

    /**
     * 对外暴露给 MCP 客户端的工具(已剔除个人信息类)。
     *
     * ToolCallbackProvider 只有一个抽象方法,所以可以直接用 lambda 返回过滤后的数组。
     */
    @Bean
    public ToolCallbackProvider lotteryMcpTools(McpTools mcpTools) {
        ToolCallback[] all = MethodToolCallbackProvider.builder()
                .toolObjects(mcpTools)
                .build()
                .getToolCallbacks();

        ToolCallback[] external = Arrays.stream(all)
                .filter(callback -> !McpTools.isPersonalTool(callback.getToolDefinition().name()))
                .toArray(ToolCallback[]::new);

        log.info("MCP Server 对外暴露工具 {} 个(总 {} 个,已排除个人信息类 {} 个)",
                external.length, all.length, all.length - external.length);
        return () -> external;
    }
}
