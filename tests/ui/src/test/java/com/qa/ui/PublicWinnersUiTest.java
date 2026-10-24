package com.qa.ui;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UI 用例 · 公开中奖名单（SA-50 ~ SA-51）—— 页面 {@code public-winners.html}
 *
 * <p>本页此前**没有任何自动化覆盖**（2026-10-18 新增）。它是全站唯一的**真·双模式页面**
 * （见该页 :241-249 的注释）：
 * <ul>
 *   <li><b>模式 A（公开）</b>：未登录 + URL 带 {@code ?activityId=<id>} →
 *       仍可查看该活动中奖名单（{@code /draw/winning-records/show} 在公开白名单内），
 *       但左栏折叠（{@code .split.no-side}）、名单上方给出登录引导；</li>
 *   <li><b>模式 B（已登录）</b>：从 {@code /activity/find-list?status=COMPLETED} 拉活动列表，
 *       渲染左栏 {@code .act-btn} 选择器，点击后右侧换名单。</li>
 * </ul>
 *
 * <h3>断言深度</h3>
 * <ul>
 *   <li>SA-50 是**免登录**用例（不注入任何 token）：断言左栏确实折叠、登录引导文案出现、
 *       名单表格行数与 {@code #listCount} 声称的条数一致、结果海报链接参数齐全。
 *       这条用例即使登录体系整体坏掉也应继续可跑——它验证的正是"公开可读"这个承诺；</li>
 *   <li>SA-51 断言选择器的**数量与文案一致性**（{@code #actCount} 声称 N 个 → 实际渲染 N 个按钮），
 *       并点击一个已结束活动，验证「左栏点击 → 右栏名单 + 海报链接」的联动。</li>
 * </ul>
 */
@Feature("公开中奖名单")
public class PublicWinnersUiTest extends BaseUiTest {

    private static String adminToken;

    @BeforeAll
    static void prepareToken() {
        adminToken = ApiSupport.adminToken();
    }

    @Test
    @Story("SA-50 未登录公开模式：带 activityId 仍可查看名单，左栏折叠并给出登录引导")
    @DisplayName("SA-50 未登录公开模式：带 activityId 仍可查看名单，左栏折叠并给出登录引导")
    void publicModeWithoutToken() {
        // 前置数据：运行时反查一个确实有中奖记录的活动。
        // 禁止写死 activityId（历史活动 33-38 等随时会被清理）；数据缺失时明确失败并说明是前置问题。
        String activityId = ApiSupport.firstActivityIdWithRecords(adminToken);
        assertNotNull(activityId,
                "前置条件不满足：库中找不到「含有中奖记录」的活动，无法验证公开名单页（数据前置问题，非页面缺陷）");

        clearAuth();   // 关键：本用例必须是未登录状态，否则会走模式 B
        open("/public-winners.html?activityId=" + activityId);

        // ---------- ① 左栏必须折叠 ----------
        // 依据 public-winners.html:258-261：无 token 且带 activityId 时走
        //   showNoLoginHint() → collapseSide(hint) → $('#split').addClass('no-side')（:338-341）
        // 并且 CSS :40 `.split.no-side .picker { display: none; }` 才真正隐藏选择器。
        assertNotNull(waitForElement(By.id("split"), 10),
                "公开名单页应有主分栏 #split（public-winners.html:212）");
        boolean collapsed = false;
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            String cls = String.valueOf(driver.findElement(By.id("split")).getAttribute("class"));
            if (cls.contains("no-side")) {
                collapsed = true;
                break;
            }
            sleep(200);
        }
        assertTrue(collapsed,
                "未登录且带 activityId 时应折叠左栏（public-winners.html:258-261 → :339 addClass('no-side')），"
                        + "实际 #split class=" + driver.findElement(By.id("split")).getAttribute("class")
                        + "，URL=" + driver.getCurrentUrl());

        // ---------- ② 登录引导文案 ----------
        // 依据 public-winners.html:334 的 hint 原文（由 :340 写入 #sideHint）
        assertTrue(waitForTextContains(By.id("sideHint"), "登录后可选择全部活动", 10),
                "折叠左栏的同时应给出登录引导（public-winners.html:334），实际 #sideHint="
                        + safeText(By.id("sideHint")));

        // ---------- ③ 名单条数与表格行数必须一致 ----------
        // 依据 public-winners.html:389 —— 有记录时 $('#listCount').text('共 ' + records.length + ' 条')；
        // :394-417 用 .winner-table 渲染每条记录一行。这两个数字来自同一份数据，
        // 对不上就说明渲染循环漏行/多行（历史上有过"只渲染第一页"的类似缺陷）。
        assertTrue(waitForTextMatches(By.id("listCount"), "^共 \\d+ 条$", 20),
                "公开名单应展示条数「共 N 条」（public-winners.html:389），实际 #listCount="
                        + safeText(By.id("listCount")) + "，页面文本前 300 字="
                        + safeText(By.tagName("body")));
        int declared = parseNumber(safeText(By.id("listCount")));
        assertTrue(declared > 0, "该活动已确认含有中奖记录，条数应大于 0，实际=" + declared);

        int renderedRows = waitForElementCount(By.cssSelector("#listBody table.winner-table tbody tr"), 1, 10);
        assertEquals(declared, renderedRows,
                "名单表格行数应等于「共 N 条」声称的条数（public-winners.html:389 与 :394-417），"
                        + "声称=" + declared + "，实际渲染=" + renderedRows);

        // ---------- ④ 不能落到"暂无记录"空态 ----------
        // 依据 public-winners.html:384-386：0 条时才渲染 .empty「该活动暂无中奖记录」。
        // 我们选的活动接口侧确认有记录，若页面显示空态 → 渲染链路断了。
        assertFalse(safeText(By.id("listBody")).contains("该活动暂无中奖记录"),
                "该活动已确认含有中奖记录，页面不应显示空态（public-winners.html:384-386），实际 #listBody="
                        + safeText(By.id("listBody")));

        // ---------- ⑤ 结果海报链接参数必须齐全 ----------
        // 依据 public-winners.html:427-440 updateDrawLink：
        //   URLSearchParams({activityId, activityName:…||'中奖名单', valid:'false', hideButton:'true'})
        // → href='draw.html?...'。四个参数缺一个，draw.html 就会走错分支
        // （valid 缺失会被当成可抽状态，hideButton 缺失会显示上下步按钮，让访客以为能继续操作）。
        var drawLink = waitForElement(By.id("drawLink"), 5);
        assertNotNull(drawLink, "名单下方应有结果海报入口 #drawLink（public-winners.html:233）");
        assertTrue(drawLink.isDisplayed(), "有活动 id 时海报入口应可见（:429-431 无 id 才 hide）");
        String href = String.valueOf(drawLink.getAttribute("href"));
        screenshot("SA-50-public-winners");

        assertTrue(href.contains("draw.html"), "海报链接应指向 draw.html，实际 href=" + href);
        assertTrue(href.contains("activityId=" + activityId),
                "海报链接必须携带当前 activityId=" + activityId + "（public-winners.html:434），实际 href=" + href);
        assertTrue(href.contains("valid=false"),
                "海报链接必须带 valid=false（public-winners.html:436），否则 draw.html 会进入抽奖流程，实际 href=" + href);
        assertTrue(href.contains("hideButton=true"),
                "海报链接必须带 hideButton=true（public-winners.html:437），否则访客会看到上下步按钮，实际 href=" + href);
    }

    @Test
    @Story("SA-51 已登录模式：活动选择器数量一致，点击后名单与海报链接联动")
    @DisplayName("SA-51 已登录模式：活动选择器数量一致，点击后名单与海报链接联动")
    void pickerModeWithToken() {
        // 前置数据：本页选择器只列 COMPLETED 活动（public-winners.html:272 的 status:'COMPLETED'），
        // 进行中的活动还没开奖、没有名单可看，不会出现在左栏。
        String completedId = ApiSupport.firstCompletedActivityId(adminToken);
        assertNotNull(completedId,
                "前置条件不满足：库中找不到 status=COMPLETED 的活动，无法验证活动选择器"
                        + "（public-winners.html:272 只列已结束活动）");

        injectToken(adminToken, "ADMIN");
        open("/public-winners.html");

        // ---------- ① 左栏必须展开 ----------
        // 依据 public-winners.html:284 expandSide() → $('#split').removeClass('no-side')
        boolean expanded = false;
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            var split = driver.findElements(By.id("split"));
            if (!split.isEmpty()
                    && !String.valueOf(split.get(0).getAttribute("class")).contains("no-side")
                    && !driver.findElements(By.cssSelector("#actBtns button.act-btn")).isEmpty()) {
                expanded = true;
                break;
            }
            sleep(200);
        }
        assertTrue(expanded,
                "已登录且存在已结束活动时左栏应展开并渲染活动按钮（public-winners.html:284-300），"
                        + "实际 #split class=" + safeAttr("split", "class")
                        + "，按钮数=" + driver.findElements(By.cssSelector("#actBtns button.act-btn")).size()
                        + "，提示=" + safeText(By.id("sideHint")));

        // ---------- ② #actCount 声称的数量必须等于实际渲染的按钮数 ----------
        // 依据 public-winners.html:285 $('#actCount').text(records.length + ' 个')
        //      与 :287-300 records.forEach 渲染 .act-btn
        // 数字与按钮数不一致，说明渲染循环被跳过（例如 records 里混入了无 activityId 的元素）。
        assertTrue(waitForTextMatches(By.id("actCount"), "^\\d+ 个$", 15),
                "左栏标题应展示活动数量「N 个」（public-winners.html:215/:285），实际="
                        + safeText(By.id("actCount")));
        int declared = parseNumber(safeText(By.id("actCount")));
        int buttons = waitForElementCount(By.cssSelector("#actBtns button.act-btn"), 1, 15);
        assertEquals(declared, buttons,
                "活动按钮数量应等于 #actCount 声称的数量（public-winners.html:285 与 :287-300），"
                        + "声称=" + declared + "，实际=" + buttons);

        // ---------- ③ 目标已结束活动必须出现在选择器里 ----------
        var option = driver.findElements(By.cssSelector("#actBtns button[data-id='" + completedId + "']"));
        assertFalse(option.isEmpty(),
                "接口返回的已结束活动 id=" + completedId
                        + " 应出现在左栏选择器中（public-winners.html:294 写入 data-id），实际按钮 id 列表="
                        + driver.findElements(By.cssSelector("#actBtns button.act-btn")).stream()
                        .map(b -> b.getAttribute("data-id")).toList());
        String optionName = option.get(0).getText().trim();
        assertFalse(optionName.isEmpty(),
                "活动按钮应显示活动名称（public-winners.html:293 写入 .nm 文本）");

        // ---------- ④ 点击 → 右栏标题与选中态联动 ----------
        // 依据 public-winners.html:295-298 —— 点击时 markActive(this) + loadRecords(a.activityId, a.activityName)；
        // loadRecords 在 :355 把活动名写进 #listTitle，并在 :370 先同步海报链接。
        option.get(0).click();
        assertTrue(waitForTextContains(By.id("listTitle"), optionName, 10),
                "点击活动后右栏标题应变为该活动名（public-winners.html:297 → :355），"
                        + "期望=" + optionName + "，实际 #listTitle=" + safeText(By.id("listTitle")));
        assertTrue(String.valueOf(driver.findElement(
                        By.cssSelector("#actBtns button[data-id='" + completedId + "']")).getAttribute("class"))
                        .contains("active"),
                "被点击的活动按钮应带 .active 高亮（public-winners.html:349-352 markActive）");

        // ---------- ⑤ 海报链接必须指向刚点击的活动 ----------
        String href = String.valueOf(driver.findElement(By.id("drawLink")).getAttribute("href"));
        assertTrue(href.contains("activityId=" + completedId),
                "点击活动后海报链接应同步为该活动（public-winners.html:370 → :434），实际 href=" + href);
        assertTrue(href.contains("valid=false") && href.contains("hideButton=true"),
                "海报链接应带 valid=false 与 hideButton=true（public-winners.html:436-437），实际 href=" + href);

        // ---------- ⑥ 名单区：有记录则行数与条数一致，无记录则确定空态 ----------
        // 已结束活动不一定有中奖记录（可能未开奖就结束），两种都是合法业务状态，
        // 但必须"二选一且各自确定"：空态文案来自 :384-386，条数来自 :389。
        String listText = safeText(By.id("listBody"));
        screenshot("SA-51-public-winners-picker");
        assertFalse(listText.contains("查询失败"),
                "名单区不应出现「查询失败」（public-winners.html:380），实际=" + listText);

        if (listText.contains("该活动暂无中奖记录")) {
            System.out.println("[SA-51] 活动 " + completedId + "（" + optionName + "）无中奖记录，命中空态分支");
        } else {
            assertTrue(waitForTextMatches(By.id("listCount"), "^共 \\d+ 条$", 15),
                    "有记录时应展示「共 N 条」（public-winners.html:389），实际 #listCount="
                            + safeText(By.id("listCount")) + "，名单区文本=" + listText);
            int stated = parseNumber(safeText(By.id("listCount")));
            int rows = waitForElementCount(By.cssSelector("#listBody table.winner-table tbody tr"), 1, 10);
            assertEquals(stated, rows,
                    "名单表格行数应等于「共 N 条」声称的条数（public-winners.html:389 与 :394-417），"
                            + "声称=" + stated + "，实际渲染=" + rows);
        }
    }

    /** 从「共 12 条」「7 个」这类文案里取第一个整数；解析不到返回 -1（让后续比较自然失败） */
    private int parseNumber(String text) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)")
                .matcher(text == null ? "" : text);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    /** 读取元素文本；元素不存在返回空串（仅用于构造断言消息） */
    private String safeText(By by) {
        var list = driver.findElements(by);
        return list.isEmpty() ? "" : list.get(0).getText().trim();
    }

    /** 读取元素属性；元素不存在返回空串（仅用于构造断言消息，不参与判定） */
    private String safeAttr(String id, String attr) {
        var list = driver.findElements(By.id(id));
        return list.isEmpty() ? "" : String.valueOf(list.get(0).getAttribute(attr));
    }
}
