"""
第④步 · 边界与异常（5/6）：依赖异常

外部依赖现状（2026-10-11 更新）：
  RabbitMQ  **已恢复为本机实例**（localhost:5672，RabbitMQ 3.13.7 + Erlang/OTP 26）
            → 抽奖链路端到端可用；原 BUG-001「MQ 不可达导致阻塞 30s」已消除
            → 历史：旧配置指向阿里云 8.136.222.102，该实例已释放；后改为 VPC 私网地址亦不可达
  DeepSeek  api.deepseek.com    → 实测可用（第③步已验证）
  Embedding key 为空            → 关键词兜底（正常降级）
  Redis     localhost:8888      → 可用

本文件重点验证「依赖可用/不可用时接口的姿态」：
  - MQ 可用   → 抽奖应成功（code=200）且**不阻塞**（防 BUG-001 复发）
  - MQ 不可用 → 抽奖应**快速失败**（明确错误码，秒级返回），不得挂起 30s

能抓到的缺陷：
  - 依赖不可用导致请求长时间挂起（线程池耗尽风险）
  - 依赖异常未转换为明确业务错误码（客户端无法区分"系统忙"与"参数错"）
"""
import socket
import time

import pytest
import allure
import requests

from common import config
from common.client import assert_success

MQ_HOST = "127.0.0.1"
MQ_PORT = 5672


def _mq_reachable(timeout: float = 1.5) -> bool:
    """探测本机 MQ 端口是否可连（用于按实际环境选择断言分支）"""
    try:
        with socket.create_connection((MQ_HOST, MQ_PORT), timeout=timeout):
            return True
    except OSError:
        return False


@allure.feature("边界与异常")
@allure.story("MQ 依赖可用性行为")
@pytest.mark.boundary
class TestMqDependency:

    def test_draw_prize_succeeds_fast_when_mq_available(self, admin_client):
        """
        MQ 可用：POST /draw/prize 应**成功返回 200**，且**不得长时间阻塞**。

        背景（BUG-001 的回归防线，2026-10-11 改判）：
          原用例名为 test_draw_prize_fails_fast_when_mq_unreachable，判定逻辑是
          「≤5s 返回非 200 才算通过，否则 xfail」——那是 MQ 不可达时期的写法。
          MQ 恢复后抽奖能真成功（code=200），该判定会误报 xfail，故改为正向断言。

          保留的回归价值：**MQ 可用时必须秒级返回**。若将来又出现"依赖异常导致请求挂起"，
          本用例会因耗时超限而失败，同样能抓住 BUG-001 那类问题。

        MQ 确实不可达时（开发机没起 MQ）：本用例仍按"快速失败"判定，避免误报。
        """
        activity_id = config.BASELINE["activity_id_sample"]
        prize_id = config.BASELINE["prize_id_sample"]
        payload = {
            "activityId": activity_id, "prizeId": prize_id,
            "winningTime": time.strftime("%Y-%m-%dT%H:%M:%S"),
            "winnerList": [{"userId": 1, "userName": "QA边界测试"}],
        }
        mq_up = _mq_reachable()
        start = time.time()
        timed_out = False
        resp = None
        try:
            resp = admin_client.post("/draw/prize", json_body=payload,
                                     timeout=config.MQ_TIMEOUT)
        except requests.exceptions.Timeout:
            timed_out = True
        elapsed = time.time() - start

        print(f"    [定性] MQ{'可用' if mq_up else '不可达'} → 抽奖耗时 {elapsed:.1f}s, "
              f"{'客户端超时' if timed_out else f'HTTP={resp.status} code={resp.code}'}")

        assert not timed_out, (
            f"抽奖请求挂起至客户端超时（{elapsed:.1f}s）—— 依赖异常不应阻塞请求线程")
        assert elapsed <= 5.0, (
            f"[防阻塞] 抽奖请求耗时 {elapsed:.1f}s > 5s，疑似依赖异常时未快速失败（BUG-001 类问题）")

        if mq_up:
            assert resp.code == config.Code.SUCCESS, (
                f"[MQ 可用] 抽奖应成功返回 code=200，实际 {resp.brief()}")
        else:
            assert resp.code != config.Code.SUCCESS, (
                f"[MQ 不可达] 抽奖应返回明确业务错误码而非假成功，实际 {resp.brief()}")

    def test_draw_prize_requires_login(self, anon_client):
        """对照组：未登录调用抽奖 → 401（鉴权先于 MQ 依赖，不应受 MQ 影响）"""
        resp = anon_client.post("/draw/prize", json_body={
            "activityId": 34, "prizeId": 25,
            "winningTime": time.strftime("%Y-%m-%dT%H:%M:%S"),
            "winnerList": [{"userId": 1, "userName": "x"}]}, timeout=config.HTTP_TIMEOUT)
        assert resp.status == 401, (
            f"[抽奖-未登录] 期望 401（拦截器先于 MQ 调用），实际 {resp.brief()}")


@allure.feature("边界与异常")
@allure.story("外部依赖降级")
@pytest.mark.boundary
class TestDegradation:

    def test_embedding_key_absent_degrades_to_keyword(self, admin_client, faq_cleanup):
        """
        依赖降级：Embedding key 为空（application.properties:81）时，
        FAQ 新增应**仍成功**（向量生成失败不阻断），检索走关键词兜底。
        能抓到：向量依赖故障导致 FAQ 新增整体失败（过度耦合外部依赖）。
        """
        import uuid
        q = f"{config.TEST_PREFIX}降级测试抽奖规则_{uuid.uuid4().hex[:6]}"
        resp = admin_client.post("/api/faq/add",
                                 json_body={"question": q, "answer": "降级答案", "category": "测试"})
        print(f"    [定性] key 为空时 FAQ 新增 → code={resp.code}")
        assert_success(resp, hint="[Embedding 降级] FAQ 新增不应因向量失败而失败",
                       expect_data=True)
        if resp.data:
            faq_cleanup(resp.data)

        # 关键词兜底仍应能召回（RAG 链路降级可用性）
        chat = admin_client.post("/api/ai/chat/sync",
                                 json_body={"message": "降级测试抽奖规则", "sessionId": "qa_auto_degrade"},
                                 timeout=config.AI_TIMEOUT)
        assert_success(chat, hint="[RAG 降级] 关键词兜底链路应可用")
        print(f"    [定性] 兜底问答回复前 60 字: {str(chat.data)[:60]!r}")

    def test_ai_dependency_error_is_graceful(self, anon_client):
        """
        依赖异常姿态：向 AI 接口发送超长/异常输入，应返回可读文案而非 500 堆栈。
        能抓到：外部 API 异常直穿为 500 + 内部细节。
        """
        resp = anon_client.post("/api/ai/chat/sync",
                                json_body={"message": "\u0000\u0001\u0002 非法控制字符测试"},
                                timeout=config.AI_TIMEOUT)
        print(f"    [定性] AI 非法字符输入 → HTTP={resp.status} code={resp.code}")
        assert resp.code != config.Code.INTERNAL_SERVER_ERROR, (
            f"[AI 依赖异常] 返回 500：{resp.brief()}")
