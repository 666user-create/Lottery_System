package com.qa.ui;

import org.junit.jupiter.api.BeforeAll;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UI 用例 · 活动发布与查看（SA-13 ~ SA-18）
 *
 * 覆盖：创建活动页（发布入口）、必填校验、活动列表渲染、翻页、详情打开、详情字段渲染。
 * 断言深度：列表类断言**渲染行数**（而非仅页面可访问）——正是这类断言能抓到
 *          "接口成功但页面空白"的缺陷（如本项目 BUG-002 的同类问题）。
 */
@Feature("活动管理")
public class ActivityUiTest extends BaseUiTest {

    private static String adminToken;

    @BeforeAll
    static void prepareToken() {
        adminToken = ApiSupport.adminToken();
    }

    @Test
    @Story("SA-13 创建活动页加载：表单与奖品/人员选择入口可见")
    @DisplayName("SA-13 创建活动页加载：表单与奖品/人员选择入口可见")
    void createActivityPageLoads() {
        injectToken(adminToken, "ADMIN");
        open("/create-activity.html");
        screenshot("SA-13-create-activity");

        assertNotNull(waitForElement(By.id("activityName"), 8), "活动名称输入框应存在");
        assertNotNull(waitForElement(By.id("description"), 8), "活动描述输入框应存在");
        assertNotNull(waitForElement(By.id("buttonPrizes"), 8), "选择奖品入口应存在");
        assertNotNull(waitForElement(By.id("buttonUsers"), 8), "选择人员入口应存在");
        assertNotNull(waitForElement(By.id("createActivity"), 8), "创建活动按钮应存在");
    }

    @Test
    @Story("SA-14 创建活动必填校验：空表单提交不产生新活动")
    @DisplayName("SA-14 创建活动必填校验：空表单提交不产生新活动")
    void createActivityRequiredValidation() {
        injectToken(adminToken, "ADMIN");
        open("/create-activity.html");
        clickIfPresent(By.id("createActivity"));
        sleep(1500);
        dismissAlertIfPresent();
        screenshot("SA-14-create-validation");

        // 断言：仍停留在创建页（未跳转），且出现校验提示
        assertTrue(driver.getCurrentUrl().contains("create-activity"),
                "必填未通过时不应离开创建活动页，实际 URL=" + driver.getCurrentUrl());
        String text = bodyText();
        assertTrue(text.contains("请输入") || text.contains("不能为空") || text.contains("请选择"),
                "空表单提交应出现校验提示，实际页面文本前 200 字="
                        + text.substring(0, Math.min(200, text.length())));
    }

    @Test
    @Story("SA-15 活动列表渲染：容器出现活动项（抓接口成功但页面空白）")
    @DisplayName("SA-15 活动列表渲染：容器出现活动项（抓接口成功但页面空白）")
    void activityListRenders() {
        injectToken(adminToken, "ADMIN");
        open("/activities-list.html");
        sleep(2500);
        dismissAlertIfPresent();
        screenshot("SA-15-activity-list");

        // 关键断言：渲染出活动项（页面实现为 #activities 下的 .activity-item 卡片，非表格）
        int items = waitForElementCount(By.cssSelector("#activities .activity-item"), 1, 12);
        assertTrue(items > 0, "活动列表应渲染活动项（#activities .activity-item），实际=" + items
                + "，页面文本前 200 字=" + bodyText().substring(0, Math.min(200, bodyText().length())));
    }

    @Test
    @Story("SA-16 活动列表翻页：调用翻页逻辑后列表重新渲染")
    @DisplayName("SA-16 活动列表翻页：调用翻页逻辑后列表重新渲染")
    void activityListPagination() {
        injectToken(adminToken, "ADMIN");
        open("/activities-list.html");
        // 等首屏渲染完成（pageInput 由 fetchActivities 成功回调写入，见 activities-list.html:239）
        waitForElementCount(By.cssSelector("#activities .activity-item"), 1, 12);
        dismissAlertIfPresent();

        // 依据 activities-list.html:153-158 —— 翻页区固定存在，页码回显在 #pageInput（:156）
        var pageInput = waitForElement(By.id("pageInput"), 5);
        assertNotNull(pageInput, "活动列表应有页码回显输入框 #pageInput（activities-list.html:156）");
        String pageBefore = pageInput.getAttribute("value");
        int before = driver.findElements(By.cssSelector("#activities .activity-item")).size();
        assertTrue(before > 0, "翻页前列表应有活动项，实际=" + before);

        // 【修复前】int after = waitForElementCount(…, 0, 8);
        //           assertTrue(before >= 0 && after >= 0, "翻页后应能正常响应…");
        //   问题：元素数量永远 >= 0，这条断言恒真——无论翻页按钮有没有反应、
        //   列表有没有重新渲染、甚至整页报错，用例都绿。它连"翻页"这个动作是否发生过都没验证。
        //
        // 【修复后】按 activities-list.html:320-326 的真实分支断言**二选一的可观测结果**：
        //   · currentPage < totalPages → fetchActivities(currentPage+1) → :239 把新页码写回 #pageInput
        //   · 否则                    → alert("已经是最后一页")（:324）
        // 两者都与"翻页"直接相关，且都不恒真：若 nextPage() 不再被调用、或 fetch 静默失败，
        // 页码不变且没有「最后一页」提示，用例立即失败。
        clearLastAlertText();
        boolean clicked = clickIfPresent(By.xpath("//div[contains(@class,'pagination')]//button[contains(text(),'下一页')]"));
        assertTrue(clicked, "活动列表应有「下一页」按钮（activities-list.html:157）");

        boolean pageChanged = false;
        String pageAfter = pageBefore;
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            dismissAlertIfPresent();       // 边界提示会阻塞页面 JS，先关掉
            if (lastAlertText != null) {
                break;                      // 已拿到「已经是最后一页」这类边界结果
            }
            pageAfter = driver.findElement(By.id("pageInput")).getAttribute("value");
            if (pageAfter != null && !pageAfter.equals(pageBefore)) {
                pageChanged = true;
                break;
            }
            sleep(200);
        }
        int after = driver.findElements(By.cssSelector("#activities .activity-item")).size();
        screenshot("SA-16-activity-paging");

