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
     * 否则会误杀正常的大模型调用(手写实现 AiCustomerService / XiaoDongChatService 都在用)。
     *
     * 用 RestTemplateBuilder 构造(而不是 new RestTemplate()):
     * 这样会自动挂上 Micrometer 观测拦截器,Zipkin 里就能看到 `http post /v1/chat/completions`
     * 这个客户端 span,形成 "chat deepseek-chat → http post" 的嵌套链路;
     * 超时策略与原来一致(不设置,即用 JDK 默认)。
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
}
