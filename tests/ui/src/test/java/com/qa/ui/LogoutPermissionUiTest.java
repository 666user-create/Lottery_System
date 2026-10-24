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
 * UI 用例 · 注销与权限入口（SA-32 ~ SA-33）
 *
 * 断言深度：
 *   - 注销：断言 localStorage 的 token **被清空** + 跳回登录页（抓"假退出"）
 *   - 权限入口：普通用户进入管理页时**不能正常使用管理功能**（抓前端越权入口）
 */
@Feature("注销与权限入口")
public class LogoutPermissionUiTest extends BaseUiTest {

    private static String userToken;

    @BeforeAll
    static void prepareToken() {
        userToken = ApiSupport.normalUserToken();
    }

    @Test
    @Story("SA-32 退出登录：token 被清空并跳转登录页")
    @DisplayName("SA-32 退出登录：token 被清空并跳转登录页")
    void logoutClearsToken() {
        injectToken(userToken, "NORMAL");
        open("/user-lobby.html");
        sleep(2500);
        dismissAlertIfPresent();
        screenshot("SA-32-lobby-before-logout");

        // 触发退出（按钮 onclick=logout() 或直接调用）
        boolean clicked = clickIfPresent(By.xpath("//button[contains(text(),'退出')]"))
                || clickIfPresent(By.cssSelector(".btn-outline-danger"));
        if (!clicked) {
            ((JavascriptExecutor) driver).executeScript("if (typeof logout === 'function') logout();");
        }
        sleep(2000);
        dismissAlertIfPresent();
        screenshot("SA-32-after-logout");

        Object token = ((JavascriptExecutor) driver).executeScript("return localStorage.getItem('token');");
        assertNull(token, "退出登录后 localStorage 中的 token 应被清空，实际=" + token);
        assertTrue(driver.getCurrentUrl().contains("login"),
                "退出登录后应跳转登录页，实际 URL=" + driver.getCurrentUrl());
    }

    @Test
    @Story("SA-33 权限入口：普通用户访问管理页无有效管理能力")
    @DisplayName("SA-33 权限入口：普通用户访问管理页无有效管理能力")
    void normalUserHasNoAdminAccess() {
        injectToken(userToken, "NORMAL");
        open("/admin-handover.html");

        // ---- 断言：普通用户访问管理页必须被 403 拦截，且**不跳转** ----
        //
        // 依据（前后端各一段）：
        //   后端 LoginInterceptor.java:93 —— 把 "/api/handover/list" 列入 adminOnly 名单；
        //                                    :113-118 判定非 ADMIN 后返回 FORBIDDEN；
        //                                    :137-140 writeJson 把 HTTP 状态与 body.code 同时置为 403。
        //   前端 admin-handover.html:125  —— 把响应交给 AuthGuard.handle(res, '加载工单列表')；
        //        auth-guard.js:159-167     —— 对 403 的处理是 mark(403, action, message)：
        //                                    :96-98 在 <body> 上写
        //                                      data-auth-guard="403"
        //                                      data-auth-guard-action="「加载工单列表」"
        //                                      data-auth-guard-message=后端 message
        //                                    然后 :166 alert('权限不足「加载工单列表」：需要管理员权限')。
        //        auth-guard.js:141-143     —— 明确 403 **不跳转**（用户身份有效，只是权限不够）。
        //
        // 【修复前】boolean blocked = rows == 0 || text.contains("失败") || text.contains("无权限")
        //                          || text.contains("登录") || url.contains("login");
        //   问题：5 通道大 OR，"表格 0 行"是最致命的通道——页面白屏、接口还没返回、
        //   前端 JS 直接抛错，表格行数同样为 0。于是"越权被正确拦截"和"页面根本没渲染出来"
        //   被当成了同一件事，用例在任何一种情况下都绿；且"登录"二字在管理端页面的
        //   任何提示/文案里都可能出现，等于又开了一个恒真通道。
        //
        // 【修复后】只断言 auth-guard 留下的**唯一可信痕迹**（它本来就是为自动化留的，见
        //   auth-guard.js:91 注释「弹窗会被 accept 掉，DOM 痕迹不会」），三个断言互相独立：
        //   ① data-auth-guard == "403"（401 是登录失效，403 才是越权，不能混）；
        //   ② data-auth-guard-action 含「加载工单列表」（证明拦截确实发生在管理接口那一步）；
        //   ③ URL 仍在 admin-handover.html（403 不跳转；若跳走了说明行为已退化）。
        boolean denied = waitForBodyAttribute("data-auth-guard", "403", 12);
        screenshot("SA-33-normal-user-admin-page");

        String guardCode = bodyAttribute("data-auth-guard");
        String guardAction = bodyAttribute("data-auth-guard-action");
        assertTrue(denied,
                "普通用户访问 /admin-handover.html 时管理接口应返回 403，并由 AuthGuard 在 body 上留痕"
                        + "（auth-guard.js:159-167），实际 data-auth-guard=" + guardCode
                        + "，URL=" + driver.getCurrentUrl());
        assertNotNull(guardAction, "应记录被拒的动作（auth-guard.js:97 data-auth-guard-action），实际为 null");
        assertTrue(guardAction.contains("加载工单列表"),
                "被拒的请求应是「加载工单列表」（admin-handover.html:125），实际记录=" + guardAction);
        assertTrue(driver.getCurrentUrl().contains("admin-handover"),
                "403 不应触发跳转（auth-guard.js:141-143），实际 URL=" + driver.getCurrentUrl());
    }
}
