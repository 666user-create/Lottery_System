package com.qa.ui;

import org.junit.jupiter.api.BeforeAll;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UI 用例 · AI 客服（SA-25 ~ SA-27）
 *
 * 断言深度：聊天用例断言**收到非空回复**（等待流式输出结束），而非仅"输入框存在"。
 *          转人工用例断言**页面出现成功提示**，并可由接口侧复核工单是否落库。
 *
 * <p>2026-10-18 空断言审计修复（SA-26 / SA-27）：
 * SA-26 原来用 {@code waitForTextContains(..., "", 3)} 判断"AI 回复非空" —— 任何文本都包含空串，
 * 该断言恒真；而且 {@code .msg.ai .bubble} 在页面加载时就已经有 1 条静态欢迎语
 * （chat.html:51），所以它连"有没有新回复"都证明不了。
 * SA-27 原来断言整页文本含「人工」或「客服」，而这两个词在页面静态文案里本来就有。
 * 现在两条用例都改为断言**发送前后的状态变化**与**精确文案**。
 */
@Feature("AI 客服")
public class AiChatUiTest extends BaseUiTest {

    private static String userToken;

    @BeforeAll
    static void prepareToken() {
        userToken = ApiSupport.normalUserToken();
    }

    @Test
    @Story("SA-25 聊天页加载：输入框/发送/转人工按钮可见")
    @DisplayName("SA-25 聊天页加载：输入框/发送/转人工按钮可见")
    void chatPageLoads() {
        injectToken(userToken, "NORMAL");
        open("/chat.html");
        screenshot("SA-25-chat-page");

        // 依据 chat.html:55-56：两个按钮的文案是固定的，顺带断言文案，
        // 避免"按钮换成了别的功能但 id 没变"这种回归被漏掉
        var input = waitForElement(By.id("input"), 8);
        assertNotNull(input, "聊天输入框应存在");
        assertTrue(input.isEnabled(), "聊天输入框应可输入");

        var sendBtn = waitForElement(By.id("sendBtn"), 8);
        assertNotNull(sendBtn, "发送按钮应存在（chat.html:55）");
        assertEquals("发送", sendBtn.getText().trim(), "发送按钮文案应为「发送」（chat.html:55）");

        var handoverBtn = waitForElement(By.id("handoverBtn"), 8);
        assertNotNull(handoverBtn, "转人工按钮应存在（chat.html:56）");
        assertEquals("转人工", handoverBtn.getText().trim(), "转人工按钮文案应为「转人工」（chat.html:56）");
    }

    @Test
    @Story("SA-26 发送消息并收到非空 AI 回复（等待流式输出完成）")
    @DisplayName("SA-26 发送消息并收到非空 AI 回复（等待流式输出完成）")
    void sendMessageAndReceiveReply() {
        injectToken(userToken, "NORMAL");
        open("/chat.html");

        // 关键：先记录发送**前**的 AI 气泡数。
        // 依据 chat.html:51，页面加载就有一条静态欢迎语 <div class="msg ai"><div class="bubble">你好呀！…</div></div>，
        // 所以"存在 .msg.ai .bubble"根本不是动态行为，必须比较数量变化。
        int bubblesBefore = driver.findElements(By.cssSelector(".msg.ai .bubble")).size();
        int userMsgsBefore = driver.findElements(By.cssSelector(".msg.user")).size();

        type(By.id("input"), "怎么参与抽奖？");
        clickIfPresent(By.id("sendBtn"));

        // 【修复前】boolean got = waitForTextContains(By.cssSelector(".msg.ai .bubble"), "", 3);
        //   ① 期望文本是空串 → 任何文本都 contains("") → 恒真；
        //   ② 紧接着 if (!got) 里赋的值再也没被使用过，是死代码；
        //   ③ 即便不看 ①②，静态欢迎语气泡也让这个条件一开始就成立。
        // 【修复后】按 chat.html:91-94 的真实时序断言：
        //   appendMsg('user', text)             → 新增 1 条用户消息
        //   const aiBubble = appendMsg('ai','') → 立刻追加一个空 AI 气泡，随后流式填充内容
        //   AI 真实调用外部模型，留足 70s 超时。
        long deadline = System.currentTimeMillis() + 70_000;
        int bubblesAfter = bubblesBefore;
        String reply = "";
        while (System.currentTimeMillis() < deadline) {
            var bubbles = driver.findElements(By.cssSelector(".msg.ai .bubble"));
            bubblesAfter = bubbles.size();
            if (bubblesAfter > bubblesBefore) {
                reply = bubbles.get(bubblesAfter - 1).getText();
                if (reply != null && reply.trim().length() > 2) {
                    break;
                }
            }
            sleep(500);
        }
        screenshot("SA-26-chat-reply");
        System.out.println("[SA-26] 发送前 AI 气泡数=" + bubblesBefore + "，发送后=" + bubblesAfter
                + "，回复前 80 字=" + (reply.length() > 80 ? reply.substring(0, 80) : reply));

        // 断言 1：用户消息确实被追加（证明"发送"动作生效，而不是按钮没反应）
        int userMsgsAfter = driver.findElements(By.cssSelector(".msg.user")).size();
        assertTrue(userMsgsAfter > userMsgsBefore,
                "点「发送」后应新增一条用户消息气泡（chat.html:91 appendMsg('user', …)），实际 发送前="
                        + userMsgsBefore + " 发送后=" + userMsgsAfter);

        // 断言 2：AI 气泡数量必须增加（证明后端真的回了，而不是只有静态欢迎语）
        assertTrue(bubblesAfter > bubblesBefore,
                "发送消息后应新增 AI 气泡（chat.html:94 appendMsg('ai','')），实际 发送前="
                        + bubblesBefore + " 发送后=" + bubblesAfter);

        // 断言 3：最后一条 AI 气泡必须有实际内容（流式输出已结束）
        assertNotNull(reply, "应能读到最后一条 AI 气泡的文本");
        assertTrue(reply.trim().length() > 2,
                "最后一条 AI 气泡不应为空（等待 70s 后仍未收到内容，可能是模型调用失败或 SSE 中断），实际="
                        + reply);
    }

