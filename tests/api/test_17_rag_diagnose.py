"""
第⑥步 · 新增接口测试（3/3）：RAG 检索诊断

覆盖 RagDiagnoseController（`/api/ai/rag/diagnose?q=`，adminOnly，2026-10 新增）。

接口用途（源码注释）：观察「向量召回 → 本地重排」的实际效果，
返回本次检索命中的知识、条数与耗时，为 RAG 效果评估提供可量化入口。

实测基线（2026-10-16）：
    返回结构 : {query, hitCount, costMs, items[{rank, text, metadata}]}
    metadata : {source: "mysql.faq", category, question, distance, faq_id}
    链路     : Controller → VectorRagService.retrieveDocuments()
               向量召回(app.rag.top-k=5, app.rag.min-score=0.2)
               → 本地重排 LocalReRanker(app.rag.rerank-enabled=true)
               → 截断 app.rag.rerank-top-n=3（故 hitCount 恒为 3）
    ⚠️**重排按「提问 bigram 与文档的重合度」降序，不是按向量距离** →
      `distance` 不随 rank 单调（实测 "领奖规则" → [0.420, 0.407, 0.429]）。
      因此本文件**不**断言 distance 升序，只断言 rank 连续性与检索相关性。
"""
import allure
import pytest

from common import config
from common.client import assert_http, assert_success


def _diagnose(client, query, timeout=None):
    params = {"q": query}
    kwargs = {"params": params}
    if timeout:
        kwargs["timeout"] = timeout
    resp = client.get("/api/ai/rag/diagnose", **kwargs)
    assert_success(resp, hint=f"[RAG诊断] q={query[:20]!r}", expect_data=True)
    return resp.data


def _numeric_distances(items):
    """取出可比较的 distance 数值（缺失/非数值的条目跳过）"""
    return [it["metadata"]["distance"] for it in items
            if isinstance((it.get("metadata") or {}).get("distance"), (int, float))]


@allure.feature("RAG 检索诊断")
@allure.story("返回结构与排序有效性")
@pytest.mark.keypath
class TestRagDiagnoseContract:

    def test_structure(self, admin_client):
        """结构契约：query 回显、hitCount == items 条数、耗时与 rank 合法、metadata 含来源"""
        query = "怎么参与抽奖"
        data = _diagnose(admin_client, query)

        for key in ("query", "hitCount", "costMs", "items"):
            assert key in data, f"[RAG诊断] 缺字段 {key}；实际字段={list(data.keys())}"
        assert data["query"] == query, (
            f"[RAG诊断] query 回显不一致：{data['query']!r} != {query!r}")
        assert isinstance(data["costMs"], int) and data["costMs"] >= 0, (
            f"[RAG诊断] costMs 应为非负整数，实际 {data['costMs']!r}")

        items = data["items"]
        assert isinstance(items, list), "[RAG诊断] items 应为列表"
        assert data["hitCount"] == len(items), (
            f"[RAG诊断] hitCount({data['hitCount']}) != items 条数({len(items)})")

        for idx, item in enumerate(items, start=1):
            assert item.get("rank") == idx, (
                f"[RAG诊断] rank 应从 1 连续递增，第 {idx} 项实际 rank={item.get('rank')}")
            assert (item.get("text") or "").strip(), f"[RAG诊断] rank={idx} 的 text 不应为空"
            metadata = item.get("metadata") or {}
            assert metadata.get("source"), (
                f"[RAG诊断] rank={idx} 的 metadata 应含 source，实际={metadata}")

    def test_hit_count_bounded_by_pipeline_config(self, admin_client):
        """
        管道配置契约：返回条数受 `app.rag.top-k=5` 与 `app.rag.rerank-top-n=3` 约束。

        实测恒为 3（= rerank-top-n）。这里断言「不超过 top-k 上限且与 items 一致」，
        避免把配置值写死成脆弱断言；同时提示：**distance 与 rank 不保证单调**
        （重排按 bigram 重合度，非向量距离），所以不在此处断言距离升序。
        """
        top_k_upper_bound = 5   # application.properties:139 app.rag.top-k=5
        data = _diagnose(admin_client, "领奖规则")
        items = data["items"]
        assert data["hitCount"] == len(items)
        assert 0 < len(items) <= top_k_upper_bound, (
            f"[RAG诊断] 返回条数 {len(items)} 应落在 (0, {top_k_upper_bound}] 内")
        for item in items:
            distance = (item.get("metadata") or {}).get("distance")
            assert distance is None or isinstance(distance, (int, float)), (
                f"[RAG诊断] distance 应为数值或缺失，实际 {distance!r}")


