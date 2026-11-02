package org.example.lottery_system.common.config;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * 把提交线程的 SLF4J MDC 上下文（含 traceId / spanId）复制到异步执行线程。
 *
 * <p>为什么需要（2026-11-02 补充）：
 * MDC 底层是 ThreadLocal，异步线程默认拿不到提交线程的上下文。
 * 因此 {@code async-service-*}、{@code operation-log-*} 线程里的日志
 * 方括号永远是空的（实测：{@code [async-service1] [] INFO QQEmailServiceImpl - 纯文本邮件发送成功}），
 * 导致「异步链路的日志无法用 traceId 串回主链路」。
 *
 * <p>接上本装饰器后，异步线程的日志也会带上提交时刻的 traceId，
 * 与 {@link TracingMdcConfig} 注册的 MDCScopeDecorator 配合，形成完整的日志-链路对应关系。
 *
 * <p>实现要点：
 * <ol>
 *   <li><b>提交时快照</b>：{@code decorate()} 在提交线程执行，此时拷贝 MDC，拿到的是
 *       提交那一刻的上下文；真正执行时才写入子线程，避免跨线程读写同一 Map。</li>
 *   <li><b>finally 清理</b>：线程池会复用线程，不清理会把上一个任务的 traceId
 *       泄漏给下一个无关任务（错误关联比没有关联更糟）。</li>
 *   <li><b>空上下文短路</b>：无线程池调用（如 main 线程启动期）时 MDC 为空，
 *       直接执行，不产生额外开销。</li>
 * </ol>
 *
 * <p>成本：每个任务多一次 {@code HashMap} 拷贝（几条键值），相对短信 HTTP 调用可忽略。
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        // 在「提交线程」中取快照（本方法由提交方调用，而非执行方）
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            if (contextMap == null || contextMap.isEmpty()) {
                // 无线程上下文：直接执行，不做无谓的 set/clear
                runnable.run();
                return;
            }
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                MDC.setContextMap(contextMap);
                runnable.run();
            } finally {
                // 恢复而非直接 clear：兼容「异步线程本身就带上下文」的嵌套场景
                if (previous == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(previous);
                }
            }
        };
    }
}
