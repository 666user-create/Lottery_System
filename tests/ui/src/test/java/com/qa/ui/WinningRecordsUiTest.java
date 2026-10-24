package com.qa.ui;

import org.junit.jupiter.api.BeforeAll;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UI 用例 · 抽奖页 / 中奖名单 / 分享链接（SA-22 ~ SA-24）
 *
 * <h3>本轮（2026-10-18）重构的原因</h3>
 * 前端 draw.html 已大幅改写（状态机、导航按钮、分享入口都是新逻辑），
 * 旧用例里 SA-23 只是「点一下按钮 + 断言页面文本非空」，
 * SA-24 找的是 <b>不存在</b>的「复制链接」文案，属于典型的「恒真断言 / 恒不命中定位器」，
 * 就算功能坏了也照样绿。本次改为断言**可观察的业务结果**。
 *
 * <h3>为什么全程用 {@code valid=false} 打开抽奖页</h3>
 * {@code drawPage()}（draw.html:702-726）按 URL 参数 valid 分两条完全不同的路径：
 * <pre>
 *   valid == "true"  → ADMIN: reloadConf(nextStep)  → 进入真实抽奖流程
 *                             → nextStep 走到 showBlink 分支会 POST /draw/prize（依赖 RabbitMQ）
 *                      非 ADMIN: alert「无权限参与抽奖」
 *   valid != "true"  → finished=true、「已全部抽完」+ 置灰、隐藏 pre-btn、直接 showRecords()
 * </pre>
 * 用例目的是验证「中奖名单渲染 / 分享链接拼接」，不是验证抽奖写链路，
 * 因此统一走 {@code valid=false} 这条只读路径：既不碰 RabbitMQ，行为也完全确定。
 *
 * <p>关键事实（实测）：{@code reloadConf(nextStep)} 这条分支<b>永远不会</b>调用
 * {@code showRecords()}，所以用 {@code valid=true} 打开页面时，
 * 第 203-233 行的分享按钮根本没有机会被创建 —— 这正是旧 SA-24 必然失败的深层原因，
 * 只把定位器从「复制链接」改成「分享结果」是不够的。
 */
@Feature("抽奖与中奖记录")
public class WinningRecordsUiTest extends BaseUiTest {

    private static String adminToken;

    /**
     * 库中「确实有中奖记录」的活动 id。
     *
     * <p>不写死 id（比如 37）：那是历史遗留数据，随时可能被清理，
     * 一旦消失，用例会以「分享按钮不存在」这种难以理解的方式失败。
     * 改为启动时通过接口反查，挑一个真有记录的活动。
     */
    private static String activityIdWithRecords;

    @BeforeAll
    static void prepareToken() {
        adminToken = ApiSupport.adminToken();
        activityIdWithRecords = ApiSupport.firstActivityIdWithRecords(adminToken);
        // 打印出来便于排查：用例失败时能立刻确认「是页面问题还是库里没数据」
        System.out.println("[WinningRecordsUiTest] 选中含中奖记录的活动 id=" + activityIdWithRecords);
    }

