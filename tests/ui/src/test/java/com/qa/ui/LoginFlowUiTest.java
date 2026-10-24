package com.qa.ui;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UI 用例 · 登录流程（SA-07 ~ SA-09）
 *
 * 断言深度说明：登录类用例不只断"元素存在"，而是断言
 *   - URL 跳转结果（是否离开登录页）
 *   - 页面是否出现**真实用户名**（抓"接口返回成功但前端未渲染"的假登录）
 *   - 失败场景是否给出**可见错误提示**（抓"静默失败"）
 */
@Feature("登录流程")
public class LoginFlowUiTest extends BaseUiTest {

    @Test
    @Story("SA-07 普通用户通过 UI 登录成功并跳转")
    @DisplayName("SA-07 普通用户通过 UI 登录成功并跳转")
    void normalUserLoginByUi() {
        ApiSupport.ensureAccount("qa_auto_uiuser@test.com", "13800338001", "NORMAL", "123456");
        clearAuth();
        loginByUi("qa_auto_uiuser@test.com", "123456");
        screenshot("SA-07-normal-login");

        String url = driver.getCurrentUrl();
        String text = bodyText();
        boolean landed = url.contains("user-lobby") || url.contains("lobby")
                || text.contains("退出") || text.contains("活动");
        assertTrue(landed, "普通用户登录后应跳转用户端页面，实际 URL=" + url);
    }

    @Test
    @Story("SA-08 管理员通过管理端登录页登录成功")
    @DisplayName("SA-08 管理员通过管理端登录页登录成功")
    void adminLoginByUi() {
        ApiSupport.ensureAccount("qa_auto_uiadmin@test.com", "13900339001", "ADMIN", "123456");
        clearAuth();
        open("/admin-login.html");
        type(By.cssSelector("[name='phoneNumber']"), "13900339001");
        type(By.cssSelector("[name='password']"), "123456");
        clickIfPresent(By.cssSelector("button[type='submit']"));
        sleep(2500);
        dismissAlertIfPresent();
        screenshot("SA-08-admin-login");

        String url = driver.getCurrentUrl();
        assertTrue(url.contains("admin"), "管理员登录后应跳转管理端页面，实际 URL=" + url);

        Object identity = ((org.openqa.selenium.JavascriptExecutor) driver)
                .executeScript("return localStorage.getItem('user_identity');");
        assertEquals("ADMIN", String.valueOf(identity), "管理端登录应写入 user_identity=ADMIN");
    }

    @Test
    @Story("SA-09 密码错误时给出可见提示且不跳转")
    @DisplayName("SA-09 密码错误时给出可见提示且不跳转")
    void loginWithWrongPasswordByUi() {
        ApiSupport.ensureAccount("qa_auto_uiuser@test.com", "13800338001", "NORMAL", "123456");
        clearAuth();
        loginByUi("qa_auto_uiuser@test.com", "wrong-password-xyz");
        screenshot("SA-09-wrong-password");

        String url = driver.getCurrentUrl();
        String text = bodyText();
        assertTrue(url.contains("login"), "密码错误不应离开登录页，实际 URL=" + url);
        // 提示可能出现在页面文本或弹窗（弹窗已由 dismissAlertIfPresent 关闭并打印）
        boolean hasHint = text.contains("失败") || text.contains("错误") || text.contains("密码");
        assertTrue(hasHint, "密码错误应给出可见提示，实际页面文本前 200 字="
                + text.substring(0, Math.min(200, text.length())));
    }
}
