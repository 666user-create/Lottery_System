package org.example.lottery_system.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/*
* 操作日志专用线程池
*
* 刻意与 asyncServiceExecutor（中奖通知的邮件/短信）分开：
* 1.日志写入再慢也不会挤占业务通知的线程；
* 2.队列满时由 CallerRunsPolicy 兜住（2026-11-02 由 AbortPolicy 改）。
*
* 为什么这里用 CallerRunsPolicy（代价极低、收益明确）：
*   - 提交线程是 HTTP 请求线程，被拒绝的任务体只是一条 MySQL insert（毫秒级），
*     由请求线程顺带执行，代价仅几毫秒；
*   - 换来的是「审计日志不再静默丢失」——AbortPolicy 下被拒日志直接消失，
*     而操作日志的价值恰恰在于完整。
* 唯一风险：MySQL 不可用时 insert 会卡住请求线程；但那种场景下所有请求本来就失败，
* 不构成额外损失。若将来日志落库变重（如写 ES/远程服务），应重新评估此策略。
* */
@Configuration
public class OperationLogConfig {

    @Bean(name = "operationLogExecutor")
    public ThreadPoolTaskExecutor operationLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("operation-log-");
        // 把请求线程的 MDC（traceId）带到日志落库线程，便于按 traceId 追查日志写入
        executor.setTaskDecorator(new MdcTaskDecorator());
        //队列满时由调用方（HTTP 请求线程）自己执行这条 insert，绝不丢日志
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