        boolean lastPageHint = lastAlertText != null && lastAlertText.contains("最后一页");
        assertTrue(pageChanged || lastPageHint,
                "点「下一页」后应要么翻页成功（#pageInput 由 " + pageBefore + " 变为新页码，实际 " + pageAfter
                        + "），要么给出「已经是最后一页」边界提示（activities-list.html:324），实际两者都没有；弹窗="
                        + lastAlertText);

        // 翻页（或触达边界）之后列表都不能变空——翻页把内容翻没了是真实故障
        assertTrue(after > 0,
                "翻页后活动列表不应变空，实际项数=" + after + "，页面文本前 200 字="
                        + bodyText().substring(0, Math.min(200, bodyText().length())));
    }

    @Test
    @Story("SA-17 活动详情入口：活动项提供携带活动ID的入口链接")
    @DisplayName("SA-17 活动详情入口：活动项提供携带活动ID的入口链接")
    void openActivityDetail() {
        injectToken(adminToken, "ADMIN");
        open("/activities-list.html");
        sleep(3000);
        dismissAlertIfPresent();

        var links = driver.findElements(By.cssSelector("#activities .activity-item a"));
        screenshot("SA-17-activity-detail");

        assertFalse(links.isEmpty(), "活动列表每项应提供详情/抽奖入口链接");
        String href = links.get(0).getAttribute("href");
        assertNotNull(href, "入口链接应有 href");
        // 关键：入口必须携带 activityId（抓"入口链接参数缺失导致无法查看详情"）
        assertTrue(href.contains("activityId"),
                "入口链接应携带 activityId 参数，实际 href=" + href);
    }

    @Test
    @Story("SA-18 抽奖/详情页渲染：通过入口链接进入后页面渲染活动信息")
    @DisplayName("SA-18 抽奖/详情页渲染：通过入口链接进入后页面渲染活动信息")
    void activityDetailFields() {
        injectToken(adminToken, "ADMIN");

        // 【修复前】open("/draw.html?activityName=…&activityId=34&valid=true") —— 写死 activityId=34
        //   且 valid=true。两个问题：
        //   ① 34 是历史活动，库被清理后这条用例会以"页面文案不对"的方式失败，看不出是数据没了；
        //   ② valid=true + ADMIN 会走**真实抽奖**分支（draw.html:704 起），依赖 RabbitMQ 与
        //      奖品余量，属于"测试去动业务状态"，既不稳定也不该由 UI 用例承担。
        // 【修复后】运行时反查一个确实含有中奖记录的活动（ApiSupport.firstActivityIdWithRecords），
        //   用 valid=false 打开只读名单页（draw.html:548-549 注释：valid=false → showList/showRecords），
        //   不触发任何写操作。
        String activityId = ApiSupport.firstActivityIdWithRecords(adminToken);
        assertNotNull(activityId,
                "前置条件不满足：库中找不到任何一个「含有中奖记录」的活动，"
                        + "无法验证抽奖名单页渲染（这是数据前置问题，不是页面缺陷）");

        open("/draw.html?activityId=" + activityId + "&valid=false");

        // 依据 draw.html:164-167 的 showRecords()：
        //   disc.textContent = "中奖名单"        → 顶部说明文字变为「中奖名单」
        //   image.style.display = 'none'         → 奖品大图隐藏
        //   list.style.display = 'block'         → 名单容器显示
        var disc = waitForElement(By.id("disc"), 10);
        assertNotNull(disc, "抽奖/名单页应有说明文字容器 #disc（draw.html:97）");
        assertTrue(waitForTextContains(By.id("disc"), "中奖名单", 10),
                "valid=false 时应进入只读名单模式，说明文字应为「中奖名单」"
                        + "（draw.html:164-165 showRecords），实际=" + disc.getText());
        screenshot("SA-18-detail-fields");

        assertFalse(driver.findElement(By.id("image")).isDisplayed(),
                "只读名单模式应隐藏奖品大图（draw.html:166）");
        assertTrue(driver.findElement(By.id("list")).isDisplayed(),
                "只读名单模式应显示名单容器 #list（draw.html:167）");

        // 核心断言：接口侧确认该活动**有**中奖记录（firstActivityIdWithRecords 的筛选条件），
        // 页面就必须渲染出记录，不能显示「暂无中奖记录」（draw.html:185-190 的空态）。
        // 这一条能抓住"接口有数据但页面渲染不出来"的真实缺陷；
        // 旧断言只要求"整页文本包含抽奖/活动/中奖 之一"，页面 95% 的静态文案都满足它。
        var list = driver.findElement(By.id("list"));
        int rendered = list.findElements(By.xpath("./*")).size();
        assertTrue(rendered > 0, "名单容器 #list 应渲染出条目，实际子节点数=" + rendered);
        assertFalse(list.getText().contains("暂无中奖记录"),
                "该活动已通过接口确认含有中奖记录，页面不应显示空态「暂无中奖记录」"
                        + "（draw.html:187）。实际 #list 文本=" + list.getText());
    }
}
