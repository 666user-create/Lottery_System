package org.example.lottery_system.common.config;

import org.example.lottery_system.common.interceptor.LoginInterceptor;
import org.example.lottery_system.common.interceptor.OperationLogInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

@Configuration
public class AppConfig implements WebMvcConfigurer {
    @Autowired
    private LoginInterceptor loginInterceptor;
    @Autowired
    private OperationLogInterceptor operationLogInterceptor;

    private final List<String> excludes = Arrays.asList(
            "/**/*.html",
            "/css/**",
            "/js/**",
            "/pic/**",
            "/*.jpg",
            "/favicon.ico",
            "/user/password/login",
            "/user/message/login",
            "/user/register",
            "/user/verification-code/send",
            "/draw/winning-records/show",
            "/api/chat/**",
            "/api/ai/chat/**",
            // MCP Server 端点(SSE 传输):任何 MCP 客户端要能无鉴权发现和调用工具
            "/sse",
            "/mcp/message"
    );

    /*
     * 操作日志拦截器的排除清单：只排除静态资源与 MCP 端点。
     *
     * 注意不能复用上面的 excludes —— 那里面包含 /user/password/login、/user/register、
     * /user/verification-code/send 等公开接口，若一并排除，登录、注册、发码这些
     * 最需要审计的事件就全部记不到了。
     * 语义为查询的 /draw/winning-records/show 由拦截器内部的 EXCLUDED_PATHS 排除。
     * */
    private final List<String> operationLogExcludes = Arrays.asList(
            "/**/*.html",
            "/css/**",
            "/js/**",
            "/pic/**",
            "/*.jpg",
            "/favicon.ico",
            "/sse",
            "/mcp/message"
    );

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 操作日志拦截器必须注册在 LoginInterceptor 之前：
        // Spring 只会对 preHandle 返回 true 的拦截器调用 afterCompletion，
        // 注册在前才能把被 401/403 拒绝的请求也记下来。
        registry.addInterceptor(operationLogInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(operationLogExcludes);
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(excludes);
    }
}