    /**
     * SA-22 抽奖页加载：核心容器存在，且「已结束」状态被正确置为终态。
     *
     * <p>这里顺带覆盖了 2026-10-13 修复的一个真实缺陷：活动已结束时，
     * 若「下一步」按钮不置灰、也不摘掉 onclick，用户点了没反应，会以为页面卡死。
     * 对应 draw.html:513-524 的 {@code disableNextButton()} 与 :717-723。
     */
    @Test
    @Story("SA-22 抽奖页加载：核心容器与已结束终态")
    @DisplayName("SA-22 抽奖页加载：核心容器可见且「已结束」时按钮置为终态")
    void drawPageLoads() {
        injectToken(adminToken, "ADMIN");
        open("/draw.html?activityId=" + activityIdWithRecords + "&valid=false");
        sleep(2000);
        dismissAlertIfPresent();
        screenshot("SA-22-draw-page");

        assertNotNull(waitForElement(By.id("container"), 8), "抽奖页主容器 #container 应存在");
        assertNotNull(waitForElement(By.id("disc"), 8), "抽奖页说明区 #disc 应存在");
        assertNotNull(waitForElement(By.id("list"), 8), "抽奖页列表区 #list 应存在");
        assertNotNull(waitForElement(By.cssSelector(".opt-box"), 8), "抽奖页按钮区 .opt-box 应存在");

        /*
         * 已结束（valid != "true"）→ drawPage() else 分支（draw.html:713-725）：
         *   finished=true、changeNextButtonText('已全部抽完')、disableNextButton()、showRecords()
         *
         * 注意：这里**不能**断言按钮文案。
         * draw.html:236-244 的隐藏逻辑是
         *   (hideButton == null && valid === 'false') || hideButton === 'true'  →  display:none
         * 我们用 valid=false 且不带 hideButton 打开，正好命中第一个条件，
         * 于是 .pre-btn 与 .next-btn **两个都被隐藏**。
         * Selenium 的 getText() 对不可见元素返回空串（第一次写这个断言就栽在这里：
         * 实际拿到 ""，误判成"文案没更新"）。
         * 因此改为断言真实可观察的状态：按钮被隐藏 + 文案已被写成「已全部抽完」。
         */
        var nextBtn = waitForElement(By.cssSelector(".opt-box .btn.next-btn"), 8);
        assertNotNull(nextBtn, "「下一步」按钮应存在（只是被隐藏）");
        assertFalse(nextBtn.isDisplayed(),
                "只读名单页应隐藏「下一步」按钮，避免出现「可点却无反应」的按钮");
        // 用 textContent 取文案：元素被 display:none 后 getText() 返回空串，但文案本身已写入
        String nextText = String.valueOf(((JavascriptExecutor) driver)
                .executeScript("return document.querySelector('.opt-box .btn.next-btn').textContent;"));
        assertEquals("已全部抽完", nextText.trim(),
                "已结束时按钮文案应先被写成「已全部抽完」");
        assertTrue(nextBtn.getAttribute("class").contains("disabled"),
                "已结束时「下一步」按钮应被置灰（disableNextButton 会加 .disabled 类）");
        assertNull(nextBtn.getAttribute("onclick"),
                "已结束时「下一步」按钮应摘掉 onclick，防止点击后毫无反应");

        // 「上一奖项/下一奖项」导航按钮同样应被隐藏（draw.html:240-243 一次性隐藏两个）
        Object preDisplay = ((JavascriptExecutor) driver)
                .executeScript("var b=document.querySelector('.opt-box .btn.pre-btn');"
                        + "return b ? b.style.display : '(无此按钮)';");
        assertEquals("none", String.valueOf(preDisplay),
                "只读名单页应隐藏 .pre-btn，避免出现「可点却无反应」的导航按钮");
    }

    /**
     * SA-23 中奖记录查询：列表区真的渲染出记录行，而不是「页面有字就算过」。
     *
     * <p>旧写法断言 {@code bodyText() 非空} —— 任何页面都非空，等于没断言。
     * 现在断言三件事：
     * <ol>
     *   <li>#disc 文案被 showRecords() 改成「中奖名单」（draw.html:165）；</li>
     *   <li>#list 下渲染出至少 1 个记录行（draw.html:193-200 每个奖项循环 append 一个 div）；</li>
     *   <li>记录行文本里带上奖项等级（数据来自接口 prizeTier，含「奖品」二字）。</li>
     * </ol>
     * 三件事同时成立，才能证明「POST /draw/winning-records/show 返回 200 且 data 非空」
     * 这一整条链路是通的。
     */
    @Test
    @Story("SA-23 中奖记录查询：真实渲染出记录行")
    @DisplayName("SA-23 中奖记录查询：名单区渲染出记录行与奖项等级")
    void winningRecordsRenders() {
        assertNotNull(activityIdWithRecords,
                "库里没有任何带中奖记录的活动，无法验证中奖名单渲染（请先造数据）");

        injectToken(adminToken, "ADMIN");
        open("/draw.html?activityId=" + activityIdWithRecords + "&valid=false");
        sleep(2000);
        dismissAlertIfPresent();

        // ① 说明区被改成「中奖名单」
        assertTrue(waitForTextContains(By.id("disc"), "中奖名单", 10),
                "查询成功后 #disc 应显示「中奖名单」");
        // ② 列表区渲染出记录行
        int rows = waitForElementCount(By.cssSelector("#list > div"), 1, 10);
        assertTrue(rows >= 1, "中奖名单应至少渲染 1 行记录，实际行数=" + rows);
        // ③ 记录行里带了奖项等级（接口 prizeTier 字段，形如「一等奖品」）
        String listText = driver.findElement(By.id("list")).getText();
        assertTrue(listText.contains("奖品"),
                "记录行应包含奖项等级文本（来自 prizeTier），实际=" + listText.replace('\n', '|'));
        screenshot("SA-23-winning-records");
        System.out.println("[SA-23] 活动 " + activityIdWithRecords + " 渲染记录行数=" + rows);
    }

