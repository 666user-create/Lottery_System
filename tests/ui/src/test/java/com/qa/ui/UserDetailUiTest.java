package com.qa.ui;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用户详情页（user-detail.html，2026-10-20 新增）UI 自动化 —— SA-59 ~ SA-64。
 *
 * <p>页面契约（源码 {@code src/main/resources/static/user-detail.html}）：
 * <ul>
 *   <li>由 {@code user-list.html} 行内「详情」按钮带 {@code ?userId=} 跳转而来；</li>
 *   <li>调 {@code GET /user/base-user/find-detail}（adminOnly，LoginInterceptor.java:89）；</li>
 *   <li>成功 → 三段卡片（基本资料 / 收货信息 / 时间信息）；业务失败 → 红字错误态；</li>
 *   <li>401 → auth-guard alert + 跳管理端登录页；403 → alert「权限不足」停留本页（写 body 痕迹）。</li>
 * </ul>
 *
 * <p>断言口径遵守基类约束（P-7/P-8/P-10）：
 * 等待方法的返回值必须被断言；期望文本不允许空串；不用动画/可见性状态当成功判据。
 */
@Feature("人员管理")
@Story("用户详情页（user-detail.html）")
@DisplayName("用户详情页（user-detail）")
class UserDetailUiTest extends BaseUiTest {

    /** 从用户列表接口取第一个 NORMAL 用户的 [id, 姓名]（详情页必须与列表同源互证） */
    private String[] firstNormalUser() {
        String resp = ApiSupport.get("/user/base-user/find-list?identity=NORMAL",
                ApiSupport.adminToken());
        Matcher id = Pattern.compile("\"userId\"\\s*:\\s*(\\d+)").matcher(resp);
        Matcher name = Pattern.compile("\"userName\"\\s*:\\s*\"([^\"]+)\"").matcher(resp);
        assertTrue(id.find(), "用户列表为空，无法构造详情用例: "
                + (resp.length() > 200 ? resp.substring(0, 200) : resp));
        assertTrue(name.find(), "列表项缺 userName 字段: "
                + (resp.length() > 200 ? resp.substring(0, 200) : resp));
        return new String[]{id.group(1), name.group(1)};
    }

    @Test
    @DisplayName("SA-59 详情页正常渲染：三段分组齐全、回显真实数据、无 password 字样")
    @Description("管理员打开带 ?userId= 的详情页 → 基本资料/收货信息/时间信息三段卡片渲染，"
            + "用户名与列表同源；页面源码不得出现 password（凭证不进前端）")
    void SA59_detail_renders_real_data() {
        String[] user = firstNormalUser();
        injectToken(ApiSupport.adminToken(), "ADMIN");
        open("/user-detail.html?userId=" + user[0]);

        // 三段分组标题逐一出现（waitForTextContains 返回值必须被断言）
        assertTrue(waitForTextContains(By.cssSelector("#detailBody"), "基本资料", 8),
                "「基本资料」分组未渲染");
        assertTrue(waitForTextContains(By.cssSelector("#detailBody"), "收货信息", 5),
                "「收货信息」分组未渲染");
        assertTrue(waitForTextContains(By.cssSelector("#detailBody"), "时间信息", 5),
                "「时间信息」分组未渲染");

        // 回显真实数据：列表里第一个 NORMAL 用户的姓名必须出现在详情里（跨接口同源互证）
        assertTrue(waitForTextContains(By.cssSelector("#detailBody"), user[1], 5),
                "详情页未回显列表中的用户名: " + user[1]);

        // 状态徽章（active/disabled 二选一，二者都必须渲染出文字）
        assertTrue(driver.findElements(By.cssSelector("#detailBody .status-badge")).size() >= 1,
                "账号状态徽章未渲染");

        // 凭证泄露防线：详情响应含除密码外全字段，页面上绝不能出现 password 字样
        assertFalse(driver.getPageSource().toLowerCase().contains("password"),
                "页面源码出现 password 字样（凭证泄露到前端）");

        screenshot("SA-59-detail-render");
    }

    @Test
    @DisplayName("SA-60 列表入口闭环：点「详情」跳转且详情姓名与列表行一致")
    @Description("user-list.html 行内「详情」按钮 → user-detail.html?userId=… → "
            + "详情页回显的用户名必须等于点击那一行的姓名（抓跳转丢参/串号）")
    void SA60_entry_from_list_consistent() {
        injectToken(ApiSupport.adminToken(), "ADMIN");
        open("/user-list.html");

        WebElement firstRow = waitForElement(By.cssSelector("#userList tr"), 10);
        assertNotNull(firstRow, "用户列表未渲染任何行");
        // 表头顺序：人员id | 姓名 | 身份 | 状态 | 操作（user-list.html:144-148）
        String rowName = firstRow.findElements(By.tagName("td")).get(1).getText();
        assertFalse(rowName.isBlank(), "列表首行姓名为空，无法做一致性互证");

        // 行内第一个按钮即「详情」（buildActionCell 先 append 详情，后 append 停用）
        firstRow.findElements(By.cssSelector("button.btn-mini")).get(0).click();

        assertTrue(waitForUrlContains("user-detail.html?userId=", 8),
                "点击「详情」后未跳转到详情页，实际 URL=" + driver.getCurrentUrl());
        assertTrue(waitForTextContains(By.cssSelector("#detailBody"), rowName, 8),
                "详情页回显姓名与列表首行不一致（跳转丢参/串号）: 列表=" + rowName);

        screenshot("SA-60-entry-from-list");
    }

