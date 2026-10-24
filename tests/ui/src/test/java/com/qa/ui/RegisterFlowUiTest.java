package com.qa.ui;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UI 用例 · 注册流程（SA-10 ~ SA-12）
 *
 * 断言深度：注册不止断"提交成功提示"，还断言**新账号能真实登录**（抓"注册接口返回成功但数据未落库"）。
 * 测试数据统一 qa_auto_ 前缀，可由 tools/db_cleanup.py 白名单清理。
 *
 * <p>2026-10-18 空断言审计修复：本类原本三条用例都存在"永远通过"的断言，已逐条收紧，
 * 详见各用例内注释。
 */
@Feature("注册流程")
public class RegisterFlowUiTest extends BaseUiTest {

    private String rand() {
        return UUID.randomUUID().toString().replaceAll("[^0-9]", "").substring(0, 6);
    }

    @Test
    @Story("SA-10 普通用户注册成功并跳转登录页")
    @DisplayName("SA-10 普通用户注册成功并跳转登录页")
    void registerNormalUserByUi() {
        String mail = "qa_auto_uireg_" + rand() + "@test.com";
        String phone = "137" + (10000000 + (int) (Math.random() * 80000000));
        clearAuth();
        open("/user-register.html");
        type(By.cssSelector("[name='name']"), "QA-UI注册");
        type(By.cssSelector("[name='mail']"), mail);
        type(By.cssSelector("[name='phoneNumber']"), phone);
        type(By.cssSelector("[name='password']"), "123456");

        // 清掉上一个用例可能留下的弹窗文案，避免断言读到别人的弹窗
        clearLastAlertText();
        clickIfPresent(By.cssSelector("button[type='submit']"));

        // ---- 断言 1：必须弹出结果提示 ----
        // 依据 user-register.html:75-87：success 分支 code==200 走 :80 alert('注册成功！请登录')、
        // code!=200 走 :77 alert('注册失败：' + message)；error 分支走 :86 alert('注册失败：' + msg)。
        // 也就是说**无论成功失败都会弹窗**，所以"没弹窗"本身就是异常（请求没发出去 / JS 报错）。
        boolean alerted = waitForAlertAndAccept(8);
        screenshot("SA-10-register");

        // ---- 断言 2：弹窗文案必须是成功的原文 ----
        // 【修复前】这里是 assertTrue(jumped || bodyText().contains("成功"), ...)。
        // 问题：bodyText() 是整页文本，而注册成功后会跳到登录页，登录页上的
        // 「注册」入口文案里就含"注册"二字，加上跳转本身的不确定性，断言实际上恒真。
        // 【修复后】直接断言弹窗原文，成功与失败分支的文案完全不同（:77/:80），
        // 一旦后端返回 code!=200（如邮箱重复），文案变成「注册失败：…」，本断言立即失败。
        assertTrue(alerted, "提交注册后应弹出结果提示（成功或失败都会 alert，见 user-register.html:75-87），"
                + "实际未捕获到任何弹窗，当前 URL=" + driver.getCurrentUrl());
        assertEquals("注册成功！请登录", lastAlertText,
                "注册成功弹窗文案应为 user-register.html:80 的原文，实际=" + lastAlertText);

        // ---- 断言 3：成功后必须跳转登录页 ----
        // 依据 user-register.html:81 `window.location.href = 'user-login.html'`
        assertTrue(waitForUrlContains("user-login", 10),
                "注册成功后应跳转到登录页（user-register.html:81），实际 URL=" + driver.getCurrentUrl());
    }

