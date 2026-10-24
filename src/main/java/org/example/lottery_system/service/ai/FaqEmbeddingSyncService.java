package org.example.lottery_system.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.example.lottery_system.dao.dataobject.FaqDO;
import org.example.lottery_system.dao.dataobject.FaqEmbeddingDO;
import org.example.lottery_system.dao.mapper.FaqEmbeddingMapper;
import org.example.lottery_system.dao.mapper.FaqMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * faq_embedding(MySQL 向量表)补全任务。
 *
 * 背景:simple 模式的检索依赖 faq_embedding 表里的向量,但该表此前一直是空的
 * (因为远程 Embedding 未配置 Key,新增 FAQ 时的写入全部失败),导致 simple 模式
 * 只能退化成关键词检索。
 *
 * 因此:当解析出的模式是 simple(远程向量可用)时,启动后在后台把缺失的 FAQ 向量补齐。
 * 用后台线程执行,避免几十次远程调用拖慢启动。
 */
@Service
public class FaqEmbeddingSyncService {

    private static final Logger log = LoggerFactory.getLogger(FaqEmbeddingSyncService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired private FaqMapper faqMapper;
    @Autowired private FaqEmbeddingMapper faqEmbeddingMapper;
    @Autowired private EmbeddingClient embeddingClient;

    @PostConstruct
    public void init() {
        // 后台执行:启动关键路径不受远程调用影响
        Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "faq-embedding-sync");
            t.setDaemon(true);
            return t;
        }).submit(() -> {
            try {
                syncMissingVectors();
            } catch (Exception e) {
                log.error("faq_embedding 补全任务异常(framework 模式不受影响)", e);
            }
        });
    }

    /**
     * 为缺失向量的 FAQ 生成并写入向量
     *
     * @return 本次成功补全的条数
     */
    public int syncMissingVectors() {
        // 向量能力不可用时跳过(例如 provider=remote 但没配 Key)
        if (!embeddingClient.isUsable()) {
            log.info("向量能力不可用({}),跳过 faq_embedding 补全", embeddingClient.describe());
            return 0;
        }
        log.info("开始检查 faq_embedding 缺失向量(向量来源: {})", embeddingClient.describe());

        List<FaqDO> faqs = faqMapper.selectAll();
        if (faqs == null || faqs.isEmpty()) {
            return 0;
        }
        Set<Long> existing = new HashSet<>();
        List<FaqEmbeddingDO> embeddings = faqEmbeddingMapper.selectAll();
        if (embeddings != null) {
            for (FaqEmbeddingDO e : embeddings) {
                existing.add(e.getFaqId());
            }
        }
        if (existing.size() >= faqs.size()) {
            log.info("faq_embedding 已是最新({}/{} 条),无需补全", existing.size(), faqs.size());
            return 0;
        }

        int success = 0;
        int failed = 0;
        for (FaqDO faq : faqs) {
            if (faq.getId() == null || existing.contains(faq.getId())) {
                continue;
            }
            try {
                double[] vector = embeddingClient.embed(faq.getQuestion());
                FaqEmbeddingDO record = new FaqEmbeddingDO();
                record.setFaqId(faq.getId());
                record.setEmbedding(MAPPER.writeValueAsString(vector));
                faqEmbeddingMapper.insert(record);
                success++;
            } catch (Exception e) {
                failed++;
                // 远程服务不稳定时不继续硬刷,直接停手,下次启动再补
                log.warn("FAQ[{}] 向量补全失败,停止本轮补全: {}", faq.getId(), e.getMessage());
                break;
            }
        }
        log.info("faq_embedding 补全完成: 新增 {} 条, 失败 {} 条, 原有 {} 条, FAQ 共 {} 条",
                success, failed, existing.size(), faqs.size());
        return success;
    }
}
