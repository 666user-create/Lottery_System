package com.qa.ui;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UI 用例 · 数据统计（SA-48 ~ SA-49）—— 页面 {@code statistics.html}
 *
 * <p>本页此前**没有任何自动化覆盖**（2026-10-18 新增）。
 * 它消费 {@code /api/stat/overview} 与 {@code /api/stat/activity}，这两个接口在
 * LoginInterceptor:100-101 被列为 adminOnly，所以本页天然是"管理员专属数据面"。
 *
 * <h3>断言深度</h3>
 * <ul>
 *   <li>SA-48 断言 7 张统计卡的**标签集合与顺序**（renderOverview 里 cards 数组是硬编码的，
 *       见 statistics.html:278-286），并校验每张卡的数值位已渲染。
 *       只断言"卡片数量 7"抓不到"标签配错数据"——例如把「注册用户数」接到 activityTotal 上，
 *       数字照常显示，但报表彻底错了；</li>
 *   <li>SA-49 做**跨控件一致性校验**：下拉框当前选中的活动名，必须出现在
 *       {@code #activitySummary} 的「活动名称」里（loadActivityOptions:335 会自动选中第一个活动
 *       并调用 loadActivityStat）。两边对不上，说明统计接口拿错了 activityId。</li>
 * </ul>
 */
@Feature("数据统计")
public class StatisticsUiTest extends BaseUiTest {

    private static String adminToken;

    @BeforeAll
    static void prepareToken() {
        adminToken = ApiSupport.adminToken();
    }

    @Test
    @Story("SA-48 概览统计：7 张指标卡的标签与顺序、数值均已渲染")
    @DisplayName("SA-48 概览统计：7 张指标卡的标签与顺序、数值均已渲染")
    void overviewCardsRendered() {
        injectToken(adminToken, "ADMIN");
        open("/statistics.html");

        // ---------- ① 7 张卡必须全部渲染 ----------
        // 依据 statistics.html:278-286 cards 数组固定 7 项，:288-295 一次性写入 #overviewCards
        int cards = waitForElementCount(By.cssSelector("#overviewCards .stat-card"), 7, 20);
        assertEquals(7, cards,
                "概览区应渲染 7 张指标卡（statistics.html:278-286），实际=" + cards
                        + "，页面文本前 300 字=" + safeText(By.tagName("body")));

        // ---------- ② 标签内容与顺序必须完全对应（防止"标签配错数据"） ----------
        // 依据 statistics.html:279-285 的 label 顺序
        List<String> labels = driver.findElements(By.cssSelector("#overviewCards .stat-card .label"))
                .stream().map(e -> e.getText().trim()).toList();
        assertEquals(List.of("活动总数", "进行中活动", "已完成活动", "已开奖活动",
                        "奖品总数", "注册用户数", "累计中奖人次"), labels,
                "7 张卡的标签与顺序应与 statistics.html:279-285 一致，实际=" + labels);

        // ---------- ③ 每张卡的数值位必须有内容 ----------
        // 依据 statistics.html:291-292：value == null 时渲染 0，所以"空"本身就是异常，
        // 说明 renderOverview 没跑到（例如接口 403 后提前 return，页面只剩空卡片）。
        List<WebElement> values = driver.findElements(By.cssSelector("#overviewCards .stat-card .value"));
        assertEquals(7, values.size(), "每张指标卡都应有 .value 数值容器，实际=" + values.size());
        for (int i = 0; i < values.size(); i++) {
            String v = values.get(i).getText().trim();
            // .value 里除数字还嵌了单位 <span class="unit">（:292），因此断言"以数字开头"
            assertTrue(v.matches("^\\d+.*"),
                    "第 " + (i + 1) + " 张卡（" + labels.get(i) + "）的数值应以数字开头"
                            + "（statistics.html:291-292 缺值会渲染 0），实际=" + v);
        }
        screenshot("SA-48-statistics-overview");
    }

    @Test
    @Story("SA-49 单活动统计：下拉选中项与概览区活动名一致、奖品明细表结构正确")
    @DisplayName("SA-49 单活动统计：下拉选中项与概览区活动名一致、奖品明细表结构正确")
    void activityStatConsistency() {
        injectToken(adminToken, "ADMIN");
        open("/statistics.html");

        // ---------- ① 活动下拉必须被填充 ----------
        // 依据 statistics.html:311-334 loadActivityOptions：选项来自 /activity/find-list，
        // 每个 option 的 value 是 activityId、文本是 activityName。
        int optionCount = waitForElementCount(By.cssSelector("#activitySelect option"), 1, 20);
        assertTrue(optionCount >= 1,
                "活动下拉框应至少有一个选项（statistics.html:332-334），实际=" + optionCount
                        + "，页面文本前 300 字=" + safeText(By.tagName("body")));

        Select select = new Select(driver.findElement(By.id("activitySelect")));
        WebElement selected = select.getFirstSelectedOption();
        String selectedId = String.valueOf(selected.getAttribute("value"));
        String selectedName = selected.getText().trim();
        assertTrue(selectedId.matches("\\d+"),
                "选中项的 value 应是数字型 activityId（statistics.html:333），实际=" + selectedId);
        assertFalse(selectedName.isEmpty(), "选中项应有活动名称文本（statistics.html:333）");

        // ---------- ② 概览区必须展示同一活动（跨控件一致性） ----------
        // 依据 statistics.html:335 —— 加载完选项后立刻 loadActivityStat(records[0].activityId)，
        // :371-391 renderActivityStat 把活动名与 5 个指标写进 #activitySummary。
        // 若下拉框选中的是 A、概览区显示的却是 B（典型原因：把 activityId 传错/传了 undefined），
        // 用户看到的数据就与他的选择不符，而页面"看起来一切正常"。
        assertTrue(waitForTextContains(By.id("activitySummary"), "活动名称", 20),
                "概览区应渲染「活动名称」指标（statistics.html:376-379），实际="
                        + safeText(By.id("activitySummary")));
        String summary = safeText(By.id("activitySummary"));
        assertTrue(summary.contains(selectedName),
                "概览区显示的活动名应等于下拉框当前选中项（statistics.html:335/:377）。"
                        + "选中=" + selectedName + "（id=" + selectedId + "），概览区文本=" + summary);

        for (String metric : new String[]{"参与人数", "奖品总份数", "已中奖份数", "开奖进度"}) {
            assertTrue(summary.contains(metric),
                    "概览区应包含指标「" + metric + "」（statistics.html:380-389），实际=" + summary);
        }
        // 开奖进度是百分比（:386-389），必须有 % 号，否则说明 renderActivityStat 走了别的分支
        assertTrue(summary.contains("%"),
                "「开奖进度」应以百分比展示（statistics.html:386-389），实际=" + summary);

        // ---------- ③ 奖品明细表：有数据则 5 列，无数据则确定空态 ----------
        // 依据 statistics.html:223 空态 colspan=5「请选择活动」、
        // :396 该活动无奖品时 colspan=5「该活动暂无奖品」、:398-400 每个奖品一行 5 列。
        int rows = waitForElementCount(By.cssSelector("#prizeStatTableBody tr"), 1, 20);
        String tableText = safeText(By.id("prizeStatTableBody"));
        screenshot("SA-49-statistics-activity");

        assertFalse(tableText.contains("加载中"),
                "奖品明细表不应停留在「加载中…」（statistics.html:347），实际=" + tableText);
        assertFalse(tableText.contains("查询失败"),
                "奖品明细表不应出现「查询失败」（statistics.html:358/:366），实际=" + tableText);

        if ("该活动暂无奖品".equals(tableText)) {
            // 合法空态：该活动没有配置奖品（:396）
            System.out.println("[SA-49] 活动 " + selectedId + " 未配置奖品，命中空态分支");
        } else {
            assertTrue(rows >= 1, "奖品明细表应有数据行，实际行数=" + rows + "，表体文本=" + tableText);
            int cols = driver.findElement(By.cssSelector("#prizeStatTableBody tr"))
                    .findElements(By.xpath("./td")).size();
            assertEquals(5, cols,
                    "奖品明细表每行应有 5 列（statistics.html:398-400 行模板），实际=" + cols);
        }
    }

    /** 读取元素文本；元素不存在返回空串（仅用于构造断言消息） */
    private String safeText(By by) {
        var list = driver.findElements(by);
        return list.isEmpty() ? "" : list.get(0).getText().trim();
    }
}