    /**
     * SA-24 分享链接：点「分享结果」后复制出去的 URL 必须带正确的参数。
     *
     * <h3>为什么用「打桩 + 断言 URL」而不是「断言弹窗」</h3>
     * {@code copyToClipboard()}（draw.html:251-276）有两条分支：
     * <pre>
     *   navigator.clipboard && window.isSecureContext → writeText().then(alert)
     *   否则                                          → 隐藏 textarea + execCommand + alert
     * </pre>
     * localhost 属于「安全上下文」，无头 Chrome 里写剪贴板还可能受权限影响，
     * 于是「点一下到底弹不弹窗」本身就是环境相关的，拿它当断言必然不稳。
     *
     * <p>而真正需要被验证的业务逻辑是<b>分享 URL 的拼接</b>（draw.html:211-227）：
     * 必须删掉 valid=true 并置为 false（防止别人点开分享链接继续抽奖），
     * 再补上 hideButton=true（防止二次分享）。
     * 所以这里先把全局 {@code copyToClipboard} 替换成打桩函数，
     * 把真实传给它的 URL 截获下来再断言 —— 确定、无副作用、且更贴近业务意图。
     */
    @Test
    @Story("SA-24 分享链接：分享 URL 参数正确（valid=false + hideButton=true）")
    @DisplayName("SA-24 分享链接：点击「分享结果」后复制的 URL 参数正确")
    void shareLink() {
        assertNotNull(activityIdWithRecords,
                "库里没有任何带中奖记录的活动，无法验证分享入口");

        injectToken(adminToken, "ADMIN");
        // 关键：必须是 valid=false。valid=true 走 reloadConf(nextStep)，不会 createShareBtn
        open("/draw.html?activityId=" + activityIdWithRecords + "&valid=false");
        sleep(2000);
        dismissAlertIfPresent();

        // 分享按钮是查询成功后才动态 append 的（draw.html:205-232）
        var shareBtn = waitForElement(By.cssSelector(".opt-box .btn.copy-btn"), 12);
        assertNotNull(shareBtn,
                "查询到中奖记录后应动态生成 .btn.copy-btn 分享入口");
        assertEquals("分享结果", shareBtn.getText().trim(), "分享入口文案应为「分享结果」");
        screenshot("SA-24-share-entry");

        // 打桩：拦截真正被复制的 URL（不改动业务代码，只替换全局函数引用）
        ((JavascriptExecutor) driver).executeScript(
                "window.__capturedShareUrl = null;"
                        + "window.copyToClipboard = function(u){ window.__capturedShareUrl = String(u); };");
        shareBtn.click();
        sleep(1200); // 等 onclick 同步执行完（打桩后无网络/剪贴板异步）

        String copied = String.valueOf(((JavascriptExecutor) driver)
                .executeScript("return window.__capturedShareUrl;"));
        assertNotNull(copied, "点击分享应调用 copyToClipboard 并传入 URL");
        assertNotEquals("null", copied, "点击分享未捕获到待复制 URL");

        // 逐项校验分享参数（对应 draw.html:217-221）
        assertTrue(copied.contains("activityId=" + activityIdWithRecords),
                "分享 URL 应保留当前活动 id，实际=" + copied);
        assertTrue(copied.contains("valid=false"), "分享 URL 应带 valid=false，实际=" + copied);
        assertFalse(copied.contains("valid=true"), "分享 URL 不应再带 valid=true，实际=" + copied);
        assertTrue(copied.contains("hideButton=true"), "分享 URL 应带 hideButton=true，实际=" + copied);
        System.out.println("[SA-24] 捕获到的分享 URL = " + copied);
    }
}
