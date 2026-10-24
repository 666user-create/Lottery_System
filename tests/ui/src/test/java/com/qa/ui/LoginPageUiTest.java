package com.qa.ui;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 登录/注册页面 UI 自动化用例（SA-01 ~ SA-05）
 *
 * <p>断言深度（2026-10-18 空断言审计后重写）：
 * 本类原有 5 条用例里有 3 条属于"永远通过"的写法：
 * <ul>
 *   <li>SA-01 {@code assertFalse(driver.findElements(By.tagName("body")).isEmpty())} —— 装饰性存在断言，
 *       任何页面（包括报错页、白屏页）都有 body，等价于什么都没断言；</li>
 *   <li>SA-04 / SA-05 {@code assertNotNull(driver.findElement(By.tagName("body")))} ——
 *       {@code findElement} 在元素不存在时会**抛异常**，根本走不到 assertNotNull，
 *       所以这两条断言永远不可能"失败"，用例实际只证明"页面能打开"。</li>
 * </ul>
 * 现在改为断言各页面**独有的结构与文案**，页面结构一旦被改坏、或页面被错误重定向都会失败。
 */
@Feature("页面可访问性")
public class LoginPageUiTest extends BaseUiTest {

    @Test
    @Story("SA-01 登录页可访问，标题与表单元素可见")
    @DisplayName("SA-01 登录页可访问，标题与表单元素可见")
    void loginPageLoads() {
        clearAuth();
        open("/user-login.html");
        assertEquals(BASE_URL + "/user-login.html", driver.getCurrentUrl(), "应停留在登录页");

        // 依据 user-login.html:37 —— <h3>抽奖系统 - 用户登录</h3>
        var title = waitForElement(By.xpath("//h3[contains(text(),'用户登录')]"), 8);
        assertNotNull(title, "登录页应渲染标题「抽奖系统 - 用户登录」（user-login.html:37）");
        assertTrue(title.getText().contains("抽奖系统"),
                "标题应包含系统名（user-login.html:37），实际=" + title.getText());

        // 依据 user-login.html:43-54 —— 密码登录表单 #loginForm，含手机号/邮箱、密码两个必填项
        var loginForm = waitForElement(By.id("loginForm"), 8);
        assertNotNull(loginForm, "登录页应有密码登录表单 #loginForm（user-login.html:43）");
        assertTrue(loginForm.isDisplayed(), "密码登录表单应默认为可见（user-login.html:39-43）");
        assertNotNull(waitForElement(By.cssSelector("#loginForm [name='phoneNumber']"), 5),
                "应有「手机号或邮箱」输入框（user-login.html:46）");
        assertNotNull(waitForElement(By.cssSelector("#loginForm [name='password']"), 5),
                "应有「密码」输入框（user-login.html:50）");

        // 依据 user-login.html:52 —— 提交按钮文案为「登录」
        var submit = waitForElement(By.cssSelector("#loginForm button[type='submit']"), 5);
        assertNotNull(submit, "应有登录提交按钮（user-login.html:52）");
        assertEquals("登录", submit.getText().trim(),
                "提交按钮文案应为「登录」（user-login.html:52），实际=" + submit.getText());

        // 依据 user-login.html:55 —— 验证码登录表单默认隐藏（display:none），
        // 用 isDisplayed() 而不是 getText()（隐藏元素 getText() 返回空串，是不可靠的判定）
        var codeForm = driver.findElements(By.id("codeForm"));
        assertFalse(codeForm.isEmpty(), "登录页应存在验证码登录表单 #codeForm（user-login.html:55）");
        assertFalse(codeForm.get(0).isDisplayed(),
                "验证码登录表单默认应隐藏（user-login.html:55 style=display:none）");

        screenshot("SA-01-login-page");
    }

    @Test
    @Story("SA-02 注册页表单元素存在（输入框/按钮）")
    @DisplayName("SA-02 注册页表单元素存在（输入框/按钮）")
    void registerPageElements() {
        clearAuth();
        open("/user-register.html");

        // 依据 user-register.html:19 —— <form id="registerForm">
        var form = waitForElement(By.id("registerForm"), 8);
        assertNotNull(form, "注册页应有注册表单 #registerForm（user-register.html:19）");

        // 依据 user-register.html:22/26/30/34 —— 四个输入项的 name 属性固定
        assertNotNull(waitForElement(By.cssSelector("#registerForm [name='name']"), 5),
                "应有姓名输入框（user-register.html:22）");
        assertNotNull(waitForElement(By.cssSelector("#registerForm [name='mail']"), 5),
                "应有邮箱输入框（user-register.html:26）");
        assertNotNull(waitForElement(By.cssSelector("#registerForm [name='phoneNumber']"), 5),
                "应有手机号输入框（user-register.html:30）");
        assertNotNull(waitForElement(By.cssSelector("#registerForm [name='password']"), 5),
                "应有密码输入框（user-register.html:34）");

        // 依据 user-register.html:36 —— <button type="submit" …>注册</button>
        var submit = waitForElement(By.cssSelector("#registerForm button[type='submit']"), 5);
        assertNotNull(submit, "应有注册提交按钮（user-register.html:36）");
        assertEquals("注册", submit.getText().trim(),
                "提交按钮文案应为「注册」（user-register.html:36），实际=" + submit.getText());

        // 依据 user-register.html:55-60 —— jQuery Validate 生成的错误标签初始不存在（提交空表单后才出现），
        // 这里断言"初始没有错误态"，防止页面一打开就满屏红字（上一轮非法提交残留）
        assertEquals(0, driver.findElements(By.cssSelector("label.error")).size(),
                "刚打开注册页时不应出现校验错误标签（user-register.html:55-60 只在校验失败时生成）");

        screenshot("SA-02-register-page");
    }

