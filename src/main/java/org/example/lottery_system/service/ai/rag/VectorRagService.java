package org.example.lottery_system.service.ai.rag;

import org.example.lottery_system.dao.dataobject.FaqDO;
import org.example.lottery_system.dao.mapper.FaqMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 框架化向量检索服务(在线阶段)
 *
 * 检索链路:向量召回(SimpleVectorStore) → 本地重排(LocalReRanker) → 组装知识上下文
 *           └─ 任一步异常 → 关键词检索降级(保证问答链路不中断)
 *
 * 与原有 RagService(手写实现)的关系:
 * - 本类为「框架化重构版」,通过 app.rag.mode=framework 启用
 * - 原 RagService 保留,可切换回 simple 模式做对照
 */
@Service
public class VectorRagService {

    private static final Logger log = LoggerFactory.getLogger(VectorRagService.class);

    @Autowired private SimpleVectorStore simpleVectorStore;
    @Autowired private LocalReRanker localReRanker;
    @Autowired private RagProperties props;
    @Autowired private FaqMapper faqMapper;

    /**
     * 检索并组装知识上下文(供提示词注入)
     */
    public String buildKnowledgeContext(String question) {
        if (question == null || question.isBlank()) {
            return "";
        }
        List<Document> documents = retrieveDocuments(question);
        if (documents.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            sb.append("【知识").append(i + 1).append("】")
              .append(documents.get(i).getText())
              .append("\n");
        }
        return sb.toString();
    }

    /**
     * 检索主流程:向量召回 → 重排;异常则降级关键词
     */
    public List<Document> retrieveDocuments(String question) {
        try {
            SearchRequest request = SearchRequest.builder()
                    .query(question)
                    .topK(props.getTopK())
                    .similarityThreshold(props.getMinScore())
                    .build();

            List<Document> recalled = simpleVectorStore.similaritySearch(request);
            if (recalled == null || recalled.isEmpty()) {
                log.warn("向量召回为空(可能索引未就绪或相似度低于阈值 {}),降级关键词检索", props.getMinScore());
                return keywordFallback(question);
            }
            log.info("向量召回 {} 条", recalled.size());

            // 第二阶段:重排(精排)
            if (props.isRerankEnabled()) {
                return localReRanker.rerank(question, recalled, props.getRerankTopN());
            }
            return recalled;
        } catch (Exception e) {
            // 降级兜底:向量库/Embedding 不可用时,退化为关键词检索
            log.warn("向量检索失败,降级关键词检索: {}", e.getMessage());
            return keywordFallback(question);
        }
    }

    /**
     * 关键词兜底检索:
     * 1. 把问题切成两字词(bigram),多词 OR 匹配数据库
     * 2. 用 bigram 重叠度排序,取前 rerankTopN 条
     */
    private List<Document> keywordFallback(String question) {
        List<String> keywords = extractKeywords(question);
        if (keywords.isEmpty()) {
            return new ArrayList<>();
        }
        List<FaqDO> candidates = faqMapper.searchByKeyword(keywords, Math.max(props.getTopK() * 5, 20));
        if (candidates == null || candidates.isEmpty()) {
            log.info("关键词兜底命中 0 条");
            return new ArrayList<>();
        }

        Set<String> queryGrams = bigrams(question);
        candidates.sort(Comparator.comparingInt((FaqDO f) -> overlap(queryGrams, f.getQuestion())).reversed());

        int limit = Math.min(props.getRerankTopN() > 0 ? props.getRerankTopN() : props.getTopK(), candidates.size());
        List<Document> result = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            FaqDO faq = candidates.get(i);
            result.add(new Document("问题:" + faq.getQuestion() + "\n答案:" + faq.getAnswer()));
        }
        log.info("关键词兜底命中 {} 条,取前 {} 条", candidates.size(), result.size());
        return result;
    }

    private List<String> extractKeywords(String question) {
        String cleaned = question.replaceAll("[^\\u4e00-\\u9fa5]", "");
        if (cleaned.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> stopWords = List.of("请问", "我想", "想要", "怎么", "如何", "怎样", "可以",
                "能否", "一个", "一下", "什么", "哪些", "那个", "这个", "有没有", "知道", "了解");
        Set<String> grams = new LinkedHashSet<>();
        for (int i = 0; i + 1 < cleaned.length(); i++) {
            grams.add(cleaned.substring(i, i + 2));
        }
        grams.removeAll(stopWords);
        return new ArrayList<>(grams);
    }

    private Set<String> bigrams(String text) {
        Set<String> grams = new HashSet<>();
        String cleaned = text == null ? "" : text.replaceAll("[^\\u4e00-\\u9fa5]", "");
        for (int i = 0; i + 1 < cleaned.length(); i++) {
            grams.add(cleaned.substring(i, i + 2));
        }
        return grams;
    }

    private int overlap(Set<String> queryGrams, String text) {
        if (text == null) {
            return 0;
        }
        String cleaned = text.replaceAll("[^\\u4e00-\\u9fa5]", "");
        int count = 0;
        for (int i = 0; i + 1 < cleaned.length(); i++) {
            if (queryGrams.contains(cleaned.substring(i, i + 2))) {
                count++;
            }
        }
        return count;
    }
}
