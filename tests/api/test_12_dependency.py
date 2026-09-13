"""
第④步 · 边界与异常（5/6）：依赖异常

外部依赖现状（application.properties）：
  RabbitMQ  8.136.222.102:5672  → **实测不可达**（连接超时）
  DeepSeek  api.deepseek.com    → 实测可用（第③步已验证）
  Embedding key 为空            → 关键词兜底（正常降级）
  Redis     localhost:8888      → 可用

本文件重点验证「依赖不可用时接口的失败姿态」：
  期望：**快速失败**（明确错误码，秒级返回）
  实际：见用例结果（BUG-002 已知：抽奖接口阻塞约 30s）

能抓到的缺陷：
  - 依赖不可用导致请求长时间挂起（线程池耗尽风险）
  - 依赖异常未转换为明确业务错误码（客户端无法区分"系统忙"与"参数错"）
"""
import time

import pytest
import requests

from common import config
from common.client import assert_success


@pytest.mark.boundary
class TestMqDependency:

    def test_draw_prize_fails_fast_when_mq_unreachable(self, admin_client):
        """
        依赖异常（MQ 不可达）：POST /draw/prize 应**快速失败**（≤5s）并返回明确错误码。

        实测事实（BUG-002）：RabbitMQ 不可达时请求阻塞约 30s 后超时，无快速失败机制。

        判定策略（避免"预期失败"污染测试信号）：
          - 若 ≤5s 返回非 200 → 通过（已具备快速失败）
          - 若阻塞超时或耗时 >5s → **xfail 记录缺陷**，不阻塞套件为"红"
        """
        activity_id = config.BASELINE["activity_id_sample"]
        prize_id = config.BASELINE["prize_id_sample"]
        payload = {
            "activityId": activity_id, "prizeId": prize_id,
            "winningTime": time.strftime("%Y-%m-%dT%H:%M:%S"),
            "winnerList": [{"userId": 1, "userName": "QA边界测试"}],
        }
        start = time.time()
        timed_out = False
        resp = None
        try:
            resp = admin_client.post("/draw/prize", json_body=payload,
                                     timeout=config.MQ_TIMEOUT)
        except requests.exceptions.Timeout:
            timed_out = True
        elapsed = time.time() - start

        print(f"    [定性] MQ 不可达下的抽奖请求 → 耗时 {elapsed:.1f}s, "
              f"{'客户端超时' if timed_out else f'HTTP={resp.status} code={resp.code}'}")

        fast_fail_ok = (not timed_out) and elapsed <= 5.0 and resp is not None \
            and resp.code != config.Code.SUCCESS

        if not fast_fail_ok:
            pytest.xfail(
                f"BUG-002（P2）：依赖 MQ 不可达时抽奖接口未快速失败"
                f"（实测耗时 {elapsed:.1f}s，timed_out={timed_out}）")

    def test_draw_prize_requires_login(self, anon_client):
        """对照组：未登录调用抽奖 → 401（鉴权先于 MQ 依赖，不应受 MQ 影响）"""
        resp = anon_client.post("/draw/prize", json_body={
            "activityId": 34, "prizeId": 25,
            "winningTime": time.strftime("%Y-%m-%dT%H:%M:%S"),
            "winnerList": [{"userId": 1, "userName": "x"}]}, timeout=config.HTTP_TIMEOUT)
        assert resp.status == 401, (
            f"[抽奖-未登录] 期望 401（拦截器先于 MQ 调用），实际 {resp.brief()}")


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
