package org.example.lottery_system.service.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Embedding(向量化)客户端 —— 统一入口,支持两种向量来源:
 *
 *   provider=local (默认) 本地 ONNX 模型(bge-small-zh-v1.5,512 维),不需要 Key、不依赖外网
 *   provider=remote        远程 OpenAI 兼容 /embeddings(硅基流动 bge-m3,1024 维),需要 Key
 *
 * 为什么默认本地:
 *   1. 远程服务需要 API Key,没配时 simple 模式的 faq_embedding 表永远是空的;
 *   2. 本地模型已经下载好,离线可用,启动即可用,不产生任何网络报错噪音。
 *
 * 注意:simple 模式的向量存在 MySQL faq_embedding 表,framework 模式存在
 * data/vector-store.json —— 两者介质不同,但改用本地模型后"向量语义空间"一致。
 */
@Component
public class EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingClient.class);

    @Value("${ai.embedding.provider:local}")
    private String provider;

    @Value("${ai.embedding.base-url}")
    private String baseUrl;

    @Value("${ai.embedding.api-key}")
    private String apiKey;

    @Value("${ai.embedding.model:BAAI/bge-m3}")
    private String model;

    /** 用带超时的实例:外网不可达时快速失败,避免卡住启动/请求 */
    @Autowired
    @Qualifier("embeddingRestTemplate")
    private RestTemplate restTemplate;

    /** 本地 ONNX 向量模型(spring.ai.model.embedding=transformers) */
    @Autowired(required = false)
    private EmbeddingModel localEmbeddingModel;

    /** 是否使用本地模型 */
    public boolean isLocalProvider() {
        return !"remote".equalsIgnoreCase(provider == null ? "" : provider.trim());
    }

    /** 是否配置了远程 API Key */
    public boolean isApiKeyConfigured() {
        return StringUtils.hasText(apiKey);
    }

    /** 当前配置下向量能力是否可用(本地模式只需模型就绪;远程模式需要 Key) */
    public boolean isUsable() {
        if (isLocalProvider()) {
            return localEmbeddingModel != null;
        }
        return isApiKeyConfigured();
    }

    /** 当前向量维度说明(日志/排查用) */
    public String describe() {
        return isLocalProvider()
                ? "本地模型(512 维)"
                : "远程 " + baseUrl + "/embeddings(" + model + ")";
    }

    /**
     * 把一段文字变成向量(浮点数数组)
     *
     * 本地模式:调用本地 ONNX 模型(不会失败,不需要网络)
     * 远程模式:调不通或没 Key 时抛异常,由调用方决定兜底策略
     */
    public double[] embed(String text) {
        if (isLocalProvider()) {
            if (localEmbeddingModel == null) {
                throw new IllegalStateException("本地 EmbeddingModel 未就绪(检查 spring.ai.model.embedding=transformers)");
            }
            float[] vector = localEmbeddingModel.embed(text);
            double[] result = new double[vector.length];
            for (int i = 0; i < vector.length; i++) {
                result[i] = vector[i];
            }
            if (log.isDebugEnabled()) {
                log.debug("本地 Embedding 成功: 文本长度={}, 向量维度={}", text.length(), result.length);
            }
            return result;
        }
        return embedByRemote(text);
    }

    /** 远程 Embedding(OpenAI 兼容 /embeddings) */
    private double[] embedByRemote(String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", text);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl + "/embeddings",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class);

        Map<String, Object> respBody = resp.getBody();
        if (respBody == null || respBody.get("data") == null) {
            throw new RuntimeException("Embedding 接口返回为空: " + resp.getStatusCode());
        }
        List<Map<String, Object>> data = (List<Map<String, Object>>) respBody.get("data");
        List<Double> embedding = (List<Double>) data.get(0).get("embedding");

        double[] result = new double[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            result[i] = embedding.get(i);
        }
        log.info("远程 Embedding 成功: 文本长度={}, 向量维度={}", text.length(), result.length);
        return result;
    }
}
