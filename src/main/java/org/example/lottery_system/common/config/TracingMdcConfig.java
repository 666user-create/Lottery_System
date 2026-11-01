package org.example.lottery_system.common.config;

import brave.context.slf4j.MDCScopeDecorator;
import brave.propagation.CurrentTraceContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 把链路追踪的 traceId 注入 SLF4J MDC，让 logback 的 {@code %X{traceId}} 有值。
 *
 * <p>为什么需要这个类（2026-11-01 实测结论，不要删）：
 * Spring Boot 3.2.6 的 {@code BraveAutoConfiguration} 只创建
 * {@link ThreadLocalCurrentTraceContext} 并把容器里所有
 * {@code CurrentTraceContext.ScopeDecorator} Bean 追加进去
 * （常量池可见 {@code addScopeDecorator} 调用），但**它自己不会提供
 * MDCScopeDecorator Bean**，也不读 Brave 的 SPI。
 * 也就是说：只把 {@code %X{traceId}} 写进 logback pattern 是不够的，
 * MDC 里根本没有 traceId，日志会一直打印空值。
 *
 * <p>因此这里显式注册一个 {@link MDCScopeDecorator} Bean：
 * {@code BraveAutoConfiguration} 收集到该 Bean 后，在每次请求进入
 * scope 时执行 {@code MDC.put("traceId", ...)} / {@code spanId}，退出时还原。
 * 依赖来自已有的 {@code zipkin-reporter-brave} 传递依赖
 * {@code brave-context-slf4j}（pom 无需新增）。
 *
 * <p>查看方式：正常请求的日志行会变成
 * {@code 22:35:41 [http-nio-8080-exec-1] [a1b2c3d4...] INFO ...}；
 * 启动阶段还没进入任何 trace scope，方括号内为空，属正常现象。
 */
@Configuration
public class TracingMdcConfig {

    @Bean
    public CurrentTraceContext.ScopeDecorator mdcScopeDecorator() {
        // 注意返回类型是 ScopeDecorator 而非 MDCScopeDecorator：
        // MDCScopeDecorator.get() 的签名就是 ScopeDecorator，
        // BraveAutoConfiguration 注入的也是 List<ScopeDecorator>。
        return MDCScopeDecorator.get();
    }
}
