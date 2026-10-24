package org.example.lottery_system.controller;

import org.example.lottery_system.common.pojo.CommonResult;
import org.example.lottery_system.service.ai.rag.VectorRagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 检索诊断接口(管理员)
 *
 * 用途:
 * 1. 观察「向量召回 → 本地重排」的实际效果(命中哪些知识、顺序如何)
 * 2. 为 RAG 效果评估提供可量化入口(毕设实验章节可用)
 *
 * 权限:由 LoginInterceptor 强制 ADMIN(见其 adminOnly 判断)。
 */
@RestController
@RequestMapping("/api/ai/rag")
public class RagDiagnoseController {

    private static final Logger log = LoggerFactory.getLogger(RagDiagnoseController.class);

    @Autowired private VectorRagService vectorRagService;

    /**
     * GET /api/ai/rag/diagnose?q=怎么参与抽奖
     * 返回本次检索的命中知识、条数与耗时
     */
    @GetMapping("/diagnose")
    public CommonResult<Map<String, Object>> diagnose(@RequestParam("q") String query) {
        long start = System.currentTimeMillis();
        List<Document> documents = vectorRagService.retrieveDocuments(query);
        long cost = System.currentTimeMillis() - start;

        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            Map<String, Object> item = new HashMap<>();
            item.put("rank", i + 1);
            item.put("text", doc.getText());
            item.put("metadata", doc.getMetadata());
            items.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("query", query);
        result.put("hitCount", items.size());
        result.put("costMs", cost);
        result.put("items", items);
        log.info("RAG 诊断: query={}, 命中 {} 条, 耗时 {} ms", query, items.size(), cost);
        return CommonResult.success(result);
    }
}