    @Test
    @Story("SA-11 注册表单必填校验（空表单提交被拦截）")
    @DisplayName("SA-11 注册表单必填校验（空表单提交被拦截）")
    void registerRequiredFieldValidation() {
        clearAuth();
        open("/user-register.html");
        clickIfPresent(By.cssSelector("button[type='submit']"));
        sleep(1500);
        dismissAlertIfPresent();
        screenshot("SA-11-register-validation");

        String url = driver.getCurrentUrl();
        assertTrue(url.contains("register"), "必填校验未通过时不应离开注册页，实际 URL=" + url);

        // 【修复前】断言 bodyText() 含「请输入」/「不能为空」/「必填」三者之一。
        // 问题：这是多通道 OR，且 bodyText() 是整页可见文本——注册页的 placeholder 里
        // 就写着「请输入密码（至少6位）」（user-register.html:34），只要页面渲染出来就命中。
        // 【修复后】断言 jQuery Validate 真正生成的错误标签 label.error：
        //   - 校验没触发 → label.error 数量为 0 → 失败；
        //   - 校验触发 → user-register.html:55-60 会为 name/mail/phoneNumber 生成
        //     「请输入您的姓名」「请输入有效的邮箱地址」「请输入您的手机号」三条提示。
        // 空表单提交时姓名/邮箱/手机号三个必填项都空，三条提示应全部出现。
        int errorLabels = waitForElementCount(By.cssSelector("label.error"), 1, 8);
        assertTrue(errorLabels >= 1,
                "空表单提交应触发必填校验并渲染 label.error（user-register.html:55-60），实际数量=" + errorLabels);

        String errText = String.join(" | ", driver.findElements(By.cssSelector("label.error"))
                .stream().map(org.openqa.selenium.WebElement::getText).toList());
        assertTrue(errText.contains("请输入"),
                "校验提示应包含「请输入」类文案（user-register.html:55-60），实际提示=" + errText);
    }

    @Test
    @Story("SA-12 注册的新账号可通过 UI 真实登录（抓假注册）")
    @DisplayName("SA-12 注册的新账号可通过 UI 真实登录（抓假注册）")
    void registeredAccountCanLogin() {
        String mail = "qa_auto_uireg2_" + rand() + "@test.com";
        String phone = "137" + (10000000 + (int) (Math.random() * 80000000));
        String pwd = "123456";

        clearAuth();
        open("/user-register.html");
        type(By.cssSelector("[name='name']"), "QA-UI注册校验");
        type(By.cssSelector("[name='mail']"), mail);
        type(By.cssSelector("[name='phoneNumber']"), phone);
        type(By.cssSelector("[name='password']"), pwd);
        clearLastAlertText();
        clickIfPresent(By.cssSelector("button[type='submit']"));
        boolean alerted = waitForAlertAndAccept(8);

        // 前置条件显式化：注册没成功的话，后面的登录失败就失去意义（会让失败原因指向登录而不是注册）
        assertTrue(alerted, "注册提交后应弹出结果提示，实际未捕获弹窗");
        assertEquals("注册成功！请登录", lastAlertText,
                "注册未成功（弹窗文案不符），后续登录断言将失去意义。实际文案=" + lastAlertText);

        // 用刚注册的账号走 UI 登录，验证数据真实落库
        loginByUi(mail, pwd);

        // 【修复前】boolean loggedIn = waitForUrlContains("lobby", 12)
        //                       || !driver.getCurrentUrl().contains("user-login");
        // 问题：第二个分支让"只要不在登录页"就算登录成功——停在任何其它页面
        // （404、报错页、被跳到首页）都通过；而且若登录页 URL 恰好不含 "user-login"
        // （例如被重定向到别的登录页），第一个分支也会让断言通过。
        // 【修复后】同时要求两个独立事实：
        //   ① URL 确实进入 user-lobby（登录成功后唯一跳转目标）；
        //   ② localStorage 里存在登录接口写入的 token（user-login.html 登录成功后会写 token）。
        // 数据未落库时后端返回 code!=200，不会写 token、不会跳转 → 两个断言都会失败。
        boolean landed = waitForUrlContains("user-lobby", 12);
        Object token = ((JavascriptExecutor) driver).executeScript("return localStorage.getItem('token');");
        screenshot("SA-12-registered-login");

        assertTrue(landed, "新注册账号登录后应进入 user-lobby，实际 URL=" + driver.getCurrentUrl());
        assertNotNull(token, "登录成功后 localStorage 应写入 token，实际为 null（URL=" + driver.getCurrentUrl() + "）");
        assertTrue(String.valueOf(token).length() >= 50,
                "写入的 token 应为有效 JWT（长度应 >= 50，与 ApiSupport.login 的判定一致），实际长度="
                        + String.valueOf(token).length());
    }
}
