package org.example.lottery_system.service.ai.safety;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 敏感词过滤器测试。
 *
 * <p>背景：本项目的内容安全策略是「命中即整段替换为固定句式」，不再对命中词打码、
 * 也不再逐 token 流式过滤（SSE 已改为同步返回）。因此原先用于验证
 * StreamingMasker 跨块边界安全的用例已随实现一并删除。
 *
 * <p>现在真正决定行为的是 {@link SensitiveWordFilter#findHits(String)}：
 * 输入侧用它决定是否拒答，输出侧用它决定是否替换为固定句式。
 * 只要它判断正确，两侧策略就都成立——所以测试集中验证它。
 */
class SensitiveWordFilterTest {

    private SensitiveWordFilter newFilter() {
        SensitiveWordFilter f = new SensitiveWordFilter();
        f.init();   // 加载演示词库
        return f;
    }

    @Test
    @DisplayName("命中演示词库中的词：findHits 返回非空")
    void detectsWordFromDictionary() {
        SensitiveWordFilter f = newFilter();
        assertTrue(f.findHits("这里有赌博内容").contains("赌博"),
                "应命中『赌博』");
        assertTrue(f.contains("外挂可以刷奖"),
                "一句话含多个敏感词时应返回 true");
    }

    @Test
    @DisplayName("正常业务问句不应误报（客服高频问题全量过一遍）")
    void doesNotFlagNormalQuestions() {
        SensitiveWordFilter f = newFilter();
        String[] normal = {
                "怎么参与抽奖？",
                "我中奖了吗",
                "领奖需要填什么信息",
                "活动什么时候结束",
                "奖品有哪些",
                "你好",
        };
        for (String q : normal) {
            assertFalse(f.contains(q),
                    "正常问句被误判为敏感词，会导致客服拒答：" + q);
        }
    }

    @Test
    @DisplayName("空值与 null 安全：不抛异常、不误报")
    void handlesEmptyAndNull() {
        SensitiveWordFilter f = newFilter();
        assertFalse(f.contains(null), "null 不应判定为命中");
        assertFalse(f.contains(""), "空串不应判定为命中");
        assertTrue(f.findHits(null).isEmpty(), "null 的命中列表应为空");
    }

    @Test
    @DisplayName("命中判定与打码替换保持独立：replace 仍可用于需要打码的场合")
    void replaceStillWorks() {
        SensitiveWordFilter f = newFilter();
        String masked = f.replace("这里有赌博内容", '*');
        assertFalse(masked.contains("赌博"), "打码后不应残留原词：" + masked);
        assertTrue(masked.contains("*"), "打码结果应包含掩码字符：" + masked);
        assertEquals("这里有**内容", masked, "等长打码：两字词替换为两个星号");
    }

    @Test
    @DisplayName("词库可动态扩充：addWord 后立即生效")
    void addWordTakesEffect() {
        SensitiveWordFilter f = newFilter();
        String word = "长词测试用例";
        assertFalse(f.contains(word), "加入前不应命中");
        f.addWord(word);
        assertTrue(f.contains(word), "addWord 后应立即命中");
    }
}
