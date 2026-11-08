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

    /** 词库中最长词的长度：流式过滤时的边界缓冲大小（见 {@link StreamingMasker}） */
    private volatile int maxWordLength = 1;

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
        if (word.length() > maxWordLength) {
            maxWordLength = word.length();
        }
    }

    /** 词库最长词长度：流式过滤需要缓冲这么多字符才能确定边界不会漏词 */
    public int getMaxWordLength() {
        return maxWordLength;
    }

    /**
     * 创建一个流式打码器（每条回答新建一个，非线程安全）。
     *
     * <p>背景：真流式（逐 token 返回）下无法"拿到全文再 replace"，必须边收边过滤。
     * 难点是敏感词可能被切断在 chunk 边界（如 chunk1="赌"，chunk2="博"）。
     * 解法是保留末尾 maxWordLength-1 个字符暂不输出，等下一块到达后合并再判断——
     * 这样永远不会把半个词提前发出去。
     *
     * <p>用法：
     * <pre>
     * StreamingMasker m = filter.newStreamingMasker();
     * Flux&lt;String&gt; out = rawFlux.map(m::drain).concatWith(Mono.fromSupplier(m::flush));
     * </pre>
     */
    public StreamingMasker newStreamingMasker() {
        return new StreamingMasker();
    }

    /**
     * 流式打码器：缓冲末尾若干字符，确保跨 chunk 的敏感词也能被识别和打码。
     */
    public class StreamingMasker {
        private final StringBuilder pending = new StringBuilder();

        /**
         * 输入新到达的文本片段，返回本次可以安全输出的部分（已打码）。
         *
         * <p>实现要点（两次修正，均有单测覆盖）：
         * <ol>
         *   <li><b>缓冲长度必须是 maxWordLength 而非 maxWordLength-1</b>：
         *       长度为 L 的词若跨越切点 safeLen，则其起点 start = end - L，
         *       而 end 最大为 pending 长度，故 start >= len - L >= len - maxWordLength。
         *       因此只有保留 maxWordLength 个字符（safeLen = len - maxWordLength）
         *       才能保证 start >= safeLen，即不会有词"一半已输出、一半还在缓冲"。
         *       （若只保留 maxWordLength-1，会出现 start = safeLen-1 的跨界词漏检。）</li>
         *   <li><b>必须先对整段 pending 打码，再截取头部输出</b>：
         *       若先截 head 再对 head 调 replace()，head 里可能只有半个词
         *       （如 head="赌"、缓冲另有"博"），replace 匹配不到完整词 → 原样输出 → 漏检。</li>
         * </ol>
         */
        public String drain(String chunk) {
            if (chunk == null || chunk.isEmpty()) {
                return "";
            }
            pending.append(chunk);

            int safeLen = pending.length() - maxWordLength;   // 见要点 1
            if (safeLen <= 0) {
                return "";                                  // 还不足以确定边界，先攒着
            }

            // 见要点 2：对"整段缓冲"打码后再截头，保证包含完整词的片段被打码
            String masked = replace(pending.toString(), MASK_CHAR);

            // 见要点 3：切点若落在掩码词内部，必须推到该词末尾整体输出。
            // 若不推：前半输出成 *，后半（如"博"）留在缓冲，但它已缺少前导字"赌"，
            // 再也匹配不上 → 后半会以原文裸奔输出，等于敏感词漏检。
            int cut = safeLen;
            if (masked.charAt(cut - 1) == MASK_CHAR) {
                while (cut < masked.length() && masked.charAt(cut) == MASK_CHAR) {
                    cut++;
                }
            }

            String head = masked.substring(0, cut);
            pending.delete(0, cut);                         // 尾部保留原始字符，供跨块匹配
            return head;
        }

        /** 流结束：把缓冲里剩余的字全部打码后输出 */
        public String flush() {
            if (pending.length() == 0) {
                return "";
            }
            String rest = replace(pending.toString(), MASK_CHAR);
            pending.setLength(0);
            return rest;
        }
    }

    /** 打码字符 */
    private static final char MASK_CHAR = '*';

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
