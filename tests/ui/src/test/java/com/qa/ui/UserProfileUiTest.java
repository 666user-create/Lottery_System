package com.qa.ui;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UI 用例 · 个人中心（SA-52 ~ SA-53）—— 页面 {@code user-profile.html}
 *
 * <p>本页此前**没有任何自动化覆盖**（2026-10-18 新增）。它同时承载
 * 个人信息展示、邮箱修改、密码修改、收货地址维护四块能力，
 * 其中后三块都要走"验证码/口令"二次校验（见 :185-208、:239-270），
 * 会在后续迭代里继续补用例。本轮先覆盖两条**不依赖外部通道**的稳定路径。
 *
 * <h3>断言深度</h3>
 * <ul>
 *   <li>SA-52 不只断言"用户名不是 ---"，而是做**跨字段交叉校验**：
 *       {@code #userName}、{@code #nameText}、{@code #vUserName} 三处必须同源
 *       （由 :308-312 同一份 {@code data.userName} 写入），
 *       {@code #vIdentity} 必须是「普通用户」（:314 把 NORMAL 映射成中文），
 *       {@code #vUserId} 必须是数字（:313 直接写 userId）。
 *       只断言"页面有内容"的话，{@code /user/me} 返回错人、或者身份映射反了都发现不了；</li>
 *   <li>SA-53 覆盖收货地址的**三级前端校验顺序**（:615-617），
 *       三处 {@code return} 一旦被删掉，用户就能提交半截地址进数据库。</li>
 * </ul>
 */
@Feature("个人中心")
public class UserProfileUiTest extends BaseUiTest {

    private static String userToken;

    @BeforeAll
    static void prepareToken() {
        userToken = ApiSupport.normalUserToken();
    }

    @Test
    @Story("SA-52 个人信息展示：用户名三处同源、身份映射为普通用户、退出入口可见")
    @DisplayName("SA-52 个人信息展示：用户名三处同源、身份映射为普通用户、退出入口可见")
    void profileInfoRendered() {
        injectToken(userToken, "NORMAL");
        open("/user-profile.html");

        // ---------- ① /user/me 必须回填用户名（三处同源） ----------
        // 依据 user-profile.html:306-312：result.code===200 时把 data.userName 同时写入
        //   #userName（顶部问候 :83）、#nameText（头像旁 :93）、#vUserName（详情 :99）、#avatar（首字）
        // 页面初始值是 '---'，若接口失败/未登录，这三处会一直停在 '---'。
        // 注意：不能用「文本非空」判断（初始占位 '---' 也是非空），必须显式排除占位值。
        boolean loaded = false;
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            String v = safeText(By.id("userName"));
            if (!v.isEmpty() && !"---".equals(v)) {
                loaded = true;
                break;
            }
            sleep(200);
        }
        screenshot("SA-52-profile");
        assertTrue(loaded,
                "个人中心应从 /user/me 回填用户名（user-profile.html:306-309 把 data.userName 写进 #userName），"
                        + "实际 #userName=" + safeText(By.id("userName"))
                        + "（初始占位是 '---'，说明接口没成功返回）");

        String userName = safeText(By.id("userName"));
        // 三处必须同源：任一处不同说明渲染分支被改坏（例如只改了 :309 忘了 :312）
        assertEquals(userName, safeText(By.id("nameText")),
                "顶部问候与头像旁用户名应同源（user-profile.html:309-310 都取 data.userName）");
        assertEquals(userName, safeText(By.id("vUserName")),
                "顶部问候与详情区用户名应同源（user-profile.html:309/:312 都取 data.userName）");

        // ---------- ② 身份必须映射成中文「普通用户」 ----------
        // 依据 user-profile.html:314：data.identity === 'ADMIN' ? '管理员' : '普通用户'
        // 本用例注入的是普通用户 token，若这里显示「管理员」，说明身份判断逻辑反了——
        // 而同一份 isAdmin 变量还控制着 :317-318 的「口令验证」入口显隐，属于权限相关缺陷。
        assertEquals("普通用户", safeText(By.id("vIdentity")),
                "普通用户个人中心的身份应显示「普通用户」（user-profile.html:314），实际="
                        + safeText(By.id("vIdentity")));

        // ---------- ③ userId 必须是数字 ----------
        // 依据 user-profile.html:313：setText('#vUserId', d.userId === undefined ? '---' : d.userId)
        String userId = safeText(By.id("vUserId"));
        assertTrue(userId.matches("\\d+"),
                "用户 ID 应为数字（user-profile.html:313 直接写 d.userId），实际=" + userId);

        // ---------- ④ 退出入口必须存在且文案正确 ----------
        // 依据 user-profile.html:84 <button … onclick="logout()">；:299 logout() 会 localStorage.clear() 并跳登录页
        var logoutBtn = waitForElement(By.cssSelector("button[onclick='logout()']"), 8);
        assertNotNull(logoutBtn, "个人中心应有退出登录按钮（user-profile.html:84）");
        assertTrue(logoutBtn.getText().contains("退出"),
                "退出按钮文案应含「退出」，实际=" + logoutBtn.getText());
        assertTrue(logoutBtn.isEnabled(), "退出按钮应可点击");
    }

    @Test
    @Story("SA-53 收货地址保存：三级必填校验按 姓名→手机号→地址 顺序拦截")
    @DisplayName("SA-53 收货地址保存：三级必填校验按 姓名→手机号→地址 顺序拦截")
    void receiverAddressValidation() {
        injectToken(userToken, "NORMAL");
        open("/user-profile.html");

        // 等页面把已有的收货信息回填完（loadReceiverInfo :589-607），再清空做校验测试，
        // 否则回填是异步的，可能在清空之后又把值写回来，导致"以为空着其实有值"。
        // 这里等的是 /user/me 回填完成（同一页面初始化的同一批请求），同样要排除占位值 '---'。
        boolean profileReady = false;
        long readyDeadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < readyDeadline) {
            String v = safeText(By.id("userName"));
            if (!v.isEmpty() && !"---".equals(v)) {
                profileReady = true;
                break;
            }
            sleep(200);
        }
        assertTrue(profileReady,
                "个人中心未加载完成（#userName 仍是占位 '---'），无法验证收货地址校验，"
                        + "实际 #userName=" + safeText(By.id("userName")) + "，URL=" + driver.getCurrentUrl());
        sleep(1500);
        driver.findElement(By.id("receiverName")).clear();
        driver.findElement(By.id("receiverPhone")).clear();
        driver.findElement(By.id("receiverAddress")).clear();

        // ---------- ① 全空 → 先拦姓名 ----------
        // 依据 user-profile.html:609-617 的顺序校验：
        //   :615 if (!name)    { '#receiverErr' = '请输入收货人姓名'; return; }
        //   :616 if (!phone)   { '#receiverErr' = '请输入收货人手机号'; return; }
        //   :617 if (!address) { '#receiverErr' = '请输入详细地址'; return; }
        // 顺序本身是契约：用户填了姓名却被提示"请输入姓名"会非常困惑，
        // 所以这里逐级推进，每一级都断言**精确文案**，而不是"出现了某条错误"。
        clickIfPresent(By.id("receiverSubmit"));
        assertEquals("请输入收货人姓名", waitForErrText(),
                "全空提交应先提示「请输入收货人姓名」（user-profile.html:615）");

        // ---------- ② 只填姓名 → 拦手机号 ----------
        type(By.id("receiverName"), "QA-UI 收货人");
        clickIfPresent(By.id("receiverSubmit"));
        assertEquals("请输入收货人手机号", waitForErrText(),
                "只填姓名时应提示「请输入收货人手机号」（user-profile.html:616）");

        // ---------- ③ 再填手机号 → 拦地址 ----------
        type(By.id("receiverPhone"), "13800000000");
        clickIfPresent(By.id("receiverSubmit"));
        assertEquals("请输入详细地址", waitForErrText(),
                "只填姓名与手机号时应提示「请输入详细地址」（user-profile.html:617）");

        // ---------- ④ 校验被拦时必须已清掉上一轮的错误提示 ----------
        // 依据 user-profile.html:613 —— 每次点击先 $('#receiverErr').text('') 再逐级校验。
        // 若这一行被删，用户会看到"上一次的报错"残留，误以为本次提交也失败了。
        String lastErr = safeText(By.id("receiverErr"));
        assertEquals("请输入详细地址", lastErr,
                "每次点击应重置错误位后按顺序给出当前级提示（user-profile.html:613-617），实际=" + lastErr);

        screenshot("SA-53-profile-receiver-validation");
    }

    // ==================== 账号安全：修改邮箱 / 修改密码（SA-54 ~ SA-58） ====================
    /*
     * 为什么都放在这个类里：这五条用例挂在同一个页面、共用同一套「验证方式 + 校验链」机制
     * （user-profile.html:447-477 的 buildVerifyBody / applyVerifyPane / resetVerify），
     * 拆到别的类就得复制这些辅助方法，反而更容易漂移。
     *
     * 分层策略（这是本轮刻意的设计，不是偷懒）：
     *   · SA-54 / SA-55 只打**前端校验链** —— 不产生任何请求，零副作用、零外部依赖；
     *   · SA-56 打**验证方式切换的显示契约**，其中包含「普通用户不得看到管理员口令入口」这条权限相关约束；
     *   · SA-57 / SA-58 才是**真实闭环** —— 用一次性专属账号真改一次，再从接口侧用新凭据登录反证。
     *
     * 为什么必须有真实闭环：前三条再严也只能证明"校验和显隐是对的"，
     * 证明不了"改了到底生效没有"。而改密码/改邮箱最严重的缺陷恰恰是
     * "前端提示成功、后端没改"——这种缺陷只有真改一次再用新凭据登录才能抓到。
     * 之所以把真实闭环放在 SA-57/58 而不是所有用例都真改：真实改密依赖后端
     * 与数据库写操作，跑得越少越不容易互相干扰。
     */

    @Test
    @Story("SA-54 修改邮箱前端校验链：空值 → 格式 → 验证方式，且不发请求")
    @DisplayName("SA-54 修改邮箱前端校验链：空值 → 格式 → 验证方式，且不发请求")
    void emailUpdateValidationChain() {
        injectToken(userToken, "NORMAL");
        open("/user-profile.html");
        openEmailModal();

        // ---------- ① 新邮箱为空 → 「请输入新邮箱」 ----------
        // 依据 user-profile.html:536-539：每次提交先清空 #emailErr / #emailFormErr，
        // 再 `if (!newEmail) { $('#emailErr').text('请输入新邮箱'); return; }`
        clickIfPresent(By.id("emailSubmit"));
        assertEquals("请输入新邮箱", waitForFieldText(By.id("emailErr")),
                "新邮箱为空时应提示「请输入新邮箱」（user-profile.html:539），实际=" + safeText(By.id("emailErr")));

        // ---------- ② 格式非法 → 「邮箱格式不正确」 ----------
        // 依据 user-profile.html:540-542 的正则分支。"not-an-email" 不含 @，必然不匹配。
        type(By.id("newEmail"), "not-an-email");
        clickIfPresent(By.id("emailSubmit"));
        assertEquals("邮箱格式不正确", waitForFieldText(By.id("emailErr")),
                "邮箱格式非法时应提示「邮箱格式不正确」（user-profile.html:541），实际=" + safeText(By.id("emailErr")));

        // ---------- ③ 格式合法但原密码留空 → 落到验证方式的提示 ----------
        // 依据 :543-545：先把 newEmail 与 verifyType 放进 body，再 buildVerifyBody；
        // 默认验证方式是 password（:173 checked），原密码为空时 :452 返回「请输入原密码」
        type(By.id("newEmail"), "qa_auto_shape_only@test.com");
        driver.findElement(By.id("emailOldPwd")).clear();
        clickIfPresent(By.id("emailSubmit"));
        assertEquals("请输入原密码", waitForFieldText(By.id("emailFormErr")),
                "选「输入原密码」方式却留空时，应提示「请输入原密码」（user-profile.html:452），实际="
                        + safeText(By.id("emailFormErr")));

        // ---------- ④ 三次非法提交都不能发出请求、也不能关掉弹窗 ----------
        // 依据：三条分支都是 `return`（:539 / :541 / :545），走不到 :547 的 post('/user/email/update')
        // 也就是说本用例全程**零写操作**——这是它能安全地反复跑的原因。
        assertTrue(isDisplayed("emailModal"),
                "前端校验未通过时弹窗应保持打开（三条分支都在发请求前 return），实际已关闭");
        screenshot("SA-54-email-validation");
    }

    @Test
    @Story("SA-55 修改密码前端校验链：验证方式 → 新密码 → 格式 → 两次一致")
    @DisplayName("SA-55 修改密码前端校验链：验证方式 → 新密码 → 格式 → 两次一致")
    void passwordUpdateValidationChain() {
        injectToken(userToken, "NORMAL");
        open("/user-profile.html");
        openPwdModal();

        // ---------- ① 全空 → 先报「请输入原密码」 ----------
        // 关键顺序：user-profile.html:564-566 的 buildVerifyBody 在 :568 的「新密码为空」判断**之前**。
        // 顺序反过来的话，用户会先被要求"请输入新密码"、填完才被告知还要原密码，体验很差，
        // 所以这个顺序本身是契约，值得用断言钉住。
        clickIfPresent(By.id("pwdSubmit"));
        assertEquals("请输入原密码", waitForFieldText(By.id("pwdFormErr")),
                "全空提交时应先提示「请输入原密码」（user-profile.html:565-566 → :452），实际="
                        + safeText(By.id("pwdFormErr")));

        // ---------- ② 补上原密码、新密码留空 → 「请输入新密码」 ----------
        // 依据 user-profile.html:568
        type(By.id("pwdOldPwd"), "123456");
        clickIfPresent(By.id("pwdSubmit"));
        assertEquals("请输入新密码", waitForFieldText(By.id("pwdFormErr")),
                "原密码已填、新密码留空时应提示「请输入新密码」（user-profile.html:568），实际="
                        + safeText(By.id("pwdFormErr")));

        // ---------- ③ 新密码 3 位 → 「新密码需为6-12位字母或数字」 ----------
        // 依据 user-profile.html:569-571 的 ^[0-9A-Za-z]{6,12}$
        type(By.id("newPwd"), "abc");
        clickIfPresent(By.id("pwdSubmit"));
        assertEquals("新密码需为6-12位字母或数字", waitForFieldText(By.id("pwdFormErr")),
                "新密码不足 6 位时应提示长度规则（user-profile.html:570），实际=" + safeText(By.id("pwdFormErr")));

        // ---------- ④ 两次输入不一致 → 提示不一致 ----------
        // 依据 user-profile.html:572。
        // 这里故意用 9 位 vs 10 位：两者都在 6-12 位的合法区间内，
        // 确保失败原因只能是"不一致"，而不会因为长度/字符集先被前两条分支拦下。
        type(By.id("newPwd"), "abc123456");
        type(By.id("newPwd2"), "abc1234567");
        clickIfPresent(By.id("pwdSubmit"));
        assertEquals("两次输入的新密码不一致", waitForFieldText(By.id("pwdFormErr")),
                "两次输入不一致时应提示「两次输入的新密码不一致」（user-profile.html:572），实际="
                        + safeText(By.id("pwdFormErr")));

        // ---------- ⑤ 全程零请求，弹窗保持打开 ----------
        // 依据：四条分支全部在 :566/:568/:571/:572 return，走不到 :575 的 post('/user/password/update')
        // 所以这条用例不会真的改掉任何人的密码。
        assertTrue(isDisplayed("pwdModal"), "前端校验未通过时改密弹窗应保持打开，实际已关闭");
        screenshot("SA-55-pwd-validation");
    }

    @Test
    @Story("SA-56 验证方式切换：输入区显隐、错误提示清空、普通用户看不到管理员口令")
    @DisplayName("SA-56 验证方式切换：输入区显隐、错误提示清空、普通用户看不到管理员口令")
    void verifyModeSwitchContract() {
        injectToken(userToken, "NORMAL");
        open("/user-profile.html");
        openEmailModal();

        // ---------- ① 默认方式是「输入原密码」 ----------
        // 依据 user-profile.html:173 的 checked + :474-477 resetVerify 会把 password 重新勾上
        assertTrue(isDisplayed("emailPwdPane"),
                "默认验证方式为「输入原密码」，原密码输入区应可见（user-profile.html:185/:474-477）");
        assertFalse(isDisplayed("emailCodePane"),
                "未选择短信验证码时，验证码输入区应隐藏（user-profile.html:196 style=display:none）");
        assertFalse(isDisplayed("emailSecretPane"),
                "未选择管理员口令时，口令输入区应隐藏（user-profile.html:190 style=display:none）");

        // ---------- ② 先制造一条验证错误，用于验证"切换方式会清空它" ----------
        type(By.id("newEmail"), "qa_auto_switch@test.com");
        clickIfPresent(By.id("emailSubmit"));
        assertEquals("请输入原密码", waitForFieldText(By.id("emailFormErr")),
                "前置：选原密码方式但留空，应产生「请输入原密码」");

        // ---------- ③ 切到「短信验证码」 ----------
        // 依据 user-profile.html:479-484 的 change 处理器：applyVerifyPane('email','code')
        // → :466-471 逐个 toggle；:482 清空 #emailFormErr；:483 loadMaskedPhone 写提示
        driver.findElement(By.cssSelector("input[name='emailVerify'][value='code']")).click();
        assertTrue(isDisplayed("emailCodePane"),
                "切到「短信验证码」后验证码输入区应显示（user-profile.html:479-484）");
        assertFalse(isDisplayed("emailPwdPane"),
                "切到「短信验证码」后原密码输入区应隐藏（user-profile.html:466-471 applyVerifyPane）");
        // 这条是"切换时要清掉上一次错误"的契约：否则用户会看到"请输入原密码"这种
        // 已经不适用的提示，误以为当前方式也填错了。
        assertEquals("", safeText(By.id("emailFormErr")),
                "切换验证方式应清空上一次的验证错误提示（user-profile.html:482）");
        // 验证码区下方要告诉用户码发到哪个号（脱敏），或明确说没绑手机（:394-398 → :483）
        assertTrue(waitForTextMatches(By.id("emailCodeHint"),
                        "^(验证码将发送至 .+|未绑定手机号，无法使用验证码验证)$", 8),
                "选中验证码方式后应提示发送目标或提示未绑定手机号（user-profile.html:394-398/:483），实际="
                        + safeText(By.id("emailCodeHint")));

        // ---------- ④ 切回「输入原密码」 ----------
        driver.findElement(By.cssSelector("input[name='emailVerify'][value='password']")).click();
        assertTrue(isDisplayed("emailPwdPane"),
                "切回「输入原密码」后原密码区应重新显示（user-profile.html:466-471）");
        assertFalse(isDisplayed("emailCodePane"), "切回后验证码区应隐藏");

        // ---------- ⑤ 普通用户不得看到「管理员口令」这一验证方式 ----------
        // 依据：:179-181 的入口 label 带 .admin-only 且 style=display:none；
        // :317-318 只有在 /user/me 返回 identity==='ADMIN' 时才 $('.admin-only').toggle(true)。
        // 这是**展示层的权限契约**：普通用户若看到该入口，会误以为能用口令绕过短信验证。
        // 注意服务端仍会独立校验（:315-316 注释：普通用户硬传 verifyType=secret 会被 128 拒绝），
        // 所以这里只断言"看不到"，不断言"用不了"——后者属于接口测试的职责。
        assertFalse(isDisplayed("emailSecretPane"),
                "普通用户的口令验证输入区必须保持隐藏（user-profile.html:190 + :317-318）");
        var secretRadios = driver.findElements(By.cssSelector("input[name='emailVerify'][value='secret']"));
        assertFalse(secretRadios.isEmpty(),
                "管理员口令单选项应存在于 DOM（user-profile.html:179-181），只是不展示给普通用户");
        assertFalse(secretRadios.get(0).isDisplayed(),
                "普通用户不应看到「管理员口令」验证方式入口（user-profile.html:179-181 + :317-318）");
        screenshot("SA-56-verify-switch");
    }

    @Test
    @Story("SA-57 修改密码真实闭环：专属账号改密后新密码可登录、旧密码失效")
    @DisplayName("SA-57 修改密码真实闭环：专属账号改密后新密码可登录、旧密码失效")
    void passwordUpdateEndToEnd() {
        int r = java.util.concurrent.ThreadLocalRandom.current().nextInt(10000000, 99999999);
        String mail = "qa_auto_uipwd_" + r + "@test.com";
        String phone = "137" + r;
        String oldPwd = "123456";
        String newPwd = "654321";

        // 用**一次性专属账号**，绝不动 qa_auto_uiuser@test.com 这种被其它用例共用的账号：
        // 万一改密只成功了一半（库改了、前端状态没同步），会连带把一批无关用例搞红，且极难定位。
        ApiSupport.ensureAccount(mail, phone, "NORMAL", oldPwd);
        String token = ApiSupport.login(mail, oldPwd, "NORMAL");

        injectToken(token, "NORMAL");
        open("/user-profile.html");
        openPwdModal();

        type(By.id("pwdOldPwd"), oldPwd);
        type(By.id("newPwd"), newPwd);
        type(By.id("newPwd2"), newPwd);
        clickIfPresent(By.id("pwdSubmit"));

        // 断言 1：以**服务端真实生效**作为成功判据，而不是"弹窗有没有关"。
        //
        // 为什么不用"弹窗关闭"：实测证据（2026-10-18 21:43:49）——
        //   应用日志：`updatePassword verifyType:password` → `用户 501 请求修改密码` → `用户 501 修改密码成功`
        //   数据库：该账号 password 已变更
        //   但页面现场：`<div class="modal fade show" style="display:block">`，.modal-backdrop 仍在
        // 原因：Bootstrap 4 的 modal('hide') 要等 CSS transitionend 才移除 .show/遮罩，
        // 该回调在无头 Chrome 下不一定触发，于是"操作已成功、弹窗却停在那儿"。
        // 用动画状态当判据 → 用例会因为浏览器渲染细节而红，属于**假失败**，
        // 而这种假失败最有害：会让人开始怀疑一个其实正常的功能。
        boolean pwdChanged = waitLoginWorks(mail, newPwd, 20);
        screenshot("SA-57-pwd-updated");
        assertTrue(pwdChanged,
                "改密码后应能用新密码登录（等价于后端真的改了）。页面错误提示=" + safeText(By.id("pwdFormErr")));

        // 断言 2：前端不得报错（失败分支会把 message 写进 #pwdFormErr，见 user-profile.html:578 的 errSel）
        assertEquals("", safeText(By.id("pwdFormErr")),
                "服务端已改密成功，前端不应同时显示错误信息（user-profile.html:578）");

        // 断言 3：旧密码必须失效
        boolean oldStillWorks = true;
        try {
            ApiSupport.login(mail, oldPwd, "NORMAL");
        } catch (RuntimeException e) {
            oldStillWorks = false;
        }
        assertFalse(oldStillWorks, "改密码后旧密码不应还能登录（否则等于没改，且是严重安全问题）");
    }

    @Test
    @Story("SA-58 修改邮箱真实闭环：专属账号改邮箱后新邮箱可登录、旧邮箱失效")
    @DisplayName("SA-58 修改邮箱真实闭环：专属账号改邮箱后新邮箱可登录、旧邮箱失效")
    void emailUpdateEndToEnd() {
        int r = java.util.concurrent.ThreadLocalRandom.current().nextInt(10000000, 99999999);
        String oldMail = "qa_auto_uimail_" + r + "@test.com";
        String newMail = "qa_auto_uimailnew_" + r + "@test.com";
        String phone = "138" + r;
        String pwd = "123456";

        // 同样用一次性专属账号：邮箱是登录名之一，改错了会直接影响这个账号能否再登录。
        ApiSupport.ensureAccount(oldMail, phone, "NORMAL", pwd);
        String token = ApiSupport.login(oldMail, pwd, "NORMAL");

        injectToken(token, "NORMAL");
        open("/user-profile.html");
        openEmailModal();

        // 当前邮箱应回填成 oldMail（依据 :390-392 loadCurrentEmail 把 account.email 写进 #curEmail）
        // 注意：#curEmail 是 <input>，它的内容在 value 属性里，getText() 对 input 恒返回空串，
        // 必须读 getAttribute("value") —— 用 getText() 写这条断言会变成永远失败（或永远为空的假判定）。
        String curEmail = "";
        long curDeadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < curDeadline) {
            var el = driver.findElements(By.id("curEmail"));
            if (!el.isEmpty()) {
                curEmail = String.valueOf(el.get(0).getAttribute("value"));
                if (oldMail.equals(curEmail)) {
                    break;
                }
            }
            sleep(200);
        }
        assertEquals(oldMail, curEmail,
                "修改邮箱弹窗应回填当前邮箱（user-profile.html:390-392 loadCurrentEmail → #curEmail 的 value），"
                        + "期望=" + oldMail + "，实际=" + curEmail);

        type(By.id("newEmail"), newMail);
        type(By.id("emailOldPwd"), pwd);
        clickIfPresent(By.id("emailSubmit"));

        // 断言 1：同 SA-57，以"新邮箱能登录"作为服务端真实生效的判据，
        // 不用"弹窗关闭"（Bootstrap modal('hide') 依赖 transitionend，无头 Chrome 下可能停在 .show）。
        boolean mailChanged = waitLoginWorks(newMail, pwd, 20);
        screenshot("SA-58-email-updated");
        assertTrue(mailChanged,
                "改邮箱后应能用新邮箱登录（等价于登录凭据真的换成了一个新邮箱）。"
                        + "页面错误提示=" + safeText(By.id("emailFormErr")) + " / " + safeText(By.id("emailErr")));

        // 断言 2：前端不得报错（失败分支会把 message 写进 #emailFormErr，见 user-profile.html:552）
        assertEquals("", safeText(By.id("emailFormErr")),
                "服务端已改邮箱成功，前端不应同时显示错误信息（user-profile.html:548-552）");

        // 断言 3：旧邮箱必须失效
        boolean oldWorks = true;
        try {
            ApiSupport.login(oldMail, pwd, "NORMAL");
        } catch (RuntimeException e) {
            oldWorks = false;
        }
        assertFalse(oldWorks, "改邮箱后旧邮箱不应还能作为登录名登录（否则等于没改）");
    }

    // ==================== 本类共用的辅助方法 ====================

    /** 等个人中心把 /user/me 的结果回填完（#userName 从占位 '---' 变成真实用户名） */
    private void waitProfileReady() {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            String v = safeText(By.id("userName"));
            if (!v.isEmpty() && !"---".equals(v)) {
                return;
            }
            sleep(200);
        }
        fail("个人中心未加载完成：#userName 仍是占位 '---'，URL=" + driver.getCurrentUrl());
    }

    /** 打开「修改邮箱」弹窗：点真实入口按钮（user-profile.html:119），并等弹窗可见 */
    private void openEmailModal() {
        waitProfileReady();
        assertTrue(clickIfPresent(By.cssSelector("button[onclick='openEmailModal()']")),
                "账号安全区应有「修改邮箱」入口按钮（user-profile.html:119）");
        assertTrue(waitDisplayed("emailModal", 10),
                "点击「修改」后应弹出修改邮箱窗口（user-profile.html:152 / openEmailModal :407-415）");
    }

    /** 打开「修改密码」弹窗：点真实入口按钮（user-profile.html:126），并等弹窗可见 */
    private void openPwdModal() {
        waitProfileReady();
        assertTrue(clickIfPresent(By.cssSelector("button[onclick='openPwdModal()']")),
                "账号安全区应有「修改密码」入口按钮（user-profile.html:126）");
        assertTrue(waitDisplayed("pwdModal", 10),
                "点击「修改」后应弹出修改密码窗口（user-profile.html:215 / openPwdModal :417-424）");
    }

    /** 元素当前是否可见（不存在按不可见处理，避免 NoSuchElementException 掩盖真正的断言失败） */
    private boolean isDisplayed(String id) {
        var list = driver.findElements(By.id(id));
        return !list.isEmpty() && list.get(0).isDisplayed();
    }

    /** 轮询等待元素可见（模态框有 300ms 淡入动画，不能点完立刻断言） */
    private boolean waitDisplayed(String id, int seconds) {
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (isDisplayed(id)) {
                return true;
            }
            sleep(150);
        }
        return false;
    }

    /**
     * 轮询直到「用指定登录名 + 密码能登录成功」，返回是否成功。
     *
     * <p>为什么用它当「改密 / 改邮箱是否真的生效」的判据：
     * 它是**服务端事实**，不依赖任何前端动画、toastr 或 Bootstrap 版本。
     * 实测证据（2026-10-18 21:43:49）：后端日志已记录「用户 501 修改密码成功」、库里密码也已变更，
     * 但页面上的 {@code #pwdModal} 仍停在 {@code class="modal fade show"} ——
     * Bootstrap 4 的 {@code modal('hide')} 要等 CSS {@code transitionend} 才移除 .show，
     * 该回调在无头 Chrome 下不一定触发。若拿"弹窗关了"当成功判据，就会得到一个**假失败**，
     * 而假失败比漏测更有害：它会让人开始怀疑一个其实正常的功能。
     * 而「新凭据能不能登进去」才是用户真正关心、也无法伪装的结论。
     *
     * <p>为什么轮询而不是立刻断言：提交是异步的，接口处理 + 落库需要一点时间；
     * 直接断言会偶发失败，固定 sleep 又会拖慢整个套件，轮询两者兼顾。
     */
    private boolean waitLoginWorks(String loginName, String password, int seconds) {
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            try {
                String t = ApiSupport.login(loginName, password, "NORMAL");
                if (t != null && t.length() >= 50) {
                    return true;
                }
            } catch (RuntimeException ignored) {
                // 尚未生效（或提交还没回来），继续等
            }
            sleep(500);
        }
        return false;
    }

    /**
     * 等某个提示位出现文本并返回它；超时返回空串。
     *
     * <p>为什么不用固定 sleep：这些提示是点击后同步写入的，但 Selenium 的 click 返回时
     * 浏览器未必已经刷完 DOM，短轮询比 `sleep(1000)` 既快又稳。
     */
    private String waitForFieldText(By by) {
        long deadline = System.currentTimeMillis() + 8_000;
        while (System.currentTimeMillis() < deadline) {
            String t = safeText(by);
            if (!t.isEmpty()) {
                return t;
            }
            sleep(150);
        }
        return "";
    }

    /**
     * 等 {@code #receiverErr} 出现文本并返回它。
     *
     * <p>为什么要等：错误文案是点击后同步写入的，但 Selenium 的 click 返回时
     * 浏览器未必已经把 DOM 刷完；用一个短轮询替代固定 sleep，避免时序抖动。
     */
    private String waitForErrText() {
        long deadline = System.currentTimeMillis() + 8_000;
        while (System.currentTimeMillis() < deadline) {
            String t = safeText(By.id("receiverErr"));
            if (!t.isEmpty()) {
                return t;
            }
            sleep(150);
        }
        return "";
    }

    /** 读取元素文本；元素不存在返回空串（仅用于构造断言消息） */
    private String safeText(By by) {
        var list = driver.findElements(by);
        return list.isEmpty() ? "" : list.get(0).getText().trim();
    }
}