    @Test
    @Story("SA-03 聊天页元素可见（输入框/发送/转人工按钮）")
    @DisplayName("SA-03 聊天页元素可见（输入框/发送/转人工按钮）")
    void chatPageElements() {
        open("/chat.html");

        // 依据 chat.html:55-56 —— 两个按钮的 id 与文案都是固定的；
        // 旧写法只断言 findElements(...) 非空（元素存在即可），这里补上文案与可用性断言。
        var input = waitForElement(By.id("input"), 8);
        assertNotNull(input, "聊天输入框应存在（chat.html 消息输入区）");
        assertTrue(input.isEnabled(), "聊天输入框应可输入");

        var sendBtn = waitForElement(By.id("sendBtn"), 8);
        assertNotNull(sendBtn, "发送按钮应存在（chat.html:55）");
        assertEquals("发送", sendBtn.getText().trim(), "发送按钮文案应为「发送」（chat.html:55）");

        var handoverBtn = waitForElement(By.id("handoverBtn"), 8);
        assertNotNull(handoverBtn, "转人工按钮应存在（chat.html:56）");
        assertEquals("转人工", handoverBtn.getText().trim(), "转人工按钮文案应为「转人工」（chat.html:56）");

        // 依据 chat.html:51 —— 页面加载即有一条静态欢迎语（.msg.ai .bubble），聊天区不是空的
        assertFalse(driver.findElements(By.cssSelector(".msg.ai .bubble")).isEmpty(),
                "聊天页加载后应有欢迎语气泡（chat.html:51）");

        screenshot("SA-03-chat-page");
    }

    @Test
    @Story("SA-04 工单管理页：未登录场景弹出提示（预期行为）")
    @DisplayName("SA-04 工单管理页：未登录场景弹出提示（预期行为）")
    void handoverPageWithoutLogin() {
        // 依据 admin-handover.html:108-115：
        //   const token = localStorage.getItem('token');
        //   if (!token) { alert('请先登录后再打开本页面'); location.assign('admin-login.html'); }
        // 即"未登录 → 弹提示 + 跳管理端登录页"是页面的既定行为（BUG-002 修复后的实现）。
        //
        // 【修复前】open(...) 后直接 assertNotNull(driver.findElement(By.tagName("body")))。
        //   findElement 找不到元素会抛 NoSuchElementException，走不到 assertNotNull，
        //   所以这句断言永远不会以"失败"形式出现——它只证明"页面能打开"，
        //   页面到底弹没弹提示、跳没跳登录页，一概没验证。
        //
        // 【修复后】断言两件可观测的事实：
        //   ① 弹窗文案等于 admin-handover.html:111 的原文；
        //   ② 页面被重定向到 admin-login.html:114。
        //   若哪天有人把这段守卫删掉（回归 BUG-002），① ② 都会立即失败。
        clearAuth();
        clearLastAlertText();
        open("/admin-handover.html");
        // 弹窗可能在 open() 的快速探测窗口之后才出现，这里补等一次
        if (lastAlertText == null) {
            waitForAlertAndAccept(5);
        }
        boolean redirected = waitForUrlContains("admin-login", 10);
        screenshot("SA-04-handover-no-login");

        assertEquals("请先登录后再打开本页面", lastAlertText,
                "未登录打开管理页应弹出 admin-handover.html:111 的提示文案，实际弹窗=" + lastAlertText);
        assertTrue(redirected,
                "未登录打开管理页应跳转 admin-login.html（admin-handover.html:114），实际 URL="
                        + driver.getCurrentUrl());
    }

    @Test
    @Story("SA-05 大厅页可访问")
    @DisplayName("SA-05 大厅页可访问")
    void lobbyPageLoads() {
        // 依据 user-lobby.html:326-329：
        //   window.AUTH_GUARD_LOGIN_PAGE = 'user-login.html';
        //   var token = localStorage.getItem('token');
        //   if (!token) { location.assign('user-login.html'); }
        // 大厅页是**登录后才可访问**的页面，不带 token 会被弹回登录页。
        // 因此本用例必须先注入登录态，否则断言"退出登录按钮存在"必然失败
        // （页面已经跳到 user-login.html 了）。
        injectToken(ApiSupport.normalUserToken(), "NORMAL");
        open("/user-lobby.html");

        // 【修复前】assertNotNull(driver.findElement(By.tagName("body"))) —— 永不失败，见类注释。
        // 【修复后】断言大厅页独有的"退出登录"入口（user-lobby.html:217），
        //   它只在渲染出用户端大厅后才会存在，既证明页面没被踢回登录页，也证明框架渲染完成。
        var logoutBtn = waitForElement(By.cssSelector("button[onclick='logout()']"), 12);
        assertNotNull(logoutBtn,
                "登录态下大厅页应渲染「退出登录」按钮（user-lobby.html:217），实际 URL=" + driver.getCurrentUrl());
        assertEquals("退出登录", logoutBtn.getText().trim(),
                "退出按钮文案应为「退出登录」（user-lobby.html:217），实际=" + logoutBtn.getText());
        assertTrue(driver.getCurrentUrl().contains("user-lobby"),
                "带 token 访问大厅页不应被重定向（user-lobby.html:328-329 只在无 token 时跳转），实际 URL="
                        + driver.getCurrentUrl());

        screenshot("SA-05-lobby");
    }

}
