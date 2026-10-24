package com.qa.ui;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用户注册-登录端到端流程 UI 自动化用例（SA-06）
 * 完整走通：注册页 → 提交表单 → 登录页 → 提交登录（真实后端）
 *
 * <p>2026-10-18 空断言审计修复，本用例原来有三个"永远通过"的设计：
 * <ol>
 *   <li>两个私有 helper 都用 {@code catch (Exception ignored)} 包住 {@code findElement}：
 *       一旦注册页字段改名、或提交按钮消失，异常被静默吞掉，用例继续往下跑，
 *       最后失败在现场（登录页）而不是根因（注册页），甚至可能"通过"；</li>
 *   <li>注册是否成功从未断言——注册失败也会继续走登录，若登录碰巧因其它原因成功
 *       （例如该账号上一轮已注册过），用例就绿了；</li>
 *   <li>最终判定 `jumped || url.contains("lobby") || pageText.contains("欢迎"|"大厅"|"退出")`
 *       是多通道 OR，而「欢迎」「大厅」「退出」这几个词在本次注册用的邮箱
 *       （qa_auto_ui_<8位数字>@test.com）和登录页上根本不存在，属于凑通道。</li>
 * </ol>
 * 现在：前置失败立即失败（不吞异常）、注册结果显式断言、最终只认「进入 user-lobby + token 非空」。
 */
@Feature("注册 → 登录端到端")
public class RegisterLoginFlowUiTest extends BaseUiTest {

    @Test
    @Story("SA-06 注册→登录 端到端旅程（真实后端）")
    @DisplayName("SA-06 注册→登录 端到端旅程（真实后端）")
    void registerThenLogin() {
        int randNum = java.util.concurrent.ThreadLocalRandom.current().nextInt(10000000, 99999999);
        String phone = "137" + randNum;
        String mail = "qa_auto_ui_" + randNum + "@test.com";
        String pwd = "123456";

        // ---------- 1. 打开注册页并填写 ----------
        clearAuth();
        open("/user-register.html");
        screenshot("SA-06-1-register-form");

        // 字段 name 依据 user-register.html:22/26/30/34；
        // 找不到字段直接失败在**注册页**，而不是等到最后在登录页报一个看不懂的失败。
        fillField("name", "UI自动化用户", "user-register.html:22");
        fillField("mail", mail, "user-register.html:26");
        fillField("phoneNumber", phone, "user-register.html:30");
        fillField("password", pwd, "user-register.html:34");

        // 提交表单（页面用 jQuery Validate，提交按钮 type=submit，user-register.html:36）
        clearLastAlertText();
        submitForm();

        // ---------- 2. 显式断言「注册真的成功了」 ----------
        // 依据 user-register.html:75-87：成功 alert('注册成功！请登录') 后 :81 跳登录页；
        // 失败 alert('注册失败：' + …)。之前这一步完全没断言，注册失败也继续往下走。
        boolean registered = waitForAlertAndAccept(8);
        assertTrue(registered, "提交注册后应弹出结果提示（user-register.html:75-87 成功/失败都会 alert）");
        assertEquals("注册成功！请登录", lastAlertText,
                "注册未成功（弹窗文案不符），后续登录断言会失去意义。实际弹窗=" + lastAlertText);
        assertTrue(waitForUrlContains("user-login", 10),
                "注册成功后应跳转登录页（user-register.html:81），实际 URL=" + driver.getCurrentUrl());

        // ---------- 3. 用刚注册的账号走 UI 登录 ----------
        open("/user-login.html");
        screenshot("SA-06-2-login-form");
        fillField("phoneNumber", mail, "user-login.html:46（手机号或邮箱）");
        fillField("password", pwd, "user-login.html:50");
        clearLastAlertText();
        submitForm();

        // ---------- 4. 真断言：进入 user-lobby 且拿到 token ----------
        // 依据 user-lobby.html:328-329 —— 大厅页会校验 token，没有 token 直接弹回登录页；
        // 依据登录接口成功后会写入 localStorage.token（ApiSupport.login 亦以「长度 >= 50」判定有效 JWT）。
        // 旧写法 `!url.contains("user-login")` 会让"停在任何非登录页"都通过，已删除。
        boolean landed = waitForUrlContains("user-lobby", 15);
        Object token = ((JavascriptExecutor) driver).executeScript("return localStorage.getItem('token');");
        screenshot("SA-06-3-after-login");

        assertTrue(landed,
                "新注册账号登录后应进入 user-lobby，实际 URL=" + driver.getCurrentUrl()
                        + "，登录页提示=" + lastAlertText);
        assertNotNull(token, "登录成功后 localStorage 应写入 token；实际为 null，登录弹窗=" + lastAlertText);
        assertTrue(String.valueOf(token).length() >= 50,
                "写入的 token 应为有效 JWT（长度 >= 50），实际长度=" + String.valueOf(token).length());
    }

    /**
     * 填写一个表单字段；**字段不存在时直接失败**，不再静默跳过。
     *
     * <p>【修复前】catch (Exception ignored) + System.out.println("[跳过字段] …")：
     * 字段缺失不会让用例失败，只会打一行日志，然后带着未填的表单继续提交，
     * 最终在完全无关的地方失败（或假通过）。前置条件缺失必须立即失败。
     *
     * @param name       input 的 name 属性
     * @param value      要填的值
     * @param sourceRef  该字段的来源说明（page:line），失败时一起打印，便于定位页面是否改版
     */
    private void fillField(String name, String value, String sourceRef) {
        WebElement el = waitForElement(By.cssSelector("[name='" + name + "']"), 8);
        assertNotNull(el, "页面应存在 name='" + name + "' 的输入框（依据 " + sourceRef
                + "），实际 URL=" + driver.getCurrentUrl()
                + "，页面文本前 200 字=" + bodyText().substring(0, Math.min(200, bodyText().length())));
        el.clear();
        el.sendKeys(value);
    }

    /** 点击表单提交按钮；按钮不存在时直接失败（同上，不吞异常）。 */
    private void submitForm() {
        WebElement submit = waitForElement(By.cssSelector("button[type='submit']"), 8);
        assertNotNull(submit, "页面应有提交按钮 button[type='submit']，实际 URL=" + driver.getCurrentUrl());
        submit.click();
    }
}
