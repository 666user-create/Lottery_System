package org.example.lottery_system.common.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    /**
     * 默认 RestTemplate:不加超时限制。
     *
     * 注意:DeepSeek 的对话补全请求耗时可能几十秒,不能用短超时,
     * 否则会误杀正常的大模型调用(framework 的 FrameworkChatService 也走这个 RestTemplate)。
     *
     * 用 RestTemplateBuilder 构造(而不是 new RestTemplate()):
     * 这样会自动挂上 Micrometer 观测拦截器,Zipkin 里就能看到 `http post /v1/chat/completions`
     * 这个客户端 span,形成 "chat deepseek-chat → http post" 的嵌套链路;
     * 超时策略与原来一致(不设置,即用 JDK 默认)。
     *
     * ⚠️ 不要给本 Bean 加短超时:它服务于大模型(几十秒正常)。
     *    外部 HTTP 网关类调用必须用自己的短超时实例,见 smsRestTemplate。
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }

    /**
     * Embedding 专用 RestTemplate:必须带超时。
     *
     * 原因:启动时要探测"远程向量服务是否可用"来决定走 simple 还是 framework。
     * 若用无超时的默认实例,一旦外网不可达,探测会一直挂住,拖慢甚至卡死启动。
     * Embedding 请求本身很短,给足 3 秒连接 / 6 秒读取即可。
     */
    @Bean("embeddingRestTemplate")
    public RestTemplate embeddingRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(6000);
        return new RestTemplate(factory);
    }

    /**
     * 短信网关专用 RestTemplate:必须带短超时（2026-11-02 修复）。
     *
     * <p>问题背景:SmsBaoServiceImpl 原先裸注入默认 restTemplate（无超时），
     * 而短信宝是「外部 HTTP 网关」，不可用时的正确语义是**快速失败**，
     * 不是无限等待。JDK 默认 connectTimeout/readTimeout 均为 0（永不超时），
     * 一旦网关不响应,DNS 解析卡住或服务挂掉,调用线程会一直挂起。
     *
     * <p>影响面（两处调用，风险不同）:
     * <ul>
     *   <li>{@code ClaimServiceImpl#notifyShipped} —— 修复前同步调用,跑在 HTTP 请求线程上。
     *       try/catch 只能捕获抛出的异常,**捕获不了线程挂起**;
     *       后果是管理员点「登记发货」后接口长时间不返回。（现已改为异步提交）</li>
     *   <li>{@code SMSServiceImpl#sendSmsAsync} —— 跑在 asyncServiceExecutor 池里,
     *       不阻塞请求线程,但会给池内线程带来最长 5 秒的占用。
     *       该池已于 2026-11-02 由 AbortPolicy 改为 CallerRunsPolicy:
     *       池满时改由 MQ 消费线程顺带执行,慢短信会拖慢队列消费（当前短信宝为
     *       Mock 化通道、失败快,代价可接受；若将来正式接入需重新评估）。</li>
     * </ul>
     *
     * <p>取值理由:短信宝是轻量 GET,正常 1 秒内返回;给 3 秒连接 + 5 秒读取,
     * 既能容忍正常抖动,又能在网关故障时快速失败并落到 WARN 日志。
     * 注意这里用 new RestTemplate(factory) 而非 Builder:
     * 短信调用不需要 AI span,避免在 Zipkin 里产生无意义的客户端 span。
     */
    @Bean("smsRestTemplate")
    public RestTemplate smsRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        return new RestTemplate(factory);
    }
}
