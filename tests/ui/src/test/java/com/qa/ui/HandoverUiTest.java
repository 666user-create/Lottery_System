package com.qa.ui;

import org.junit.jupiter.api.BeforeAll;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UI 用例 · 工单管理（SA-28 ~ SA-31）
 *
 * ⚠️ 本文件的 SA-28 是「功能可用性」用例：断言**表格渲染行数 > 0**。
 *    本项目曾存在 BUG-002（前端成功码误判 code!==0 应为 200），
 *    导致工单列表永远空白、处理按钮永远失败——只有"渲染行数/状态变化"这类断言才能抓到，
 *    仅断"页面可访问/元素存在"会漏检。
 *
 * <p>2026-10-18 重构（对齐 10-16 前端改版后的真实交互）：
 * <ul>
 *   <li>操作列由「处理中 / 已解决 / 删除」三个大按钮收成一个「更多 ∨」下拉
 *       （{@code admin-handover.html:193-204}），菜单项文案为
 *       <b>标记为处理中 / 标记为已解决 / 删除工单</b>。</li>
 *   <li>处理不再弹原生 {@code prompt} 与成功 {@code alert}：点菜单项后在本行下方
 *       <b>展开行内备注区</b>（{@code #procRemark} + 「保存」按钮，见 :296-326），
 *       保存成功后就地刷新列表（:333-354），全程无弹窗。</li>
 *   <li>因此旧写法 {@code window.prompt = ...} 覆盖与 {@code waitForAlertAndAccept()}
 *       都已失效，改为断言<b>目标行的状态标签真实变化</b>（用 {@code tr[data-row]} 精确定位，
 *       避免列表重排后行序变化导致误判）。</li>
 * </ul>
 */
@Feature("工单管理")
public class HandoverUiTest extends BaseUiTest {

    private static String adminToken;
    private static String userToken;

    @BeforeAll
    static void prepare() {
        adminToken = ApiSupport.adminToken();
        userToken = ApiSupport.normalUserToken();
        // 前置数据：创建 2 条工单，保证列表有数据可断言
        ApiSupport.createHandover(userToken);
        ApiSupport.createHandover(userToken);
    }

    @Test
    @Story("SA-28 工单列表渲染：表格出现数据行（可抓 BUG-002 类缺陷）")
    @DisplayName("SA-28 工单列表渲染：表格出现数据行（可抓 BUG-002 类缺陷）")
    void handoverListRenders() {
        injectToken(adminToken, "ADMIN");
        open("/admin-handover.html");
        sleep(3000);
        dismissAlertIfPresent();
        screenshot("SA-28-handover-list");

        int rows = waitForElementCount(By.cssSelector("#tbody tr"), 1, 12);
        assertTrue(rows > 0,
                "工单列表应渲染数据行（前置已创建 2 条工单），实际行数=" + rows
                        + "，页面文本前 200 字=" + bodyText().substring(0, Math.min(200, bodyText().length())));
        // 每行都应由 render() 构造出「更多 ∨」操作入口（:193），否则操作列是空的
        assertFalse(driver.findElements(By.cssSelector("#tbody tr[data-row] .more-link")).isEmpty(),
                "工单行应包含「更多」操作入口");
    }

    @Test
    @Story("SA-29 工单状态筛选：切换「待处理」后列表只留待处理工单")
    @DisplayName("SA-29 工单状态筛选：切换「待处理」后列表只留待处理工单")
    void handoverStatusFilter() {
        injectToken(adminToken, "ADMIN");
        open("/admin-handover.html");
        sleep(3000);
        dismissAlertIfPresent();

        boolean clicked = clickIfPresent(By.cssSelector(".status-btn[data-status='PENDING']"))
                || clickIfPresent(By.xpath("//button[contains(text(),'待处理')]"));
        assertTrue(clicked, "应存在「待处理」筛选按钮");
        sleep(2500);
        dismissAlertIfPresent();
        screenshot("SA-29-handover-filter");

        // 2026-10-18 修复：原断言 rows >= 0 恒真（形同虚设）。改为真断言：
        // 筛选后列表里渲染出来的每一行，状态标签都必须是「待处理」——
        // 这样筛选参数没拼进请求（如 status 拼错/漏传）时会立刻暴露。
        int rows = waitForElementCount(By.cssSelector("#tbody tr[data-row]"), 1, 10);
        assertTrue(rows > 0, "「待处理」筛选下应有数据行（前置工单初始即待处理），实际=" + rows);
        var tags = driver.findElements(By.cssSelector("#tbody tr[data-row] .status-tag"));
        assertFalse(tags.isEmpty(), "筛选后每行都应渲染状态标签");
        for (var tag : tags) {
            assertEquals("待处理", tag.getText().trim(),
                    "「待处理」筛选下不应混入其他状态的工单");
        }
        // 筛选按钮本身应处于激活态（:359 会 remove/add active）
        assertTrue(driver.findElement(By.cssSelector(".status-btn[data-status='PENDING']"))
                        .getAttribute("class").contains("active"),
                "「待处理」筛选按钮应显示激活态");
    }

    @Test
    @Story("SA-30 工单处理：标记「处理中」后状态真实变化")
    @DisplayName("SA-30 工单处理：标记「处理中」后状态真实变化")
    void handoverProcessToProcessing() {
        injectToken(adminToken, "ADMIN");
        open("/admin-handover.html");
        sleep(3000);
        dismissAlertIfPresent();

        String rowId = processFirstHandover("处理中", "PROCESSING");
        assertNotNull(rowId, "前置条件：工单列表应有数据行");
        screenshot("SA-30-handover-processing");
    }

    @Test
    @Story("SA-31 工单处理：标记「已解决」并可被状态筛选查到")
    @DisplayName("SA-31 工单处理：标记「已解决」并可被状态筛选查到")
    void handoverProcessToResolved() {
        injectToken(adminToken, "ADMIN");
        open("/admin-handover.html");
        sleep(3000);
        dismissAlertIfPresent();

        String rowId = processFirstHandover("已解决", "RESOLVED");
        assertNotNull(rowId, "前置条件：工单列表应有数据行");

        // 切到「已解决」筛选（明确使用 status-btn，避免歧义），验证状态真实变更可查
        boolean filtered = clickIfPresent(By.cssSelector(".status-btn[data-status='RESOLVED']"));
        assertTrue(filtered, "应存在「已解决」筛选按钮");
        sleep(3000);
        dismissAlertIfPresent();
        screenshot("SA-31-handover-resolved");

        // 用被处理的那一行 id 精确校验：它必须出现在「已解决」筛选结果里
        int hit = waitForElementCount(By.cssSelector("#tbody tr[data-row='" + rowId + "']"), 1, 12);
        assertTrue(hit > 0, "标记已解决后，在「已解决」筛选下应能查到工单 id=" + rowId + "，实际命中=" + hit);
    }

    // ==================== 复用的行内处理流程 ====================

    /**
     * 对列表第一行执行一次「标记为 xx」行内处理，并断言该行状态标签真实变为目标值。
     *
     * <p>完整交互链路（全部无弹窗）：
     * <ol>
     *   <li>点该行「更多 ∨」→ {@code toggleMore()} 给 {@code .more-wrap} 加 {@code open} 类；</li>
     *   <li>点菜单项「标记为 xx」→ {@code pickStatus()} → {@code process()} 在该行下方插入
     *       {@code .expand-row}（含 {@code #procRemark} 与「保存」按钮）；</li>
     *   <li>填备注后点「保存」→ {@code saveProcess()} 发 POST {@code /api/handover/process}；</li>
     *   <li>成功后 {@code loadList()} 重刷列表 → 该行状态标签变为目标文案。</li>
     * </ol>
     *
     * @return 被处理的工单行 id（{@code tr[data-row]} 的值）；无数据行时返回 null
     */
    private String processFirstHandover(String targetLabel, String targetCode) {
        if (waitForElementCount(By.cssSelector("#tbody tr[data-row]"), 1, 12) == 0) {
            return null;
        }
        // 先记住要处理哪一行：列表刷完后行序可能变化，必须按 id 回查而不是"看第一行"
        String rowId = driver.findElement(By.cssSelector("#tbody tr[data-row]")).getAttribute("data-row");
        assertNotNull(rowId, "工单行应带 data-row 属性用于定位");

        // ① 展开该行的「更多 ∨」菜单
        boolean opened = clickIfPresent(By.cssSelector("#tbody tr[data-row='" + rowId + "'] .more-link"));
        assertTrue(opened, "工单行应有「更多」操作入口");
        // 菜单靠 .more-wrap.open 控制 display:block，未展开时点不到菜单项
        assertNotNull(waitForElement(
                        By.cssSelector("#tbody tr[data-row='" + rowId + "'] .more-wrap.open .more-menu a"), 5),
                "「更多」菜单应能展开");

        // ② 选「标记为 xx」
        boolean picked = clickIfPresent(By.xpath(
                "//tbody//tr[@data-row='" + rowId + "']//div[contains(@class,'more-wrap')]"
                        + "//a[contains(text(),'标记为" + targetLabel + "')]"));
        assertTrue(picked, "菜单应有「标记为" + targetLabel + "」项");

        // ③ 行内备注区展开（替代旧的原生 prompt）
        assertNotNull(waitForElement(By.id("procRemark"), 6), "应展开行内备注输入框 #procRemark");
        type(By.id("procRemark"), "QA-UI 自动化备注-" + targetCode);
        // ---- 断言：行内处理区回显的目标状态，必须等于本次点击的菜单项 ----
        //
        // 依据 admin-handover.html:296-326 的 process(id, status)：点击「标记为 xx」后它动态拼出
        //   <tr class='expand-row'>                                   （:301）
        //   <td colspan="9">                                          （:303）
        //   <div class="expand-err" id="procErr"></div>                （:305）
        //   <div class="expand-field"><label>目标状态</label>
        //        <div class="expand-status">statusLabel(status)</div></div>  （:306-307）
        // 并把这一行插到被点击行的下一行（:317），所以 .expand-status 是"行内专属"节点。
        //
        // 【修复前】assertTrue(bodyText().contains(targetLabel), "行内处理区应回显目标状态…")
        //   问题：bodyText() 返回的是**整页可见文本**，而「处理中」「已解决」这两个词本来就
        //   印在页面顶部的状态筛选按钮上（admin-handover.html:88-89 的
        //   <button class="btn status-btn" data-status="PROCESSING">处理中</button> 等）。
        //   于是这句断言与"展开行有没有正确回显"毫无关系 —— 哪怕展开行渲染成全空、
        //   哪怕点错了菜单项，只要筛选按钮在，断言就通过。属于典型的"存在性断言冒充行为断言"。
        //
        // 【修复后】
        //   ① 只读展开行内的 .expand-status，并断言文本与目标状态**精确相等**；
        //      点错菜单项（例如点了「标记为已解决」却期望「处理中」）会立刻失败。
        //   ② 同时断言行内错误位 #procErr 为空 —— 后端失败时 admin-handover.html:335
        //      会把错误写进这个节点（而不是弹窗），只看最终 .status-tag 的话，
        //      "保存失败但列表里恰好已是目标状态"这种情况会被误判为成功。
        var expandStatus = waitForElement(By.cssSelector("#tbody tr.expand-row .expand-status"), 5);
        assertNotNull(expandStatus,
                "点「标记为" + targetLabel + "」后应出现行内处理区并回显目标状态 .expand-status"
                        + "（admin-handover.html:306-307），实际未出现。页面文本前 300 字="
                        + bodyText().substring(0, Math.min(300, bodyText().length())));
        assertEquals(targetLabel, expandStatus.getText().trim(),
                "行内处理区回显的目标状态应与点击的菜单项一致（admin-handover.html:307 调用 statusLabel(status)）");

        var procErr = driver.findElements(By.id("procErr"));
        String procErrText = procErr.isEmpty() ? "" : procErr.get(0).getText().trim();
        assertTrue(procErrText.isEmpty(),
                "提交前行内处理区不应有错误提示（#procErr，admin-handover.html:305/335），实际=" + procErrText);

        // ④ 保存（旧实现这里会弹 prompt，现在无弹窗、直接提交）
        boolean saved = clickIfPresent(By.xpath(
                "//tr[contains(@class,'expand-row')]//button[contains(text(),'保存')]"));
        assertTrue(saved, "行内处理区应有「保存」按钮");

        // ⑤ 真断言：该行状态标签变为目标文案（失败时页面会在行内红字提示，故同时抓取文本）
        boolean changed = waitForTextContains(
                By.cssSelector("#tbody tr[data-row='" + rowId + "'] .status-tag"), targetLabel, 12);
        assertTrue(changed,
                "工单 id=" + rowId + " 标记为「" + targetLabel + "」后状态未变化（BUG-002 修复前"
                        + "此处会因 code!==0 误判而失败），页面文本前 300 字="
                        + bodyText().substring(0, Math.min(300, bodyText().length())));
        return rowId;
    }
}
