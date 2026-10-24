package org.example.lottery_system.service.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.lottery_system.dao.dataobject.FaqDO;
import org.example.lottery_system.dao.dataobject.FaqEmbeddingDO;
import org.example.lottery_system.dao.mapper.FaqEmbeddingMapper;
import org.example.lottery_system.dao.mapper.FaqMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG 检索服务:根据用户问题,从知识库找出最相关的几条 FAQ
 * 优先向量检索;向量不可用时自动降级为关键词检索
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${ai.rag.top-k:3}")
    private int topK;

    @Autowired private EmbeddingClient embeddingClient;
    @Autowired private FaqMapper faqMapper;
    @Autowired private FaqEmbeddingMapper faqEmbeddingMapper;

    /**
     * 检索和问题最相关的知识,拼成一段文本给 AI 参考
     * 返回形如:
     * 【知识1】Q:怎么参与抽奖? A:登录后...
     * 【知识2】...
     */
    public String buildKnowledgeContext(String question) {
        List<FaqDO> matched = retrieve(question);
        if (matched == null || matched.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < matched.size(); i++) {
            FaqDO faq = matched.get(i);
            sb.append("【知识").append(i + 1).append("】")
              .append("问题:").append(faq.getQuestion())
              .append(" 答案:").append(faq.getAnswer())
              .append("\n");
        }
        return sb.toString();
    }

    /**
     * 检索主流程:向量 → 关键词兜底
     */
    public List<FaqDO> retrieve(String question) {
        // 1. 先试向量检索
        try {
            List<FaqDO> vectorResult = retrieveByVector(question);
            if (vectorResult != null && !vectorResult.isEmpty()) {
                log.info("向量检索命中 {} 条", vectorResult.size());
                return vectorResult;
            }
        } catch (Exception e) {
            log.warn("向量检索失败,降级关键词: {}", e.getMessage());
        }
        // 2. 向量不行,用关键词兜底(先把问题切成词,再 OR 匹配)
        List<String> keywords = extractKeywords(question);
        if (keywords.isEmpty()) {
            return Collections.emptyList();
        }
        List<FaqDO> keywordResult = faqMapper.searchByKeyword(keywords, Math.max(topK * 5, 20));
        // 3. 用 bigram 重叠度给结果排序,把最相关的排前面
        List<FaqDO> ranked = rankByBigramOverlap(question, keywordResult);
        List<FaqDO> top = ranked.size() > topK ? ranked.subList(0, topK) : ranked;
        log.info("关键词兜底命中 {} 条,取前 {} 条", keywordResult.size(), top.size());
        return top;
    }

    /**
     * 从问题里提取关键词:去掉标点/语气词,切成连续的两字词(中文无空格分词,用 bigram 近似)
     * 例如 "请问怎么参与抽奖?" → [怎么, 参与, 抽奖]
     */
    private List<String> extractKeywords(String question) {
        // 1. 去掉所有非中文字符(英文/数字/标点/空格全去掉)
        String cleaned = question.replaceAll("[^\\u4e00-\\u9fa5]", "");
        if (cleaned.isEmpty()) {
            return Collections.emptyList();
        }
        // 2. 常见语气词/虚词,匹配时意义不大,去掉
        List<String> stopWords = Arrays.asList(
                "请问", "我想", "想要", "怎么", "如何", "怎样", "可以", "能否", "一个",
                "一下", "什么", "哪些", "那个", "这个", "有没有", "知道", "了解");
        // 3. 切 bigram,去重,过滤停用词
        Set<String> grams = new LinkedHashSet<>();
        for (int i = 0; i < cleaned.length() - 1; i++) {
            grams.add(cleaned.substring(i, i + 2));
        }
        grams.removeAll(stopWords);
        return new ArrayList<>(grams);
    }

    /**
     * 用 bigram 重叠度给候选 FAQ 排序:用户问题与 FAQ 问题的两字词交集越多越相关
     */
    private List<FaqDO> rankByBigramOverlap(String question, List<FaqDO> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return candidates == null ? Collections.emptyList() : candidates;
        }
        // 用户问题的 bigram 集合
        String cleanQ = question.replaceAll("[^\\u4e00-\\u9fa5]", "");
        Set<String> qGrams = new HashSet<>();
        for (int i = 0; i + 1 < cleanQ.length(); i++) {
            qGrams.add(cleanQ.substring(i, i + 2));
        }
        // 对每个候选算重叠度
        return candidates.stream()
                .sorted(Comparator.comparingInt((FaqDO f) -> overlapCount(qGrams, f.getQuestion()))
                        .reversed()
                        .thenComparing(FaqDO::getId, Comparator.reverseOrder()))
                .collect(Collectors.toList());
    }

    private int overlapCount(Set<String> qGrams, String faqQuestion) {
        if (faqQuestion == null) {
            return 0;
        }
        String clean = faqQuestion.replaceAll("[^\\u4e00-\\u9fa5]", "");
        int count = 0;
        for (int i = 0; i + 1 < clean.length(); i++) {
            if (qGrams.contains(clean.substring(i, i + 2))) {
                count++;
            }
        }
        return count;
    }

    /**
     * 向量检索:问题向量化 → 和所有 FAQ 向量算余弦相似度 → 取前 topK
     */
    private List<FaqDO> retrieveByVector(String question) {
        // 1. 问题向量化
        double[] queryVector = embeddingClient.embed(question);
        // 2. 加载全部向量
        List<FaqEmbeddingDO> all = faqEmbeddingMapper.selectAll();
        if (all == null || all.isEmpty()) {
            return Collections.emptyList();
        }
        // 3. 逐个算相似度,按分数从高到低排,取前 topK
        List<Map.Entry<Long, Double>> scored = all.stream()
                .map(e -> new AbstractMap.SimpleEntry<>(e.getFaqId(),
                        cosineSimilarity(queryVector, parseVector(e.getEmbedding()))))
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(topK)
                .collect(Collectors.toList());
        // 4. 用 faqId 批量查回 FAQ 内容
        List<Long> ids = scored.stream().map(Map.Entry::getKey).collect(Collectors.toList());
        List<FaqDO> faqs = faqMapper.batchSelectByIds(ids);
        // 5. 按相似度顺序返回
        Map<Long, FaqDO> byId = faqs.stream().collect(Collectors.toMap(FaqDO::getId, f -> f));
        return scored.stream()
                .map(Map.Entry::getKey)
                .map(byId::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /** 余弦相似度,越大越像(范围 -1~1) */
    private double cosineSimilarity(double[] a, double[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /** 把 JSON 数组字符串解析成 double[] */
    private double[] parseVector(String json) {
        try {
            List<Double> list = MAPPER.readValue(json, new TypeReference<List<Double>>() {});
            double[] arr = new double[list.size()];
            for (int i = 0; i < list.size(); i++) {
                arr[i] = list.get(i);
            }
            return arr;
        } catch (Exception e) {
            throw new RuntimeException("向量解析失败", e);
        }
    }
}
