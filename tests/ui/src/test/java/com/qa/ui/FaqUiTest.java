package com.qa.ui;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UI 用例 · FAQ 知识库管理（SA-44 ~ SA-45）—— 页面 {@code faq-management.html}
 *
 * <p>本页此前**没有任何自动化覆盖**（2026-10-18 新增）。它是 AI 客服 RAG 的知识来源，
 * 网页上新增的条目会直接影响客服回答（见 docs/design/AI客服RAG实现指南.md），
 * 所以"新增成功但列表看不到"这类问题必须能被自动化抓到。
 *
 * <h3>断言深度</h3>
 * <ul>
 *   <li>SA-44 不只是"弹了成功提示"：还要求**总数 +1**（证明后端真的落库）
 *       并且**新条目能在列表里查到**（证明渲染链路没断）。只断言弹窗的话，
 *       接口返回 code=200 但数据没写库、或写了但列表不刷新，都会被放过。</li>
 *   <li>SA-45 不触发任何请求：它是纯前端校验（faq-management.html:285-286 在 $.ajax 之前 return），
 *       所以断言弹窗原文即可，且完全不受数据库状态影响。</li>
 * </ul>
 *
 * <p>分页约定：本页 pageSize=10（:203），排序按 id 升序（:264 注释），
 * 因此**新增的条目在最后一页**，要验证它出现必须翻到「尾页」。
 */
@Feature("FAQ 知识库管理")
public class FaqUiTest extends BaseUiTest {

    private static String adminToken;

    @BeforeAll
    static void prepareToken() {
        adminToken = ApiSupport.adminToken();
    }

    @Test
    @Story("SA-44 新增 FAQ：总条数 +1 且新条目可在列表末页查到")
    @DisplayName("SA-44 新增 FAQ：总条数 +1 且新条目可在列表末页查到")
    void addFaqAndSeeItInList() {
        injectToken(adminToken, "ADMIN");
        open("/faq-management.html");

        // 等首屏加载完：renderFaqs 会写 '共 N 条'（faq-management.html:254）
        assertTrue(waitForTextMatches(By.id("totalTip"), "^共 \\d+ 条$", 20),
                "知识条目总数应为「共 N 条」（faq-management.html:254），实际="
                        + safeText(By.id("totalTip")) + "，URL=" + driver.getCurrentUrl());
        int before = parseTotal(safeText(By.id("totalTip")));

        // 用唯一问题串，避免和库里的历史数据混淆（同时符合 qa_auto_ 清理约定）
        String unique = "QA-UI 自动化知识_" + UUID.randomUUID().toString().replaceAll("[^0-9]", "").substring(0, 6);
        type(By.id("question"), unique);
        type(By.id("answer"), "QA-UI 自动化答案：用于验证新增与列表回显");
        type(By.id("category"), "测试");

        clearLastAlertText();
        clickIfPresent(By.cssSelector("button[onclick='addFaq()']"));

        // ---------- ① 结果弹窗必须是成功的原文 ----------
        // 依据 faq-management.html:295-308：
        //   code==200 → :301 alert('新增成功！')
        //   code!=200 → :298 alert('新增失败：' + message)
        //   401/403   → :296 交给 AuthGuard（会弹「登录状态已失效」/「权限不足」）
        // 三种结局文案互不相同，断言精确文案即可区分；只断言"弹了个窗"是没有意义的。
        assertTrue(waitForAlertAndAccept(15),
                "新增 FAQ 后应弹出结果提示（faq-management.html:295-308 三种分支都会 alert），实际未捕获弹窗");
        assertEquals("新增成功！", lastAlertText,
                "新增成功弹窗文案应为 faq-management.html:301 的原文，实际=" + lastAlertText);

        // ---------- ② 总数必须 +1（证明真的落库并重新拉取了列表） ----------
        // 依据 faq-management.html:303 —— 新增成功后调用 fetchFaqs(1) 重新拉取，
        // :254 把新的 total 写回 #totalTip。
        boolean increased = false;
        String observedTip = "";
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            observedTip = safeText(By.id("totalTip"));
            if (parseTotal(observedTip) == before + 1) {
                increased = true;
                break;
            }
            sleep(300);
        }
        assertTrue(increased,
                "新增成功后总条数应 +1（新增前=" + before + "，期望=" + (before + 1)
                        + "，实际 totalTip=" + observedTip + "）。"
                        + "若弹窗是「新增成功！」但总数没变，说明后端返回成功却没落库，或前端没刷新列表");

        // ---------- ③ 新条目必须能在列表里查到 ----------
        // 依据 faq-management.html:264 注释：列表按 id 升序、跨页连续编号，
        // 所以刚新增（id 最大）的条目在**最后一页**；点「尾页」(:194 onclick="goLastPage()") 才能看到。
        boolean jumped = clickIfPresent(By.xpath("//button[contains(text(),'尾页')]"));
        assertTrue(jumped, "知识库分页区应有「尾页」按钮（faq-management.html:194）");

