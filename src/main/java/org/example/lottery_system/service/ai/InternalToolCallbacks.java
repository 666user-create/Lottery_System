package org.example.lottery_system.service.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 站内客服用的工具集(全量,含个人信息类工具)。
 *
 * 为什么单独抽一个组件,而不是直接用 McpConfig 里的 ToolCallbackProvider Bean:
 *   Spring AI 的 MCP Server 自动配置会扫描容器里【所有】ToolCallbackProvider / ToolCallback Bean
 *   并把它们对外暴露。如果站内全量工具也做成 Bean,个人信息类工具就会重新泄漏给外部 MCP 客户端。
 *   所以这里用普通 @Component(不实现 ToolCallbackProvider),只给站内服务注入使用。
 *
 * 见 McpConfig:对外只暴露通用数据查询工具。
 */
@Component
public class InternalToolCallbacks {

    private static final Logger log = LoggerFactory.getLogger(InternalToolCallbacks.class);

    private final ToolCallback[] callbacks;

    public InternalToolCallbacks(McpTools mcpTools) {
        this.callbacks = MethodToolCallbackProvider.builder()
                .toolObjects(mcpTools)
                .build()
                .getToolCallbacks();
        long personal = Arrays.stream(callbacks)
                .filter(c -> McpTools.isPersonalTool(c.getToolDefinition().name()))
                .count();
        log.info("站内客服可用工具 {} 个(其中个人信息类 {} 个,不对外暴露)", callbacks.length, personal);
    }

    public ToolCallback[] get() {
        return callbacks;
    }
}
