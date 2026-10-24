package com.qa.ui;

import org.junit.jupiter.api.BeforeAll;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UI 用例 · 奖品管理（SA-19 ~ SA-21）
 */
@Feature("奖品管理")
public class PrizeUiTest extends BaseUiTest {

    private static String adminToken;

    @BeforeAll
    static void prepareToken() {
        adminToken = ApiSupport.adminToken();
    }

    @Test
    @Story("SA-19 奖品列表渲染：卡片列表出现数据项")
    @DisplayName("SA-19 奖品列表渲染：卡片列表出现数据项")
    void prizeListRenders() {
        injectToken(adminToken, "ADMIN");
        open("/prizes-list.html");
        sleep(2500);
        dismissAlertIfPresent();
        screenshot("SA-19-prize-list");

        // 2026-10-18 修复：10-16 改版把奖品列表由 <table><tbody tr> 改成卡片列表
        // （prizes-list.html:163 容器 #prizes、:244 每项 .prize-item、:223 空态 .prize-empty），
        // 旧断言 tbody tr 恒为 0 行 → 必然失败。现改为断言卡片项数量。
        int items = waitForElementCount(By.cssSelector("#prizes .prize-item"), 1, 10);
        if (items == 0) {
            // 空态是合法的业务状态（库里没有奖品），此时页面必须给出可读的空态提示，
            // 而不是白屏——把两种情况区分开，避免"没数据"被误报成"渲染坏了"。
            assertNotNull(waitForElement(By.cssSelector("#prizes .prize-empty"), 5),
                    "没有奖品时页面应渲染空态提示 .prize-empty，实际页面文本前 200 字="
                            + bodyText().substring(0, Math.min(200, bodyText().length())));
        } else {
            assertTrue(items > 0, "奖品列表应渲染卡片数据项，实际项数=" + items);
            // 每张卡片都必须带「删除」操作按钮（renderPrizes 中 prize-ops 固定构造）
            assertFalse(driver.findElements(By.cssSelector("#prizes .prize-item .btn-mini.danger")).isEmpty(),
                    "奖品卡片应包含删除按钮");
        }
    }

    @Test
    @Story("SA-20 创建奖品页加载：表单与图片上传入口可见")
    @DisplayName("SA-20 创建奖品页加载：表单与图片上传入口可见")
    void createPrizePageLoads() {
        injectToken(adminToken, "ADMIN");
        open("/create-prizes.html");
        screenshot("SA-20-create-prize");

        var nameEl = waitForElement(By.cssSelector("[name='prizeName']"), 8);
        if (nameEl == null) {
            nameEl = waitForElement(By.id("prizeName"), 8);
        }
        assertNotNull(nameEl, "奖品名称输入框应存在");

        var priceEl = waitForElement(By.id("price"), 8);
        if (priceEl == null) {
            priceEl = waitForElement(By.cssSelector("[name='price']"), 8);
        }
        assertNotNull(priceEl, "奖品价格输入框应存在");

        var fileInputs = driver.findElements(By.cssSelector("input[type='file']"));
        assertFalse(fileInputs.isEmpty(), "应存在图片上传控件");
    }

    @Test
    @Story("SA-21 奖品列表翻页：页码回显与首末页边界提示")
    @DisplayName("SA-21 奖品列表翻页：页码回显与首末页边界提示")
    void prizeListPagination() {
        injectToken(adminToken, "ADMIN");
        open("/prizes-list.html");
        sleep(2500);
        dismissAlertIfPresent();
        screenshot("SA-21-prize-paging");

        // 2026-10-18 修复：翻页控件由「id=nextPage 的按钮」改为
        // prizes-list.html:166-172 的 .pagination 容器（首页/上一页/下一页/尾页 四个按钮，
        // 均无 id，只靠 onclick 调用 fetchPrizes/previousPage/nextPage）。
        // 旧写法 By.id("nextPage") 在静态审计里就是死的（selector_audit 报 1/3 死选择器）。
        // 现改用文本定位到真实存在的「下一页」按钮。
        var nextBtn = waitForElement(
                By.xpath("//div[contains(@class,'pagination')]//button[contains(text(),'下一页')]"), 8);
        assertNotNull(nextBtn, "奖品列表应存在「下一页」按钮");

        // 页码回显：pageInput 在每次 fetchPrizes 成功后由 :206 写回 currentPage
        var pageInput = waitForElement(By.id("pageInput"), 5);
        assertNotNull(pageInput, "应存在页码输入框 #pageInput");
        String pageBefore = pageInput.getAttribute("value");
        assertNotNull(pageBefore, "页码输入框应有页码回显");

        clearLastAlertText();
        nextBtn.click();

        // 边界行为：只有一页时 nextPage() 会弹「已经是最后一页」（prizes-list.html:294），
        // 多于 1 页时则正常翻到第 2 页。两种情况都必须有可见结果，不能静默无响应。
        //
        // 【修复前】assertTrue(pageChanged || alertText != null, "…应要么翻页成功…要么给出边界提示…")
        //   问题：只要**任何**弹窗出现，第一个断言就通过；虽然紧接着还有一条
        //   `if (alertText != null) assertTrue(alertText.contains("最后一页"))` 兜住了文案，
        //   但两个断言合起来表达的仍然是一个松散的 OR，且它不区分"弹窗是翻页触发的"
        //   还是"页面加载时就残留的"。现改为**先分流再断言**，每个分支只允许一种结果。
        boolean pageChanged = false;
        String pageAfter = pageBefore;
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            dismissAlertIfPresent();          // 边界提示会阻塞页面 JS
            if (lastAlertText != null) {
                break;
            }
            pageAfter = driver.findElement(By.id("pageInput")).getAttribute("value");
            if (pageAfter != null && !pageAfter.equals(pageBefore)) {
                pageChanged = true;
                break;
            }
            sleep(200);
        }

        if (lastAlertText != null) {
            // 有弹窗 → 只允许「已经是最后一页」（prizes-list.html:294）
            assertFalse(pageChanged,
                    "一边翻页成功一边弹「最后一页」提示，说明分页判断与渲染不一致：页码 "
                            + pageBefore + "→" + pageAfter + "，弹窗=" + lastAlertText);
            assertTrue(lastAlertText.contains("最后一页"),
                    "只有一页时点「下一页」应提示已是最后一页（prizes-list.html:294），实际弹窗文案=" + lastAlertText);
        } else {
            // 没弹窗 → 必须真的翻页（页码回显变化）
            assertTrue(pageChanged,
                    "点「下一页」后既没有翻页（#pageInput 仍为 " + pageBefore + "），也没有边界提示，"
                            + "说明翻页按钮/请求已失效");
        }
    }

}