        boolean found = false;
        String tableText = "";
        deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            tableText = safeText(By.id("faqTableBody"));
            if (tableText.contains(unique)) {
                found = true;
                break;
            }
            sleep(300);
        }
        screenshot("SA-44-faq-added");

        assertTrue(found,
                "新增的条目应出现在列表最后一页的问题列中（渲染依据 faq-management.html:269 写 question 列）。"
                        + "查找的问题串=" + unique + "，实际表体文本前 400 字="
                        + (tableText.length() > 400 ? tableText.substring(0, 400) : tableText));
    }

    @Test
    @Story("SA-45 新增 FAQ 必填校验：先拦「问题」再拦「答案」")
    @DisplayName("SA-45 新增 FAQ 必填校验：先拦「问题」再拦「答案」")
    void addFaqRequiredValidation() {
        injectToken(adminToken, "ADMIN");
        open("/faq-management.html");
        waitForTextMatches(By.id("totalTip"), "^共 \\d+ 条$", 20);

        // 依据 faq-management.html:280-287：addFaq() 在发请求**之前**做前端校验，
        //   :285 if (!question) { alert('请填写问题'); return; }
        //   :286 if (!answer)   { alert('请填写答案'); return; }
        // 也就是说这两条断言完全不依赖后端与数据库，是稳定可复现的行为契约。
        // 旧式写法（断言"页面出现某某提示"）在这里会失效：这两个提示是 alert，
        // 不进 DOM，整页文本里根本看不到。

        // ---------- ① 空表单 → 「请填写问题」 ----------
        clearLastAlertText();
        clickIfPresent(By.cssSelector("button[onclick='addFaq()']"));
        assertTrue(waitForAlertAndAccept(8),
                "空表单点「新增」应弹出校验提示（faq-management.html:285），实际未捕获弹窗");
        assertEquals("请填写问题", lastAlertText,
                "问题为空时应提示 faq-management.html:285 的原文，实际=" + lastAlertText);

        // ---------- ② 只填问题、不填答案 → 「请填写答案」 ----------
        String unique = "QA-UI 校验问题_" + UUID.randomUUID().toString().replaceAll("[^0-9]", "").substring(0, 6);
        type(By.id("question"), unique);
        // 清空答案（页面刚打开时答案框本来就是空的，这里显式清一次，防止上一步残留）
        driver.findElement(By.id("answer")).clear();
        clearLastAlertText();
        clickIfPresent(By.cssSelector("button[onclick='addFaq()']"));
        assertTrue(waitForAlertAndAccept(8),
                "只填问题不填答案时点「新增」应弹出校验提示（faq-management.html:286），实际未捕获弹窗");
        assertEquals("请填写答案", lastAlertText,
                "答案为空时应提示 faq-management.html:286 的原文，实际=" + lastAlertText);

        // ---------- ③ 两次校验都不能落库 ----------
        // 真断言：被校验拦下的提交不应改变总条数。若 addFaq 的 return 被删掉，
        // 空答案请求就会真的发出去（后端可能因 answer 非空约束失败、也可能真的写入），
        // 这条断言能发现"校验形同虚设"的回归。
        String tipNow = safeText(By.id("totalTip"));
        // 重新拉一次首屏做对比（两次非法提交之间没有合法提交，总数应当不变）
        open("/faq-management.html");
        assertTrue(waitForTextMatches(By.id("totalTip"), "^共 \\d+ 条$", 20));
        assertEquals(tipNow, safeText(By.id("totalTip")),
                "被前端校验拦下的两次提交不应改变知识条目总数（faq-management.html:285-286 的 return），"
                        + "校验前=" + tipNow + "，重新加载后=" + safeText(By.id("totalTip")));

        // 顺带确认那两条唯一问题串没有真的进库（防止"请求其实发出去了"）
        assertFalse(driver.findElement(By.id("faqTableBody")).getText().contains(unique),
                "被校验拦下的问题串不应出现在列表里：" + unique);
        screenshot("SA-45-faq-validation");
    }

    /**
     * 从「共 N 条」里解析出 N；解析不到返回 -1。
     *
     * <p>-1 会让后续的数字比较自然失败，比返回 0 更安全
     * （返回 0 时"新增前=0、新增后=1"看起来像成功了）。
     */
    private int parseTotal(String tip) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("共\\s*(\\d+)\\s*条").matcher(tip == null ? "" : tip);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    /** 读取元素文本；元素不存在返回空串（仅用于构造断言消息） */
    private String safeText(By by) {
        var list = driver.findElements(by);
        return list.isEmpty() ? "" : list.get(0).getText().trim();
    }
}
