package org.example.lottery_system.service.ai.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 本地重排器(两阶段检索的第二阶段:精排)
 *
 * 背景:业界标准 RAG 是「向量召回(粗排) → Rerank 模型(精排)」。
 * 但 Spring AI 1.0.9 / 1.1.8 均未内置 ReRanker 抽象,且第三方 Rerank API(Cohere / BGE-Reranker)
 * 需要额外 key。因此这里实现「本地轻量重排」:用查询与文档的字符级 bigram 重叠度打分,
 * 作为无外部依赖的替代方案;将来接入真实 Rerank 模型时,只需替换本类实现。
 *
 * 同时实现 DocumentPostProcessor 扩展点,便于将来直接挂到 RetrievalAugmentationAdvisor 上。
 */
@Component
public class LocalReRanker implements DocumentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(LocalReRanker.class);

    /**
     * 重排:按「查询-文档」bigram 重叠度降序,取前 topN
     *
     * @param queryText 用户查询
     * @param documents 向量召回的候选文档
     * @param topN      返回条数(<=0 表示不截断)
     */
    public List<Document> rerank(String queryText, List<Document> documents, int topN) {
        if (documents == null || documents.isEmpty()) {
            return documents == null ? new ArrayList<>() : documents;
        }
        Set<String> queryGrams = bigrams(queryText);

        List<Document> ranked = new ArrayList<>(documents);
        ranked.sort((a, b) -> Double.compare(score(queryGrams, b), score(queryGrams, a)));

        List<Document> result = (topN > 0 && ranked.size() > topN)
                ? new ArrayList<>(ranked.subList(0, topN))
                : ranked;

        log.info("本地重排: 候选 {} 条 → 输出 {} 条", documents.size(), result.size());
        return result;
    }

    @Override
    public List<Document> process(Query query, List<Document> documents) {
        String text = query == null ? "" : query.text();
        return rerank(text, documents, 0);
    }

    /** 计算文档与查询的词面重叠度(命中 bigram 数 / 查询 bigram 数) */
    private double score(Set<String> queryGrams, Document doc) {
        if (queryGrams.isEmpty() || doc == null || doc.getText() == null) {
            return 0;
        }
        String text = doc.getText();
        int hit = 0;
        for (String gram : bigrams(text)) {
            if (queryGrams.contains(gram)) {
                hit++;
            }
        }
        return (double) hit / queryGrams.size();
    }

    /** 字符级 bigram(只保留中英文数字,过滤标点空白) */
    private Set<String> bigrams(String text) {
        Set<String> grams = new HashSet<>();
        if (text == null) {
            return grams;
        }
        String cleaned = text.replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9]", "");
        for (int i = 0; i + 1 < cleaned.length(); i++) {
            grams.add(cleaned.substring(i, i + 2));
        }
        return grams;
    }
}
