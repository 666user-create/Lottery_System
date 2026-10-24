package org.example.lottery_system.service.ai.rag;

import org.example.lottery_system.dao.dataobject.FaqDO;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MySQL FAQ 文档读取器(自实现 DocumentReader)
 *
 * 说明:
 * 1. Spring AI 官方(org.springframework.ai)没有内置 MySQL Reader,故自实现;
 *    Spring AI Alibaba 社区虽有 mysql-document-reader,但其依赖 spring-ai-commons 1.1.2,
 *    与本项目 spring-ai 1.0.9 版本线不一致,为避免二进制兼容风险改为自实现(约 50 行)。
 * 2. 职责单一:只把数据库记录转成 Document,不做切分、不做向量化。
 *
 * ETL 管道中的位置:  Reader(本类) → Transformer(切分) → VectorStore(向量化+持久化)
 */
public class FaqDocumentReader implements DocumentReader {

    private final List<FaqDO> faqs;

    public FaqDocumentReader(List<FaqDO> faqs) {
        this.faqs = faqs;
    }

    @Override
    public List<Document> get() {
        List<Document> documents = new ArrayList<>(faqs.size());
        for (FaqDO faq : faqs) {
            if (faq == null || faq.getQuestion() == null || faq.getAnswer() == null) {
                continue;
            }
            // 组装为利于检索的文本:问题 + 答案
            String text = "问题:" + faq.getQuestion() + "\n答案:" + faq.getAnswer();

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("faq_id", faq.getId());
            metadata.put("question", faq.getQuestion());
            metadata.put("category", faq.getCategory() == null ? "通用" : faq.getCategory());
            metadata.put("source", "mysql.faq");

            documents.add(new Document(text, metadata));
        }
        return documents;
    }
}
