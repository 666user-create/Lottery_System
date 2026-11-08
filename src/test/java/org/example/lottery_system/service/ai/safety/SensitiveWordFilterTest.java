package org.example.lottery_system.service.ai.safety;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流式敏感词打码的边界安全性测试。
 *
 * <p>背景：真流式下无法"拿到全文再 replace"，改用 StreamingMasker 保留末尾
 * maxWordLength-1 个字符延迟输出。核心风险是敏感词被切在 chunk 边界时，
 * 若缓冲不足会漏检——把"赌"先发出去、后面再补"博"，敏感词就逃逸了。
 *
 * <p>本测试逐字符、按任意切分点喂入，验证任何切分方式下输出都等价于
 * "全文打码后逐段输出"，即跨块敏感词必定被打码。
 */
class SensitiveWordFilterTest {

    private SensitiveWordFilter newFilter() {
        SensitiveWordFilter f = new SensitiveWordFilter();
        f.init();   // 加载演示词库
        return f;
    }

    /** 把整段文本按给定切分喂入 StreamingMasker，返回全部输出拼接 */
    private String streamThrough(SensitiveWordFilter filter, String text, int chunkSize) {
        SensitiveWordFilter.StreamingMasker masker = filter.newStreamingMasker();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i += chunkSize) {
            out.append(masker.drain(text.substring(i, Math.min(i + chunkSize, text.length()))));
        }
        out.append(masker.flush());
        return out.toString();
    }

    @Test
    @DisplayName("跨 chunk 敏感词必须被打码：任意切分粒度结果一致")
    void crossChunkSensitiveWordIsMasked() {
        String text = "这里有赌博内容要屏蔽";
        String expected = newFilter().replace(text, '*');

        // 从 1 到 全文长度，穷举所有切分粒度
        for (int size = 1; size <= text.length(); size++) {
            String actual = streamThrough(newFilter(), text, size);
            assertEquals(expected, actual,
                    "chunkSize=" + size + " 时流式输出与全文打码不一致，存在跨块漏检");
        }
    }

    @Test
    @DisplayName("敏感词正好被切在边界：chunk1 是前缀，chunk2 是后缀")
    void wordSplitExactlyOnBoundary() {
        SensitiveWordFilter filter = newFilter();
        SensitiveWordFilter.StreamingMasker masker = filter.newStreamingMasker();

        // "赌博" 被切成 "赌" + "博"
        String part1 = masker.drain("赌");
        String part2 = masker.drain("博");
        String tail = masker.flush();
        String all = part1 + part2 + tail;

        assertFalse(all.contains("赌博"), "跨块敏感词漏检：输出中出现完整敏感词。实际=" + all);
        assertTrue(all.contains("*"), "敏感词未被替换为掩码。实际=" + all);
    }

    @Test
    @DisplayName("流式输出内容与全文打码等价：完整句子的无敏感词文本原样输出")
    void normalTextPassesThroughUnchanged() {
        String text = "抽奖活动的参与方式很简单，登录后进入活动页面即可。";
        String expected = newFilter().replace(text, '*');
        for (int size = 1; size <= 10; size++) {
            assertEquals(expected, streamThrough(newFilter(), text, size),
                    "chunkSize=" + size + " 时正常文本被破坏");
        }
        // 无敏感词文本不应被改动
        assertEquals(text, expected);
    }

    @Test
    @DisplayName("空块不应产生可见输出（前端不该收到空事件）")
    void emptyChunkProducesNothing() {
        SensitiveWordFilter filter = newFilter();
        SensitiveWordFilter.StreamingMasker masker = filter.newStreamingMasker();
        assertEquals("", masker.drain(""));
        assertEquals("", masker.drain(null));
        assertEquals("", masker.flush(), "无输入时 flush 应为空");
    }

    @Test
    @DisplayName("缓冲长度取自词库最长词，词库扩充后自动适配")
    void maxWordLengthReflectsWordList() {
        SensitiveWordFilter filter = newFilter();
        assertTrue(filter.getMaxWordLength() > 1, "演示词库含多字词，最长长度应大于 1");

        // 加一个更长的词，缓冲长度应随之增长
        int before = filter.getMaxWordLength();
        filter.addWord("长词测试用例");
        assertTrue(filter.getMaxWordLength() >= 5 || filter.getMaxWordLength() == "长词测试用例".length(),
                "加入更长词后 maxWordLength 未更新");
        assertTrue(filter.getMaxWordLength() > before || before >= "长词测试用例".length());
    }
}