    @Test
    @Story("SA-27 转人工：点击按钮后出现成功提示")
    @DisplayName("SA-27 转人工：点击按钮后出现成功提示")
    void handoverByButton() {
        injectToken(userToken, "NORMAL");
        open("/chat.html");
        int bubblesBefore = driver.findElements(By.cssSelector(".msg.ai .bubble")).size();
        clearLastAlertText();

        type(By.id("input"), "QA-UI 自动化转人工");
        clickIfPresent(By.id("handoverBtn"));

        // 依据 chat.html:147-175 的三条真实分支：
        //   :151 未登录      → alert('转人工需要先登录哦，请先登录后再操作')
        //   :156 已提交过    → alert('已经提交过转人工请求了，请耐心等待客服处理～')
        //   :171 成功        → appendMsg('ai', '已为您转接人工客服，客服人员会尽快联系您～ 🙋‍♀️')
        //   :173 失败        → alert('转人工失败：' + e.message)
        // 本次已注入登录态，所以 :151 分支不该出现；:156 是**合法的既有状态**（同一账号重复转人工），
        // 不能当成失败。因此这里枚举两种可接受结果，其余一律判失败。
        long deadline = System.currentTimeMillis() + 20_000;
        boolean successBubble = false;
        while (System.currentTimeMillis() < deadline) {
            // 弹窗会阻塞页面 JS，读 DOM 前必须先关掉（否则抛 UnexpectedAlertOpenException）
            dismissAlertIfPresent();
            String last = lastTextOf(By.cssSelector(".msg.ai .bubble"));
            if (last != null && last.contains("已为您转接人工客服")) {
                successBubble = true;
                break;
            }
            if (lastAlertText != null) {
                break; // 已经拿到弹窗结果，不用再等气泡
            }
            sleep(300);
        }
        screenshot("SA-27-handover");

        // 【修复前】assertFalse(text.contains("转人工失败")) + assertTrue(text.contains("人工")||text.contains("客服"))
        //   问题：整页文本里「转人工」按钮（chat.html:56）、「转人工需要先登录哦」等已有文案
        //   本来就含「人工」「客服」，第二个断言恒真；第一个断言只能抓"恰好写了转人工失败"这一种情况。
        // 【修复后】
        assertFalse(lastAlertText != null && lastAlertText.contains("转人工失败"),
                "点击转人工不应失败（chat.html:173 alert('转人工失败：…')），实际弹窗=" + lastAlertText);
        assertFalse(lastAlertText != null && lastAlertText.contains("转人工需要先登录"),
                "已注入登录态，不应走到未登录分支（chat.html:151），实际弹窗=" + lastAlertText);

        if (lastAlertText != null) {
            // 唯一可接受的弹窗：重复转人工
            assertTrue(lastAlertText.contains("已经提交过转人工请求"),
                    "唯一可接受的弹窗是「已经提交过转人工请求了…」（chat.html:156），实际弹窗=" + lastAlertText);
        } else {
            // 没有弹窗 → 必须真的渲染出成功气泡
            assertTrue(successBubble,
                    "转人工成功后应追加 chat.html:171 的原文气泡「已为您转接人工客服，客服人员会尽快联系您～」，"
                            + "实际未出现（发送前气泡数=" + bubblesBefore + "，页面文本前 200 字="
                            + bodyText().substring(0, Math.min(200, bodyText().length())) + "）");
        }
    }
}
