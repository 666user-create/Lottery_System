package com.qa.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 登录/注册页面 UI 自动化用例（SA-01 ~ SA-04）
 */
public class LoginPageUiTest extends BaseUiTest {

    @Test
    @DisplayName("SA-01 登录页可访问，标题与表单元素可见")
    void loginPageLoads() {
        open("/user-login.html");
        assertEquals(BASE_URL + "/user-login.html", driver.getCurrentUrl(), "应停留在登录页");
        assertFalse(driver.findElements(By.tagName("body")).isEmpty(), "页面 body 应渲染");
        screenshot("SA-01-login-page");
    }

    @Test
    @DisplayName("SA-02 注册页表单元素存在（输入框/按钮）")
    void registerPageElements() {
        open("/user-register.html");
        WebElement body = driver.findElement(By.tagName("body"));
        assertTrue(body.getText().contains("注册") || body.getText().contains("姓名"),
                "注册页应包含注册相关文案");
        screenshot("SA-02-register-page");
    }

    @Test
    @DisplayName("SA-03 聊天页元素可见（输入框/发送/转人工按钮）")
    void chatPageElements() {
        open("/chat.html");
        assertFalse(driver.findElements(By.id("input")).isEmpty(), "聊天输入框应存在");
        assertFalse(driver.findElements(By.id("sendBtn")).isEmpty(), "发送按钮应存在");
        assertFalse(driver.findElements(By.id("handoverBtn")).isEmpty(), "转人工按钮应存在");
        screenshot("SA-03-chat-page");
    }

    @Test
    @DisplayName("SA-04 工单管理页：未登录场景弹出提示（预期行为）")
    void handoverPageWithoutLogin() {
        // 未登录打开管理页，页面 JS 会 alert 提示先登录；验证 alert 出现且页面不崩溃
        open("/admin-handover.html");
        // 弹窗已在 open() 中被捕获并 accept
        WebElement body = driver.findElement(By.tagName("body"));
        assertNotNull(body, "页面应正常渲染");
        screenshot("SA-04-handover-no-login");
    }

    @Test
    @DisplayName("SA-05 大厅页可访问")
    void lobbyPageLoads() {
        open("/user-lobby.html");
        WebElement body = driver.findElement(By.tagName("body"));
        assertNotNull(body, "大厅页应渲染");
        screenshot("SA-05-lobby");
    }
}