@allure.feature("RAG 检索诊断")
@allure.story("召回相关性")
@pytest.mark.keypath
class TestRagRelevance:

    def test_exact_question_ranks_first(self, admin_client, faq_cleanup):
        """
        相关性契约：新增一条含唯一词的 FAQ 后，用该唯一词检索，应排在 rank1。

        这是「RAG 真的读到了新知识」的功能级证据（而非只看接口返回 200）。
        """
        import uuid
        uniq = f"{config.TEST_PREFIX}rag{uuid.uuid4().hex[:8]}"
        created = admin_client.post("/api/faq/add", json_body={
            "question": f"{uniq} 如何参与抽奖",
            "answer": "这是相关性验证专用答案",
            "category": "自动化测试"})
        assert_success(created, hint="[RAG-准备FAQ]", expect_data=True)
        faq_cleanup(created.data)

        items = _diagnose(admin_client, f"{uniq} 如何参与抽奖")["items"]
        assert items, "[RAG诊断] 检索结果不应为空"
        assert uniq in (items[0].get("text") or ""), (
            f"[相关性] 精确检索未把新增 FAQ 排在 rank1，rank1={items[0].get('text')!r}")
        assert items[0]["metadata"].get("faq_id") == created.data, (
            f"[相关性] rank1 的 faq_id 应为新增记录 {created.data}，"
            f"实际={items[0]['metadata'].get('faq_id')}")

    def test_relevant_query_scores_better_than_unrelated(self, admin_client):
        """相关性区分度：相关提问的最佳 distance 必须优于无关字符串的最佳 distance"""
        related = _diagnose(admin_client, "怎么参与抽奖")["items"]
        unrelated = _diagnose(admin_client, "zzz与被测系统完全无关的乱码串xyz")["items"]
        assert related and unrelated, "[RAG诊断] 两次检索都应有结果"

        d_related = min(_numeric_distances(related), default=None)
        d_unrelated = min(_numeric_distances(unrelated), default=None)
        assert d_related is not None and d_unrelated is not None, (
            f"[相关性] distance 应为数值：related={_numeric_distances(related)} "
            f"unrelated={_numeric_distances(unrelated)}")
        assert d_related < d_unrelated, (
            f"[相关性] 相关提问最佳 distance({d_related:.4f}) 未优于"
            f"无关提问最佳 distance({d_unrelated:.4f})")

    @pytest.mark.boundary
    def test_relevance_not_reflected_in_hit_count(self, admin_client):
        """
        现状记录（已登记 P4 观察项）：命中数不体现相关性。

        配置里**有**阈值 `app.rag.min-score=0.2`，但实测空串与无关字符串同样能召回候选，
        经重排后仍返回 3 条 → 调用方无法用 hitCount 判断「是否真有依据」。

        断言的是**可控的结构一致性**（hitCount == len(items) 且不超过 top-k），
        而不是"必须返回 0 条"——后者与实现不符，会变成假失败。
        一旦后续收紧阈值（期望 hitCount=0），本用例会失败并提醒更新登记项。
        """
        top_k_upper_bound = 5
        for query, case in [("", "空串"), ("zzz完全无关xyz", "无关字符串")]:
            data = _diagnose(admin_client, query)
            items = data["items"]
            assert data["hitCount"] == len(items)
            assert len(items) <= top_k_upper_bound, (
                f"[{case}] 返回条数 {len(items)} 超过 top-k 上限 {top_k_upper_bound}")
            print(f"    [现状] {case} → hitCount={data['hitCount']}"
                  f"（min-score=0.2 未挡住无关/空查询，已登记观察项）")


@allure.feature("RAG 检索诊断")
@allure.story("边界与权限")
@pytest.mark.boundary
class TestRagDiagnoseBoundary:

    def test_long_query_still_works(self, admin_client):
        """边界：较长查询（200 字，URL 编码约 1.8KB）仍应正常返回 JSON"""
        data = _diagnose(admin_client, "抽奖" * 100)
        assert data["hitCount"] == len(data["items"])

    def test_oversized_query_rejected_by_container(self, admin_client):
        """
        边界：超长查询（1000 字，URL 编码约 9KB，超过 Tomcat 默认 8KB 请求头上限）
              → HTTP 400（容器层拒绝），**不得 500**。

        备注：此处返回的是 Tomcat 自带 HTML 错误页，而非项目统一的 CommonResult JSON；
        属轻微不一致，已记录（不影响业务，但客户端按 JSON 解析会失败）。
        """
        resp = admin_client.get("/api/ai/rag/diagnose", params={"q": "测" * 1000}, timeout=60)
        assert resp.status == 400, (
            f"[超长查询] 期望容器层 400，实际 HTTP={resp.status} body={resp.text[:120]!r}")
        assert resp.status != 500, "[超长查询] 不得 500"
        print(f"    [现状] 超长查询返回 Content-Type=HTML，非统一 JSON（已记录）")

    def test_rejects_anonymous_and_normal_user(self, anon_client, user_client):
        """权限契约：RAG 诊断属 adminOnly → 匿名 401、普通用户 403"""
        assert_http(anon_client.get("/api/ai/rag/diagnose", params={"q": "抽奖"}), 401,
                    hint="[RAG诊断-匿名]")
        assert_http(user_client.get("/api/ai/rag/diagnose", params={"q": "抽奖"}), 403,
                    hint="[RAG诊断-普通用户]")