    @Test
    @DisplayName("SA-61 缺 userId 参数：显示明确错误态而非白屏")
    @Description("直接打开 user-detail.html（无 ?userId=）→ 页面自检先于请求，"
            + "显示「缺少 userId 参数，请从账号列表进入」且详情主体不渲染")
    void SA61_missing_param_shows_error_state() {
        injectToken(ApiSupport.adminToken(), "ADMIN");
        open("/user-detail.html");

        assertTrue(waitForTextContains(By.cssSelector("#detailLoading"), "缺少 userId 参数", 5),
                "缺参时未显示明确错误提示，实际提示=" + driver.findElement(By.cssSelector("#detailLoading")).getText());
        String cls = driver.findElement(By.cssSelector("#detailLoading")).getAttribute("class");
        assertNotNull(cls);
        assertTrue(cls.contains("err"), "错误态未标红（class 应含 err），实际=" + cls);
        assertFalse(driver.findElement(By.cssSelector("#detailBody")).isDisplayed(),
                "错误态下详情主体不应显示");

        screenshot("SA-61-missing-param");
    }

    @Test
    @DisplayName("SA-62 userId 不存在：显示业务错误文案（133 用户不存在）")
    @Description("查不存在的 userId=99999999 → HTTP 200 + code=133 → "
            + "页面显示「查询失败：用户不存在或已被删除」，不得白屏或假成功")
    void SA62_nonexistent_user_shows_business_error() {
        injectToken(ApiSupport.adminToken(), "ADMIN");
        open("/user-detail.html?userId=99999999");

        assertTrue(waitForTextContains(By.cssSelector("#detailLoading"), "用户不存在", 8),
                "未显示「用户不存在」业务错误，实际提示="
                        + driver.findElement(By.cssSelector("#detailLoading")).getText());
        assertTrue(driver.findElement(By.cssSelector("#detailLoading")).getAttribute("class").contains("err"),
                "业务错误态未标红");
        assertFalse(driver.findElement(By.cssSelector("#detailBody")).isDisplayed(),
                "业务失败时详情主体不应渲染（假成功防线）");

        screenshot("SA-62-user-not-exist");
    }

    @Test
    @DisplayName("SA-63 普通用户越权：403 被 auth-guard 识别、alert「权限不足」、停留本页不跳转")
    @Description("NORMAL token 打开详情页 → 拦截器真 HTTP 403 → auth-guard 写 body 痕迹 "
            + "data-auth-guard=403（waitForBodyAttribute 会先关 alert，否则原生弹窗阻塞 JS 读不到属性）")
    void SA63_normal_user_gets_403_guard() {
        clearLastAlertText();
        injectToken(ApiSupport.normalUserToken(), "NORMAL");
        open("/user-detail.html?userId=1");

        assertTrue(waitForBodyAttribute("data-auth-guard", "403", 8),
                "403 未被 auth-guard 识别（body 无 data-auth-guard=403 痕迹），"
                        + "实际=" + bodyAttribute("data-auth-guard"));
        // 403 语义 = 提示但不跳转（auth-guard.js:159-167）
        assertTrue(driver.getCurrentUrl().contains("user-detail.html"),
                "403 场景不应跳转登录页，实际 URL=" + driver.getCurrentUrl());
        assertNotNull(lastAlertText, "403 未弹提示");
        assertTrue(lastAlertText.contains("权限不足"),
                "403 弹窗文案不含「权限不足」，实际=" + lastAlertText);

        screenshot("SA-63-403-forbidden");
    }

    @Test
    @DisplayName("SA-64 未登录：401 弹「登录状态已失效」并跳管理端登录页")
    @Description("清空登录态打开详情页 → 拦截器真 401 → auth-guard alert 后 location.assign "
            + "到 /admin-login.html（详情页未覆盖 AUTH_GUARD_LOGIN_PAGE，走默认管理端登录页）。"
            + "注意：open() 收尾的 dismissAlertIfPresent 可能先吃掉弹窗（文案留在 lastAlertText），"
            + "两种捕获路径都算数，但弹窗文案与跳转都必须有据")
    void SA64_anonymous_redirects_to_admin_login() {
        clearLastAlertText();
        clearAuth();
        open("/user-detail.html?userId=1");

        // 跳转是主行为证据（alert 不 accept 时页面 JS 被阻塞、assign 不会执行，
        // 所以若弹窗此刻还在，先 accept 再等跳转；若已被 open() 吃掉，跳转已在路上）
        if (waitForAlertAndAccept(2)) {
            // 本轮捕获到弹窗，已被 accept
        }
        assertTrue(waitForUrlContains("admin-login.html", 8),
                "401 应跳转管理端登录页（默认 AUTH_GUARD_LOGIN_PAGE），实际 URL=" + driver.getCurrentUrl());

        // 弹窗证据：要么 open() 收尾时捕获（lastAlertText 留痕），要么刚刚 waitForAlertAndAccept 捕获
        if (lastAlertText == null) {
            waitForAlertAndAccept(2);
        }
        assertNotNull(lastAlertText, "401 全程未捕获到「登录状态已失效」弹窗（open 收尾与显式等待均无）");
        assertTrue(lastAlertText.contains("登录状态已失效"),
                "401 弹窗文案不含「登录状态已失效」，实际=" + lastAlertText);

        screenshot("SA-64-401-redirect");
    }
}
