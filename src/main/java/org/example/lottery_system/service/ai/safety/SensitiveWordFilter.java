package org.example.lottery_system.service.ai.safety;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 敏感词过滤器(DFA / Trie 树实现)
 *
 * 用途:对 AI 客服做「输入 + 输出」双向内容过滤。
 * - 输入侧:命中敏感词直接拒答/转人工,不送往大模型
 * - 输出侧:模型回复命中敏感词时打码,避免不当内容外泄
 *
 * 为什么用 DFA 而不是正则/contains 循环:
 * 准确率相同(O(n) 扫描一遍,与词库大小无关),正则多词匹配会退化成 O(n*m)。
 *
 * 说明:这里的词库仅作演示(抽奖系统常见风控词)。
 * 生产环境应替换为完整词库文件或接入第三方内容安全服务。
 */
@Component
public class SensitiveWordFilter {

    private static final Logger log = LoggerFactory.getLogger(SensitiveWordFilter.class);

    /** DFA 树节点 */
    private static class Node {
        final Map<Character, Node> children = new HashMap<>();
        boolean end = false;
    }

    private final Node root = new Node();

    /** 演示词库:抽奖系统常见风控词(可按需扩充/改为从数据库或文件加载) */
    private static final List<String> DEFAULT_WORDS = List.of(
            "赌博", "博彩", "六合彩", "外挂", "刷奖", "作弊", "诈骗", "洗钱",
            "色情", "暴力", "毒品", "枪支", "代抽", "内定", "黑幕", "违法"
    );

    @PostConstruct
    public void init() {
        DEFAULT_WORDS.forEach(this::addWord);
        log.info("敏感词过滤器初始化完成,词条数: {}", DEFAULT_WORDS.size());
    }

    /** 添加一个敏感词到 DFA 树 */
    public void addWord(String word) {
        if (word == null || word.isEmpty()) {
            return;
        }
        Node current = root;
        for (char c : word.toCharArray()) {
            current = current.children.computeIfAbsent(c, k -> new Node());
        }
        current.end = true;
    }

    /** 是否包含敏感词 */
    public boolean contains(String text) {
        return !findHits(text).isEmpty();
    }

    /** 找出文本中命中的所有敏感词 */
    public List<String> findHits(String text) {
        List<String> hits = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return hits;
        }
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            Node current = root;
            int j = i;
            StringBuilder matched = new StringBuilder();
            while (j < chars.length && current.children.containsKey(chars[j])) {
                current = current.children.get(chars[j]);
                matched.append(chars[j]);
                if (current.end) {
                    hits.add(matched.toString());
                }
                j++;
            }
        }
        return hits;
    }

    /** 打码替换(命中词替换为等长 * ) */
    public String replace(String text, char mask) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            Node current = root;
            int j = i;
            int lastEnd = -1;
            while (j < chars.length && current.children.containsKey(chars[j])) {
                current = current.children.get(chars[j]);
                if (current.end) {
                    lastEnd = j;
                }
                j++;
            }
            if (lastEnd >= i) {
                for (int k = i; k <= lastEnd; k++) {
                    chars[k] = mask;
                }
                i = lastEnd;
            }
        }
        return new String(chars);
    }
}
