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
     * - queue = 20：刻意做浅——不堆几千条任务把内存吃满；
     * - CallerRunsPolicy（2026-11-02 由 AbortPolicy 改）：池满时由提交线程自己执行，
     *   通知不再静默丢失。
     *
     * ⚠️ 选 CallerRunsPolicy 的取舍（前提变了必须重新评估，勿盲目沿用）：
     *   提交线程是「MQ 唯一消费线程」（@RabbitListener 未配 concurrentConsumers）。
     *   池满时它会被拉去串行执行通知，期间停止消费队列。
     *   - 当前短信宝为 Mock 化未认证通道，失败快（数百 ms），
     *     100 个中奖者约多占用 30s，且中奖记录与状态此时**早已落库**，
     *     只影响「下一次开奖请求的响应速度」，人工操作场景可接受。
     *   - 若将来短信宝正式接入且响应变慢（读超时 5s），
     *     100 人可能拖住消费线程约 500s —— 届时应改为
     *     「加大 queue + AbortPolicy + 失败落库重试」，而不是继续用 CallerRuns。
     *
     * 另外：拒绝点已不再是异常（CallerRuns 不抛异常）。
     * MqReceiver#notifyWinnersQuietly 与 ClaimServiceImpl 里的 try/catch 仍保留，
     * 用于兜住「线程池已关闭」等其它提交失败场景，属防御性代码。
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
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
