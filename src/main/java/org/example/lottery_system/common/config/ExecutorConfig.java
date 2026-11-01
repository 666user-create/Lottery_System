package org.example.lottery_system.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步线程池配置。
 *
 * <p>本类参数刻意写在代码里（不读 application.properties）：
 * 它们属于「结构性保护阈值」而非「随环境变化的容量参数」；项目单机部署、只有一个读者（本类），
 * 参数与拒绝策略放在一起可读性更好，也避免「配置文件和代码各有一份」的两处真相。
 * 若将来需要多环境差异化，再整体抽成 @ConfigurationProperties 并保留字段默认值。
 */
@Configuration
public class ExecutorConfig {

    /*
     * 短信通知池（原 properties 的 async.executor.thread.*，已内聚到此）。
     *
     * 负载特征：外部 HTTP（短信宝），单次数百 ms ~ 数秒；开奖时脉冲式突发。
     * - core  = 10：覆盖日常通知量，常驻线程；
     * - max   = 20：开奖一次性几十个中奖者时临时扩容，峰值不排太久；
     * - queue = 20：刻意做浅——宁可快速拒绝并告警，也不堆几千条任务把内存吃满；
     * - AbortPolicy：池满抛 RejectedExecutionException，由调用方兜底。
     *
     * ⚠️ 拒绝是「同步抛回调用线程」的（@Async 代理在提交阶段抛出 TaskRejectedException），
     * 因此 MqReceiver 中的通知段必须独立 try/catch，绝不能让它连带回滚开奖结果。
     * 详见 MqReceiver#notifyWinnersQuietly。
     */
    private static final int CORE_POOL_SIZE = 10;
    private static final int MAX_POOL_SIZE = 20;
    private static final int QUEUE_CAPACITY = 20;
    private static final int KEEP_ALIVE_SECONDS = 3;
    private static final String THREAD_NAME_PREFIX = "async-service";

    @Bean(name = "asyncServiceExecutor")
    public ThreadPoolTaskExecutor asyncServiceExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setKeepAliveSeconds(KEEP_ALIVE_SECONDS);
        executor.setThreadNamePrefix(THREAD_NAME_PREFIX);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
