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
* 2.采用 AbortPolicy + 调用方显式捕获，队列满时丢弃日志而不是阻塞请求线程。
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
        //队列满时直接拒绝，由调用方捕获并丢弃，绝不阻塞 HTTP 请求线程
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
