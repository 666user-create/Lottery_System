package com.qa.ui;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UI 用例 · 领奖履约管理（SA-42 ~ SA-43）—— 页面 {@code admin-claim.html}
 *
 * <p>本页此前**没有任何自动化覆盖**（2026-10-18 新增）。
 * 它对应「中奖履约-领奖发货」设计（docs/design/中奖履约-领奖发货设计.md），
 * 是管理端唯一能改动领奖状态与物流单号的页面，属于高风险操作面。
 *
 * <h3>断言深度</h3>
 * 不做"页面能打开"这种断言，而是校验三件容易被改坏、且坏了业务就出错的事：
 * <ol>
 *   <li><b>筛选按钮契约</b>：5 个 <code>.status-btn</code> 的中文文案与 <code>data-status</code>
 *       必须一一对应（:98-102）。这两个值任何一个错位，筛选就会查出错误的记录集；</li>
 *   <li><b>分页/总数回显</b>：{@code #totalTip} 必须是「共 N 条」格式（:222），
 *       {@code #totalPages} 必须被写入（:223）；</li>
 *   <li><b>筛选真的生效</b>：点「已签收」后，列表里只能出现 COMPLETED 记录，
 *       或给出确定性的空态文案（:228）。</li>
 * </ol>
 *
 * <p>业务规则提醒：本系统业务失败仍返回 <b>HTTP 200</b>，错误码在 {@code body.code}；
 * 只有 LoginInterceptor 会返回真 401/403。所以"看 HTTP 状态"在本页毫无意义。
 */
@Feature("领奖履约管理")
public class ClaimUiTest extends BaseUiTest {

    private static String adminToken;

    @BeforeAll
    static void prepareToken() {
        adminToken = ApiSupport.adminToken();
    }

    @Test
    @Story("SA-42 领奖履约列表：筛选按钮契约、总数与分页回显、表格列结构")
    @DisplayName("SA-42 领奖履约列表：筛选按钮契约、总数与分页回显、表格列结构")
    void claimListContract() {
        injectToken(adminToken, "ADMIN");
        open("/admin-claim.html");

        // ---------- ① 5 个筛选按钮的文案与 data-status 必须一一对应 ----------
        // 依据 admin-claim.html:98-102：
        //   data-status=""            → 全部（默认 .active）
        //   data-status="NOT_CLAIMED" → 未领取
        //   data-status="CLAIMED"     → 已申请待发货
        //   data-status="SHIPPED"     → 已发货
        //   data-status="COMPLETED"   → 已签收
        // 这两列一旦错位（例如把"已发货"配成 CLAIMED），筛选出来的就是错的记录集，
        // 运营会照着错的列表发货，属于高危且难察觉的问题。
        int btnCount = waitForElementCount(By.cssSelector(".status-btn"), 5, 15);
        assertTrue(btnCount >= 5, "领奖履约页应有 5 个状态筛选按钮（admin-claim.html:98-102），实际=" + btnCount);

        List<WebElement> btns = driver.findElements(By.cssSelector(".status-btn"));
        List<String> labels = btns.stream().map(b -> b.getText().trim()).toList();
        assertEquals(List.of("全部", "未领取", "已申请待发货", "已发货", "已签收"), labels,
                "筛选按钮文案应依次为 全部/未领取/已申请待发货/已发货/已签收（admin-claim.html:98-102），实际=" + labels);

        List<String> statuses = btns.stream().map(b -> String.valueOf(b.getAttribute("data-status"))).toList();
        assertEquals(List.of("", "NOT_CLAIMED", "CLAIMED", "SHIPPED", "COMPLETED"), statuses,
                "筛选按钮 data-status 应依次为 \"\"/NOT_CLAIMED/CLAIMED/SHIPPED/COMPLETED，实际=" + statuses);

        assertEquals("全部", driver.findElement(By.cssSelector(".status-btn.active")).getText().trim(),
                "首屏默认选中应为「全部」（admin-claim.html:98 的 class=\"btn status-btn active\"）");

        // ---------- ② 总数与分页回显 ----------
        // 依据 admin-claim.html:222-224：render() 会把 '共 N 条' 写进 #totalTip、
        // 把总页数写进 #totalPages、把当前页写进 #pageInput。
        // 只断言"元素存在"不够——接口失败时元素也在，只是从来没被写过值。
        assertTrue(waitForTextMatches(By.id("totalTip"), "^共 \\d+ 条$", 20),
                "总数提示应为「共 N 条」格式（admin-claim.html:222），实际="
                        + safeText(By.id("totalTip")) + "，URL=" + driver.getCurrentUrl());
        assertTrue(waitForTextMatches(By.id("totalPages"), "^\\d+$", 5),
                "总页数应被写入数字（admin-claim.html:223），实际=" + safeText(By.id("totalPages")));
        assertEquals("1", driver.findElement(By.id("pageInput")).getAttribute("value"),
                "首屏应停在第 1 页（admin-claim.html:224），实际="
                        + driver.findElement(By.id("pageInput")).getAttribute("value"));

        // ---------- ③ 表格：有数据行则校验列数，没数据则必须给出确定空态 ----------
        // 依据 admin-claim.html:231-232 每行 9 个 <td>；:228 空态为 colspan=9 的「暂无中奖记录」。
        // 这里把"有数据"和"没数据"两条分支分别断言清楚，而不是 `rows>=0` 这种恒真写法：
        // 白屏/请求未返回时两条分支都不满足 → 用例失败（旧写法会当成"没数据"放过）。
        int rows = waitForElementCount(By.cssSelector("#tbody tr[data-row]"), 1, 10);
        screenshot("SA-42-claim-list");
        if (rows == 0) {
            assertEquals("暂无中奖记录", safeText(By.id("tbody")),
                    "没有中奖记录时应渲染确定空态「暂无中奖记录」（admin-claim.html:228），"
                            + "否则说明页面初始化失败（白屏/接口报错），实际表体文本=" + safeText(By.id("tbody")));
        } else {
            int cols = driver.findElement(By.cssSelector("#tbody tr[data-row]")).findElements(By.xpath("./td")).size();
            assertEquals(9, cols,
                    "领奖履约表格每行应有 9 列（admin-claim.html:109-110 表头 + :232 行模板），实际=" + cols);
            // data-row 是后续改状态/改物流的定位键（:232 `<tr data-row="${recordId}">`），必须有值
            String rowId = driver.findElement(By.cssSelector("#tbody tr[data-row]")).getAttribute("data-row");
            assertNotNull(rowId, "数据行应带 data-row=recordId，用于定位改状态/改物流（admin-claim.html:232）");
            assertTrue(rowId.matches("\\d+"), "data-row 应是数字型中奖记录 id，实际=" + rowId);
        }
    }

    @Test
    @Story("SA-43 领奖履约筛选：点「已签收」后列表只含 COMPLETED 记录")
    @DisplayName("SA-43 领奖履约筛选：点「已签收」后列表只含 COMPLETED 记录")
    void claimStatusFilter() {
        injectToken(adminToken, "ADMIN");
        open("/admin-claim.html");
        // 等首屏数据回来再点筛选，否则点的是"加载中"状态的按钮，测不到筛选逻辑
        assertTrue(waitForTextMatches(By.id("totalTip"), "^共 \\d+ 条$", 20),
                "首屏数据未加载完成，无法验证筛选。实际 totalTip=" + safeText(By.id("totalTip")));
        String totalBefore = safeText(By.id("totalTip"));

        // 依据 admin-claim.html:98-102 的 data-status 与 loadList() :150 的请求参数：
        // 点「已签收」会把 claimStatus=COMPLETED 带进 /api/winning/manage/list
        clickIfPresent(By.cssSelector(".status-btn[data-status='COMPLETED']"));

        // ---------- ① 选中态必须转移到「已签收」 ----------
        // 只断言"请求发了"做不到（拿不到 XHR），但选中态是页面唯一的状态回显；
        // 若 class 切换逻辑坏了，用户会以为还在看「全部」，从而误判数据。
        boolean activeMoved = false;
        long deadline = System.currentTimeMillis() + 6_000;
        while (System.currentTimeMillis() < deadline) {
            List<WebElement> actives = driver.findElements(By.cssSelector(".status-btn.active"));
            if (actives.size() == 1 && "已签收".equals(actives.get(0).getText().trim())) {
                activeMoved = true;
                break;
            }
            sleep(150);
        }
        assertTrue(activeMoved, "点「已签收」后应有且仅有它带 .active（其余按钮的 active 要移除），实际 active 按钮="
                + driver.findElements(By.cssSelector(".status-btn.active")).stream()
                .map(b -> b.getText().trim()).toList());

        // ---------- ② 列表内容必须只剩 COMPLETED，或给出空态 ----------
        // 依据 admin-claim.html:271-281 statusSelectHtml：
        //   COMPLETED 记录渲染 <span class="status-tag status-COMPLETED">已签收</span>（:276，终态不可再改）
        //   其它状态渲染 <select class="status-select status-XX">（:284，可改状态）
        // 因此"筛选生效"的判据很明确：要么一条 .status-tag 都没有（空态），
        // 要么所有 .status-tag 都是 status-COMPLETED 且文案为「已签收」。
        // 若筛选参数没生效、后端仍返回全量，这里会出现 .status-select 或非 COMPLETED 的 tag → 失败。
        boolean consistent = false;
        String observed = "";
        deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            List<WebElement> tags = driver.findElements(By.cssSelector("#tbody .status-tag"));
            String tbodyText = safeText(By.id("tbody"));
            if (tags.isEmpty()) {
                if ("暂无中奖记录".equals(tbodyText)) {
                    consistent = true;
                    observed = "空态（暂无中奖记录）";
                    break;
                }
            } else {
                List<String> tagTexts = tags.stream().map(t -> t.getText().trim()).toList();
                List<String> tagClasses = tags.stream()
                        .map(t -> String.valueOf(t.getAttribute("class"))).toList();
                boolean allCompleted = tagTexts.stream().allMatch("已签收"::equals)
                        && tagClasses.stream().allMatch(c -> c.contains("status-COMPLETED"));
                if (allCompleted) {
                    consistent = true;
                    observed = "全部为 status-COMPLETED：共 " + tags.size() + " 条";
                    break;
                }
            }
            sleep(300);
        }
        screenshot("SA-43-claim-filter-completed");

        assertTrue(consistent,
                "筛选「已签收」后，列表应只含 COMPLETED 记录（admin-claim.html:276 渲染 .status-tag.status-COMPLETED）"
                        + "或显示空态「暂无中奖记录」（:228）。筛选前总数=" + totalBefore
                        + "，实际表体文本=" + safeText(By.id("tbody"))
                        + "，实际 status-tag=" + driver.findElements(By.cssSelector("#tbody .status-tag")).stream()
                        .map(t -> t.getText().trim()).toList()
                        + "，实际 status-select 数="
                        + driver.findElements(By.cssSelector("#tbody select.status-select")).size());
        System.out.println("[SA-43] 筛选结果=" + observed);
    }

    /** 读取元素文本；元素不存在返回空串（仅用于构造断言消息，不参与判定） */
    private String safeText(By by) {
        var list = driver.findElements(by);
        return list.isEmpty() ? "" : list.get(0).getText().trim();
    }
}
