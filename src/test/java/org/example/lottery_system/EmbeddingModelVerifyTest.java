package org.example.lottery_system;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 本地 ONNX Embedding 验证测试
 *
 * 目的:尽早验证「完全离线向量化」链路可用,不依赖任何 API key。
 * 运行: mvnw.cmd test -Dtest=EmbeddingModelVerifyTest
 *
 * 说明:禁用 RabbitMQ listener 自动启动,避免 60 秒连接超时拖慢测试。
 */
@SpringBootTest(properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.listener.direct.auto-startup=false"
})
class EmbeddingModelVerifyTest {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Test
    void testLocalEmbedding() {
        System.out.println("=== EmbeddingModel 实现类: " + embeddingModel.getClass().getName());

        float[] v1 = embeddingModel.embed("怎么参与抽奖");
        float[] v2 = embeddingModel.embed("如何参加活动");
        float[] v3 = embeddingModel.embed("今天天气怎么样");

        System.out.println("=== 向量维度: " + v1.length);
        System.out.println("=== 相似语义相似度(应接近 1): " + cosine(v1, v2));
        System.out.println("=== 无关语义相似度(应明显更低): " + cosine(v1, v3));
    }

    private double cosine(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
