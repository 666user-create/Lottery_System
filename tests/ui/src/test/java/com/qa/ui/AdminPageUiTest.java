package com.qa.ui;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 管理端页面 UI 自动化用例（SA-34 ~ SA-41）。
 *
 * <p>覆盖 {@code admin.html}（后台管理框架外壳）、{@code user-list.html}（账号列表）、
 * {@code admin-register.html}（管理端注册用户），以及两条 AuthGuard 相关的静态防退化检查
 * （SA-40 / SA-41）。
 *
 * <p>重要实测结论（2026-10-18 复核，10-16 前端改版后）：
 * <ul>
 *   <li>{@code admin.html} 自身**不做**登录校验，它只是左侧导航 + {@code #contentFrame} 外壳；
 *       iframe 默认加载 {@code activities-list.html}（{@code admin.html:238}）。</li>
 *   <li>子页拿到 401 后由 {@code AuthGuard} 把**父页面整体跳走**到登录页，
 *       因此访问管理端页面必须带管理员 token，否则外壳会被子页"劫持跳转"
 *       （SA-38 锁定 token 失效场景，SA-37 锁定普通用户外壳可渲染但接口被拒）。</li>
 *   <li>10-16 改版后侧边栏「人员」分组下只剩 {@code userList}（账号列表）+
 *       {@code accountCenter}（个人中心）两项，「注册用户」不再作为导航项存在，
 *       而是 {@code user-list.html} 页内的「+ 注册用户」按钮。</li>
 * </ul>
 */
@Feature("管理端页面与授权守卫")
public class AdminPageUiTest extends BaseUiTest {

    @Test
    @Story("SA-34 后台管理页（管理员登录态）侧边栏入口与内容 iframe 齐全")
    @DisplayName("SA-34 后台管理页（管理员登录态）侧边栏入口与内容 iframe 齐全")
    void adminPageLoadsWithAdminToken() {
        injectToken(ApiSupport.adminToken(), "ADMIN");
        open("/admin.html");
        assertNotNull(waitForElement(By.id("activitiesList"), 10), "活动列表入口应存在");
        assertFalse(driver.findElements(By.id("createActivity")).isEmpty(), "新建活动入口应存在");
        assertFalse(driver.findElements(By.id("userList")).isEmpty(), "账号列表入口应存在");
        // 2026-10-18 修复：原断言 By.id("register") 取自 10-12 之前的老侧边栏。
        // 10-16 改版后侧边栏「人员」分组下只剩「账号列表 + 个人中心」两项，
        // 「注册用户」已从导航项降级为 user-list.html 页内的「+ 注册用户」按钮
        // （该动作不适合与列表页并列为导航项，见 user-list.html:55 的设计说明），
        // 故此处改断言真实存在的 accountCenter（个人中心 → user-profile.html）。
        assertFalse(driver.findElements(By.id("accountCenter")).isEmpty(), "个人中心入口应存在");
        assertFalse(driver.findElements(By.id("contentFrame")).isEmpty(), "内容 iframe 应存在");
        assertFalse(driver.findElements(By.cssSelector(".sidebar")).isEmpty(), "侧边栏应存在");
        // 管理员登录态下不应被跳离管理页
        assertTrue(driver.getCurrentUrl().contains("admin.html"),
                "管理员登录态应停留在 admin.html，实际=" + driver.getCurrentUrl());
        screenshot("SA-34-admin-page");
    }

    @Test
    @Story("SA-35 人员列表页（管理员登录态）表格渲染真实数据行")
    @DisplayName("SA-35 人员列表页（管理员登录态）表格渲染真实数据行")
    void userListPageWithAdminToken() {
        injectToken(ApiSupport.adminToken(), "ADMIN");
        open("/user-list.html");
        // 2026-10-18 修复：10-16 改版把该页标题由「人员列表」改为「账号列表」
        // （user-list.html:5 <title>账号列表</title>、:139 <h2>账号列表</h2>）。
        // 断言同步改为「账号列表」；同时保留对设计说明中「注册用户是动作不是导航项」的隐含校验。
        assertTrue(bodyText().contains("账号列表"),
                "页面标题应为账号列表，实际页面文本前 200 字="
                        + bodyText().substring(0, Math.min(200, bodyText().length())));
        // 注册入口已内联到本页标题行右侧（user-list.html:139 onclick="goRegister()"）
        assertNotNull(waitForElement(By.xpath("//*[contains(text(),'注册用户')]"), 8),
                "账号列表页应保留「+ 注册用户」入口");
        assertFalse(driver.findElements(By.id("userList")).isEmpty(), "用户表格 tbody#userList 应存在");
        // 功能可用性断言：真实拉到数据行，而非仅"元素存在"（BUG-002 的教训）
        int rows = waitForElementCount(By.cssSelector("#userList tr"), 1, 10);
        assertTrue(rows >= 1, "管理员登录态下人员列表应至少渲染 1 行，实际=" + rows);
        screenshot("SA-35-user-list");
    }

    @Test
    @Story("SA-36 注册用户页（管理端 admin-register.html）表单元素齐全")
    @DisplayName("SA-36 注册用户页（管理端 admin-register.html）表单元素齐全")
    void adminRegisterPageElements() {
        open("/admin-register.html");
        assertTrue(bodyText().contains("注册"), "注册页应包含注册相关文案");
        assertFalse(driver.findElements(By.id("registerForm")).isEmpty(), "注册表单应存在");
        assertFalse(driver.findElements(By.id("name")).isEmpty(), "姓名输入框应存在");
        assertFalse(driver.findElements(By.id("mail")).isEmpty(), "邮箱输入框应存在");
        assertFalse(driver.findElements(By.id("phoneNumber")).isEmpty(), "手机号输入框应存在");
        assertFalse(driver.findElements(By.id("password")).isEmpty(), "密码输入框应存在");
        assertFalse(driver.findElements(By.id("toggleAdmin")).isEmpty(), "「切换为管理员注册」按钮应存在");
        screenshot("SA-36-admin-register-page");
    }

    @Test
    @Story("SA-37 普通用户打开 admin.html：外壳可渲染（真正的鉴权在管理端接口，见 SA-39）")
    @DisplayName("SA-37 普通用户打开 admin.html：外壳可渲染（真正的鉴权在管理端接口，见 SA-39）")
    void adminPageShellRendersForNormalUser() {
        injectToken(ApiSupport.normalUserToken(), "NORMAL");
        open("/admin.html");
        // 说明：admin.html 只是前端外壳，其 iframe 默认加载 activities-list.html
        // （/activity/find-list 非 adminOnly，普通用户可读）；
        // 真正的鉴权拦截发生在人员/奖品等 adminOnly 接口上，故此处只断言外壳与入口齐全。
        assertNotNull(waitForElement(By.id("activitiesList"), 10), "侧边栏入口应存在");
        assertFalse(driver.findElements(By.id("userList")).isEmpty(), "账号列表入口应存在");
        assertFalse(driver.findElements(By.id("contentFrame")).isEmpty(), "内容 iframe 应存在");
        screenshot("SA-37-admin-shell-normal-user");
    }

    /** 探测当前弹窗文案并关闭；无弹窗返回 null（不影响后续断言） */
    private String captureAlertText() {
        try {
            org.openqa.selenium.Alert alert = driver.switchTo().alert();
            String text = alert.getText();
            alert.accept();
            dismissAlertIfPresent();
            return text;
        } catch (org.openqa.selenium.NoAlertPresentException e) {
            return null;
        }
    }

    /**
     * 轮询「AuthGuard 痕迹 / 弹窗 / 跳转 / 页面文本」命中即返回。
     *
     * <p>为什么不能只看 alert（2026-09-16 实测）：
     * 授权失效分支在页面导航完成时就已弹出，而 {@link BaseUiTest#open(String)} 结尾会
     * {@code dismissAlertIfPresent()} 把它 accept 掉 —— 测试再去看就已经没有弹窗了。
     * 因此 AuthGuard 会在 body 上留 {@code data-auth-guard="401|403"} 痕迹，测试以它为主判据。
     */
    private String waitForAuthSignal(String[] urlKeywords, String[] textKeywords, int seconds) {
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            String captured = captureAlertText();
            if (captured != null) {
                return "alert:" + captured;
            }
            Object mark = ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                    "return document.body ? document.body.getAttribute('data-auth-guard') : null;");
            if (mark != null) {
                Object action = ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                        "return document.body.getAttribute('data-auth-guard-action');");
                return "guard:" + mark + (action == null ? "" : (" " + action));
            }
            String url = driver.getCurrentUrl();
            for (String kw : urlKeywords) {
                if (url.contains(kw)) {
                    return "url:" + url;
                }
            }
            String body = bodyText();
            for (String kw : textKeywords) {
                if (body.contains(kw)) {
                    return "text:" + kw;
                }
            }
            sleep(400);
        }
        return null;
    }

    @Test
    @Story("SA-38 Token 失效：请求管理端页面应提示并跳转到登录页（授权守卫修复验证）")
    @DisplayName("SA-38 Token 失效：请求管理端页面应提示并跳转到登录页（授权守卫修复验证）")
    void expiredTokenRedirectsToLogin() {
        // 注入一个格式合法但服务端不认的 token（模拟「登录已过期/被踢」）
        injectToken("invalid.token.for.auth.guard.regression", "ADMIN");
        open("/activities-list.html");
        String signal = waitForAuthSignal(
                new String[]{"admin-login", "user-login"}, new String[]{"登录状态已失效"}, 15);
        assertNotNull(signal,
                "Token 失效应被提示并跳转登录页（修复前：err.status==401 永不触发，页面停在原地）。"
                        + "实际 URL=" + driver.getCurrentUrl());
        screenshot("SA-38-expired-token-redirect");
    }

    @Test
    @Story("SA-39 权限不足：普通用户查管理端数据应提示「权限不足」且不跳转")
    @DisplayName("SA-39 权限不足：普通用户查管理端数据应提示「权限不足」且不跳转")
    void insufficientPermissionShowsAlert() {
        injectToken(ApiSupport.normalUserToken(), "NORMAL");
        open("/user-list.html");
        String signal = waitForAuthSignal(
                new String[]{"admin-login"}, new String[]{"权限不足"}, 12);
        assertNotNull(signal, "非管理员查询人员列表应给出「权限不足」提示，实际 URL="
                + driver.getCurrentUrl() + " body=" + bodyText());
        // 403 不应把人踢到登录页（用户是登录状态，只是权限不够）
        assertFalse(driver.getCurrentUrl().contains("admin-login"),
                "403 不应跳转登录页，实际 URL=" + driver.getCurrentUrl());
        screenshot("SA-39-insufficient-permission");
    }

    @Test
    @Story("SA-40 静态检查：页面不得再把 401 判断写在 error 回调里（授权守卫防退化）")
    @DisplayName("SA-40 静态检查：页面不得再把 401 判断写在 error 回调里（授权守卫防退化）")
    void noDeadAuthBranchInPages() throws Exception {
        java.nio.file.Path staticDir = projectRoot().resolve("src/main/resources/static");
        assertTrue(java.nio.file.Files.isDirectory(staticDir),
                "未找到静态资源目录: " + staticDir.toAbsolutePath());
        java.util.List<String> offenders = new java.util.ArrayList<>();
        int checked = 0;
        try (var stream = java.nio.file.Files.list(staticDir)) {
            for (java.nio.file.Path p : stream.filter(x -> x.toString().endsWith(".html")).toList()) {
                checked++;
                for (String line : java.nio.file.Files.readAllLines(p)) {
                    String t = line.trim();
                    // 跳过注释行：修复说明里会引用旧写法，不算违规代码
                    if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")
                            || t.startsWith("<!--")) {
                        continue;
                    }
                    if (t.matches(".*err\\.status\\s*==\\s*401.*")) {
                        offenders.add(p.getFileName() + ": " + t);
                    }
                }
            }
        }
        assertTrue(checked >= 10, "应扫描到至少 10 个页面，实际 " + checked);
        assertTrue(offenders.isEmpty(),
                "以下位置仍把 401 判断写在 error 回调里（应改为 AuthGuard.handle）: " + offenders);
    }

    @Test
    @Story("SA-41 静态检查：接入 AuthGuard 的页面必须同时守 success 与 error 两条分支")
    @DisplayName("SA-41 静态检查：接入 AuthGuard 的页面必须同时守 success 与 error 两条分支")
    void authGuardWiredOnBothBranches() throws Exception {
        java.nio.file.Path staticDir = projectRoot().resolve("src/main/resources/static");
        // 这些页面会调管理端接口（成功可能带 body.code，拦截器拒绝则是 HTTP 401/403），两条分支都要守
        String[] pages = {"user-list.html", "activities-list.html", "prizes-list.html",
                "create-prizes.html", "create-activity.html", "draw.html", "admin-handover.html"};
        java.util.List<String> problems = new java.util.ArrayList<>();
        for (String page : pages) {
            java.nio.file.Path p = staticDir.resolve(page);
            if (!java.nio.file.Files.exists(p)) {
                problems.add(page + "(文件不存在)");
                continue;
            }
            String html = java.nio.file.Files.readString(p);
            if (!html.contains("auth-guard.js")) {
                problems.add(page + "(未引入 auth-guard.js)");
            }
            if (!html.contains("AuthGuard.handle(result")
                    && !html.contains("AuthGuard.handle(res")) {
                problems.add(page + "(success 分支未守)");
            }
            if (!html.contains("AuthGuard.handle(err")
                    && !html.contains("AuthGuard.handle(res,")
                    && !html.contains("AuthGuard.handle(res ,")) {
                problems.add(page + "(error 分支未守)");
            }
        }
        assertTrue(problems.isEmpty(), "AuthGuard 接入不完整: " + problems);
    }
}
