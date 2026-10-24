package com.qa.ui;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UI 用例 · 操作日志（SA-46 ~ SA-47）—— 页面 {@code operation-log.html}
 *
 * <p>本页此前**没有任何自动化覆盖**（2026-10-18 新增）。
 * 数据来自后端的 {@code OperationLogInterceptor}（在 LoginInterceptor **之前**注册，
 * 见 AppConfig.java:63-68），是排查"谁在什么时候改了什么"的唯一审计来源，
 * 属于安全/合规面：日志页坏掉不会影响业务，但出事时就没有证据。
 *
 * <h3>断言深度</h3>
 * <ul>
 *   <li>SA-46 做**交叉一致性校验**：{@code #totalTip} 声称的条数（:351 '共 N 条'）
 *       必须和表格实际渲染的行数对得上（页面 pageSize=20，:245）。
 *       只断言"表格非空"抓不到"总数写 500 条、实际渲染 0 行"这种分页参数错配；</li>
 *   <li>SA-47 断言筛选区**重置契约**（:383-389 四个控件全部清空 + 回到第 1 页）
 *       与**首页边界提示**（:395 alert('已经是第一页')）。这两件事都在前端完成，
 *       不依赖库里有没有日志，因此是稳定可复现的。</li>
 * </ul>
 */
@Feature("操作日志")
public class OperationLogUiTest extends BaseUiTest {

    private static String adminToken;

    @BeforeAll
    static void prepareToken() {
        adminToken = ApiSupport.adminToken();
    }

    @Test
    @Story("SA-46 操作日志列表：表头 8 列、总数与实际渲染行数一致")
    @DisplayName("SA-46 操作日志列表：表头 8 列、总数与实际渲染行数一致")
    void logListRenders() {
        injectToken(adminToken, "ADMIN");
        open("/operation-log.html");

        // ---------- ① 表头必须是 8 列 ----------
        // 依据 operation-log.html:214-225：时间/操作人/身份/业务模块/操作/结果/耗时/IP
        // 列数变化意味着行模板（:364-374 也是 8 个 <td>）与表头错位，表格会整体错位。
        assertTrue(waitForElementCount(By.cssSelector("thead th"), 8, 10) >= 8,
                "操作日志表头应有 8 列（operation-log.html:214-225），实际="
                        + driver.findElements(By.cssSelector("thead th")).size());
        assertEquals(8, driver.findElements(By.cssSelector("thead th")).size(),
                "操作日志表头列数应恰为 8（operation-log.html:216-223）");

        // ---------- ② 总数提示格式 ----------
        // 依据 operation-log.html:351 —— renderLogs() 里 $('#totalTip').text('共 ' + total + ' 条')
        assertTrue(waitForTextMatches(By.id("totalTip"), "^共 \\d+ 条$", 20),
                "总数提示应为「共 N 条」格式（operation-log.html:351），实际="
                        + safeText(By.id("totalTip")) + "，URL=" + driver.getCurrentUrl());
        int total = parseTotal(safeText(By.id("totalTip")));
        assertTrue(total >= 0, "应能从总数提示中解析出数字，实际提示=" + safeText(By.id("totalTip")));

        // ---------- ③ 交叉一致性：总数 vs 实际渲染行数 ----------
        // 依据 operation-log.html:245 pageSize=20、:354-356 空列表渲染 colspan=8 的「暂无操作日志」。
        // 断言规则：
        //   total == 0 → 表格必须是空态文案（否则说明总量统计与列表查询条件不一致）
        //   total  > 0 → 渲染行数应为 min(total, 20)，且每行 8 列
        // 这是本用例的核心：数字与渲染必须自洽，不能各说各话。
        int rows = waitForElementCount(By.cssSelector("#logTableBody tr"), 1, 15);
        String tbodyText = safeText(By.id("logTableBody"));
        screenshot("SA-46-operation-log");

        if (total == 0) {
            assertEquals("暂无操作日志", tbodyText,
                    "总数为 0 时表格应渲染空态「暂无操作日志」（operation-log.html:355），实际=" + tbodyText);
        } else {
            int expected = Math.min(total, 20);
            assertEquals(expected, rows,
                    "表格渲染行数应为 min(总数, pageSize=20)（operation-log.html:245/:350），总数="
                            + total + "，期望行数=" + expected + "，实际行数=" + rows);
            WebElement firstRow = driver.findElement(By.cssSelector("#logTableBody tr"));
            int cols = firstRow.findElements(By.xpath("./td")).size();
            assertEquals(8, cols,
                    "数据行应有 8 列，与表头一一对应（operation-log.html:364-374），实际=" + cols);
            // 第一格是时间列（:365 formatDateTime），必须是非空的日期串，不能是「—」以外的空白
            String firstCell = firstRow.findElements(By.xpath("./td")).get(0).getText().trim();
            assertTrue(firstCell.matches("\\d{4}-\\d{2}-\\d{2}.*") || firstCell.equals("—"),
                    "首列应为格式化时间（operation-log.html:365），实际=" + firstCell);
        }
    }

    @Test
    @Story("SA-47 操作日志筛选：重置清空四个条件并回到第 1 页、首页再上一页给边界提示")
    @DisplayName("SA-47 操作日志筛选：重置清空四个条件并回到第 1 页、首页再上一页给边界提示")
    void logFilterResetAndFirstPageBoundary() {
        injectToken(adminToken, "ADMIN");
        open("/operation-log.html");
        assertTrue(waitForTextMatches(By.id("totalTip"), "^共 \\d+ 条$", 20),
                "首屏数据未加载完成，无法验证筛选。实际 totalTip=" + safeText(By.id("totalTip")));

        // ---------- ① 先设两个筛选条件 ----------
        // 依据 operation-log.html:180-197：模块与身份都是 <select>，选项值是中文模块名 / 身份枚举
        new Select(driver.findElement(By.id("moduleSelect"))).selectByValue("活动模块");
        new Select(driver.findElement(By.id("identitySelect"))).selectByValue("ADMIN");
        assertEquals("活动模块", new Select(driver.findElement(By.id("moduleSelect")))
                .getFirstSelectedOption().getAttribute("value"), "模块筛选应被设为「活动模块」");
        assertEquals("ADMIN", new Select(driver.findElement(By.id("identitySelect")))
                .getFirstSelectedOption().getAttribute("value"), "身份筛选应被设为 ADMIN");

        // 查询走 search() → fetchLogs(1)（:379-381），回来会把 pageInput 写成 1
        clickIfPresent(By.cssSelector("button.btn-query"));
        assertTrue(waitForTextMatches(By.id("totalTip"), "^共 \\d+ 条$", 20),
                "查询后总数提示应重新渲染，实际=" + safeText(By.id("totalTip")));

        // ---------- ② 重置必须清空四个条件 ----------
        // 依据 operation-log.html:383-389 resetFilter()：
        //   moduleSelect/identitySelect/startTime/endTime 全部 val('') 后 fetchLogs(1)
        // 只清界面不发请求、或只发请求不清界面，都会让用户对着错的筛选条件看数据。
        clickIfPresent(By.cssSelector("button.btn-reset"));
        boolean cleared = false;
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            String module = driver.findElement(By.id("moduleSelect")).getAttribute("value");
            String identity = driver.findElement(By.id("identitySelect")).getAttribute("value");
            String start = driver.findElement(By.id("startTime")).getAttribute("value");
            String end = driver.findElement(By.id("endTime")).getAttribute("value");
            String page = driver.findElement(By.id("pageInput")).getAttribute("value");
            if ("".equals(module) && "".equals(identity) && (start == null || start.isEmpty())
                    && (end == null || end.isEmpty()) && "1".equals(page)) {
                cleared = true;
                break;
            }
            sleep(200);
        }
        screenshot("SA-47-operation-log-reset");
        assertTrue(cleared,
                "点「重置」后应清空模块/身份/起止日期并回到第 1 页（operation-log.html:383-389），实际 "
                        + "module=" + driver.findElement(By.id("moduleSelect")).getAttribute("value")
                        + " identity=" + driver.findElement(By.id("identitySelect")).getAttribute("value")
                        + " start=" + driver.findElement(By.id("startTime")).getAttribute("value")
                        + " end=" + driver.findElement(By.id("endTime")).getAttribute("value")
                        + " page=" + driver.findElement(By.id("pageInput")).getAttribute("value"));

        // ---------- ③ 第 1 页再点「上一页」必须给出边界提示 ----------
        // 依据 operation-log.html:391-397 previousPage()：currentPage<=1 时 alert('已经是第一页')
        // 若这里静默无响应，用户会以为按钮坏了；若真的翻到第 0 页，请求参数会非法。
        clearLastAlertText();
        clickIfPresent(By.xpath("//button[contains(text(),'上一页')]"));
        boolean alerted = waitForAlertAndAccept(8);
        assertTrue(alerted, "在第 1 页点「上一页」应弹出边界提示（operation-log.html:395），实际未捕获弹窗");
        assertEquals("已经是第一页", lastAlertText,
                "边界提示应为 operation-log.html:395 的原文，实际=" + lastAlertText);
        assertEquals("1", driver.findElement(By.id("pageInput")).getAttribute("value"),
                "点「上一页」被边界拦住后仍应停在第 1 页，实际="
                        + driver.findElement(By.id("pageInput")).getAttribute("value"));
    }

    /** 从「共 N 条」里解析 N；解析不到返回 -1（让后续比较自然失败，而不是伪装成 0） */
    private int parseTotal(String tip) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("共\\s*(\\d+)\\s*条")
                .matcher(tip == null ? "" : tip);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    /** 读取元素文本；元素不存在返回空串（仅用于构造断言消息） */
    private String safeText(By by) {
        var list = driver.findElements(by);
        return list.isEmpty() ? "" : list.get(0).getText().trim();
    }
}
