package com.qa.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用户注册-登录端到端流程 UI 自动化用例（SA-06）
 * 完整走通：注册页 → 提交表单 → 登录页 → 提交登录（真实后端）
 */
public class RegisterLoginFlowUiTest extends BaseUiTest {

    @Test
    @DisplayName("SA-06 注册→登录 端到端旅程（真实后端）")
    void registerThenLogin() {
        int randNum = java.util.concurrent.ThreadLocalRandom.current().nextInt(10000000, 99999999);
        String phone = "137" + randNum;
        String mail = "qa_ui_" + randNum + "@test.com";

        // 1. 打开注册页并填写
        open("/user-register.html");
        screenshot("SA-06-1-register-form");

        fillFieldIfPresent("name", "UI自动化用户");
        fillFieldIfPresent("mail", mail);
        fillFieldIfPresent("phoneNumber", phone);
        fillFieldIfPresent("password", "123456");

        // 提交表单（页面用 jQuery Validate，提交按钮 type=submit）
        submitFormIfPresent();

        // 2. 打开登录页
        open("/user-login.html");
        screenshot("SA-06-2-login-form");

        // 3. 填写登录信息（登录页字段：phoneNumber=手机号或邮箱，password=密码）
        fillFieldIfPresent("phoneNumber", mail);
        fillFieldIfPresent("password", "123456");
        submitFormIfPresent();
        screenshot("SA-06-3-after-login");

        // 4. 验证：登录后应跳转或出现欢迎信息（不同页面实现不同，做宽松断言）
        String pageText = driver.findElement(By.tagName("body")).getText();
        boolean landed = driver.getCurrentUrl().contains("lobby")
                || pageText.contains("欢迎")
                || pageText.contains("大厅")
                || pageText.contains("退出");
        assertTrue(landed, "登录后应进入用户端页面，当前 URL=" + driver.getCurrentUrl());
    }

    private void fillFieldIfPresent(String name, String value) {
        try {
            WebElement el = driver.findElement(By.cssSelector("[name='" + name + "']"));
            el.clear();
            el.sendKeys(value);
        } catch (Exception ignored) {
            System.out.println("[跳过字段] " + name + " 不存在");
        }
    }

    private void submitFormIfPresent() {
        try {
            WebElement submit = driver.findElement(By.cssSelector("button[type='submit']"));
            submit.click();
            // 等待页面跳转/请求完成，并处理可能弹出的 alert（注册成功/失败提示）
            try {
                Thread.sleep(2500);
            } catch (InterruptedException ignored) {
            }
            dismissAlertIfPresent();
        } catch (Exception ignored) {
            System.out.println("[跳过提交] 未找到提交按钮");
        }
    }
}
