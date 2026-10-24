"""
第③步 · 关键路径用例（5/7）：AI 客服

覆盖：工具调用聊天（同步）、RAG 聊天（同步）、SSE 流式契约、空消息降级、转人工关键词闭环。

外部依赖说明：AI 接口真实调用 DeepSeek（application.properties:74-76），
超时放宽至 AI_TIMEOUT；调用次数刻意克制以控制外部成本。

能抓到的缺陷：
  - 接口返回 200 但回答为空串（前端显示空白气泡）
  - SSE 接口退化为普通 JSON（Content-Type 不含 text/event-stream）→ 前端流式解析失效
  - 流未以 [DONE] 结束 → 前端 loading 永不停止
  - 转人工关键词未生成工单（业务闭环断裂）
"""
import uuid

import pytest
import allure

from common import config
from common.client import assert_code, assert_success


def _session_id() -> str:
    """会话标识带统一前缀，便于识别与清理（ai:memory:{sessionId}）"""
    return f"{config.TEST_PREFIX}sess_{uuid.uuid4().hex[:8]}"


@allure.feature("AI 客服")
@allure.story("AI 对话/RAG/多轮记忆/SSE")
@pytest.mark.smoke
@pytest.mark.keypath
class TestAiChatKeyPath:

    def test_tool_call_chat_sync(self, anon_client):
        """正常流：工具调用聊天返回非空回答（游客可用）"""
        resp = anon_client.post("/api/chat/sync",
                               json_body={"message": "查询所有活动"},
                               timeout=config.AI_TIMEOUT)
        assert_success(resp, hint="[AI工具调用聊天]", expect_data=True)
        answer = resp.data
        assert isinstance(answer, str) and len(answer.strip()) > 0, (
            f"[AI工具调用聊天] 期望非空字符串回答，实际 {answer!r}\n 响应: {resp.brief()}")

    def test_rag_chat_sync(self, anon_client):
        """正常流：RAG 知识库聊天返回非空回答"""
        resp = anon_client.post("/api/ai/chat/sync",
                               json_body={"message": "怎么参与抽奖?", "sessionId": _session_id()},
                               timeout=config.AI_TIMEOUT)
        assert_success(resp, hint="[AI-RAG聊天]", expect_data=True)
        answer = resp.data
        assert isinstance(answer, str) and len(answer.strip()) > 0, (
            f"[AI-RAG聊天] 期望非空字符串回答，实际 {answer!r}")

    def test_empty_message_degrades_gracefully(self, anon_client):
        """分支：空消息 → 返回欢迎语而非报错"""
        resp = anon_client.post("/api/ai/chat/sync",
                               json_body={"message": "", "sessionId": _session_id()},
                               timeout=config.AI_TIMEOUT)
        assert_success(resp, hint="[AI-空消息]", expect_data=True)
        assert len(str(resp.data).strip()) > 0, (
            f"[AI-空消息] 期望兜底欢迎语，实际空响应：{resp.brief()}")

    def test_sse_stream_contract(self, anon_client):
        """
        SSE 契约：Content-Type 为 text/event-stream，响应体以 [DONE] 结束。
        能抓到：接口退化为普通 JSON（前端 fetch 流式解析失效）、流未正常闭合。
        """
        resp = anon_client.post("/api/ai/chat",
                               json_body={"message": "你好", "sessionId": _session_id()},
                               timeout=config.AI_TIMEOUT)
        assert resp.status == 200, f"[AI-SSE] 期望 HTTP=200，实际 {resp.brief()}"
        ctype = resp.raw.headers.get("Content-Type", "")
        assert "text/event-stream" in ctype, (
            f"[AI-SSE] 期望 Content-Type 含 text/event-stream，实际 {ctype!r}\n"
            f" → 前端流式解析会失效。响应: {resp.brief()}")
        assert "[DONE]" in resp.text, (
            f"[AI-SSE] 期望响应以 [DONE] 结束标记收尾，实际未找到\n 响应: {resp.brief()}")
        assert "data:" in resp.text, (
            f"[AI-SSE] 期望响应含 SSE data: 帧，实际未找到：{resp.brief()}")


@allure.feature("AI 客服")
@allure.story("转人工关键词触发")
@pytest.mark.keypath
class TestHandoverKeywordKeyPath:
    """转人工闭环：关键词触发 → 工单生成 → 管理端可见（业务链路断点检测）"""

    def test_keyword_trigger_creates_ticket(self, user_client, admin_client, handover_cleanup):
        """
        正常流：消息含"转人工"关键词 → 自动生成 PENDING 工单，管理端总数 +1。
        能抓到：AI 回复了引导语但工单未落库（业务闭环断裂，用户以为已转人工）。
        """
        before = admin_client.get("/api/handover/list",
                                  params={"status": "PENDING", "page": 1, "pageSize": 1})
        assert_success(before, hint="[转人工-前置查询]")
        total_before = before.data.get("total", 0)

        sid = _session_id()
        reply = user_client.post("/api/ai/chat/sync",
                                 json_body={"message": "我要投诉，转人工", "sessionId": sid},
                                 timeout=config.AI_TIMEOUT)
        assert_success(reply, hint="[转人工-触发]")

        after = admin_client.get("/api/handover/list",
                                 params={"status": "PENDING", "page": 1, "pageSize": 50})
        assert_success(after, hint="[转人工-后置查询]")
        total_after = after.data.get("total", 0)
        assert total_after == total_before + 1, (
            f"[转人工] 期望 PENDING 工单数 {total_before} → {total_before + 1}，"
            f"实际 {total_after}（工单未生成或未落库）")

        # 注册清理：按 sessionId 定位本次工单
        for item in after.data.get("list", []):
            if item.get("sessionId") == sid:
                handover_cleanup(item["id"])
                break
        else:
            pytest.fail(f"[转人工] 未能在列表中找到 sessionId={sid} 的工单，无法清理")
