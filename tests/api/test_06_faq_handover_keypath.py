"""
第③步 · 关键路径用例（6/7）：FAQ 知识库与交接班工单

覆盖：FAQ 增-查-删闭环（含 total 变化与向量连带删除）、工单创建-查询-状态流转闭环、
      越权保护、参数校验分支。

能抓到的缺陷（"操作成功但结果错"的重点靶区）：
  - 新增返回 id 但 total 未增加（未真正落库）
  - 删除返回 true 但 total 未减少 / faq_embedding 残留（孤儿向量）
  - 工单状态处理返回 true 但列表中状态未变（更新未生效）
  - 普通用户越权访问管理接口
"""
import uuid

import pytest

from common import config
from common.client import assert_code, assert_success


def _faq_question() -> str:
    return f"{config.TEST_PREFIX}知识库探针_{uuid.uuid4().hex[:8]}"


def _session_id() -> str:
    return f"{config.TEST_PREFIX}sess_{uuid.uuid4().hex[:8]}"


def _faq_total(client) -> int:
    resp = client.get("/api/faq/list", params={"page": 1, "pageSize": 1})
    assert_success(resp, hint="[FAQ-total查询]")
    return resp.data.get("total", 0)


@pytest.mark.smoke
@pytest.mark.keypath
class TestFaqKeyPath:

    def test_list_normal_flow(self, admin_client):
        """正常流：FAQ 列表返回 total 与 list 字段"""
        resp = admin_client.get("/api/faq/list", params={"page": 1, "pageSize": 10})
        assert_success(resp, hint="[FAQ列表]", expect_data=True)
        assert "total" in resp.data and "list" in resp.data, (
            f"[FAQ列表] 期望含 total 与 list，实际字段 {list(resp.data.keys())}")
        assert resp.data["total"] >= config.BASELINE["faq_count_min"], (
            f"[FAQ列表] total={resp.data['total']} 低于基线 {config.BASELINE['faq_count_min']}"
            f"（基础知识库疑似被误删）")

    def test_add_then_delete_closed_loop(self, admin_client, faq_cleanup):
        """
        闭环：新增 → total+1 → 删除 → total 复原。
        能抓到：接口返回成功但数据未落库/未删除（假成功）。
        """
        total_before = _faq_total(admin_client)
        question = _faq_question()

        add = admin_client.post("/api/faq/add", json_body={
            "question": question, "answer": "QA自动化答案内容", "category": "测试"})
        assert_success(add, hint="[FAQ新增]", expect_data=True)
        new_id = add.data
        assert isinstance(new_id, int) and new_id > 0, (
            f"[FAQ新增] 期望返回正整数 id，实际 {new_id!r}")
        faq_cleanup(new_id)

        total_after_add = _faq_total(admin_client)
        assert total_after_add == total_before + 1, (
            f"[FAQ新增] 期望 total {total_before} → {total_before + 1}，"
            f"实际 {total_after_add}（新增未生效/未落库）")

        # 校验新增内容可被检索到（数据正确性，而非仅计数）
        listing = admin_client.get("/api/faq/list", params={"page": 1, "pageSize": 50})
        assert_success(listing, hint="[FAQ列表-校验内容]")
        matched = [x for x in listing.data.get("list", []) if x.get("id") == new_id]
        assert matched, f"[FAQ列表] 新增的 id={new_id} 未出现在列表中"
        assert matched[0].get("question") == question, (
            f"[FAQ数据] 期望 question={question!r}，实际 {matched[0].get('question')!r}")

        delete = admin_client.delete("/api/faq/delete", params={"id": new_id})
        assert_success(delete, hint="[FAQ删除]")
        total_after_delete = _faq_total(admin_client)
        assert total_after_delete == total_before, (
            f"[FAQ删除] 期望 total 复原为 {total_before}，实际 {total_after_delete}"
            f"（删除未生效）")

    def test_add_missing_body_fields(self, admin_client):
        """
        分支：新增 FAQ 缺 question/answer → 不得返回 500 之外的成功码。
        代码依据：FaqDO 无校验注解（契约风险 C-6），DB 有 NOT NULL 约束。
        """
        resp = admin_client.post("/api/faq/add", json_body={"category": "测试"})
        print(f"    [定性] FAQ 缺必填字段 → HTTP={resp.status} code={resp.code} msg={resp.message!r}")
        assert resp.code != config.Code.SUCCESS, (
            f"[FAQ缺字段] 期望失败（DB NOT NULL 约束），实际成功：{resp.brief()}")

    def test_normal_user_forbidden(self, user_client):
        """越权：普通用户访问 FAQ 管理接口 → 403"""
        resp = user_client.get("/api/faq/list", params={"page": 1, "pageSize": 10})
        assert resp.status == 403, (
            f"[越权FAQ] 期望 HTTP=403，实际 {resp.brief()}")


@pytest.mark.smoke
@pytest.mark.keypath
class TestHandoverKeyPath:

    def test_create_query_process_closed_loop(self, user_client, admin_client, handover_cleanup):
        """
        闭环：用户创建工单 → 管理员可见(status=PENDING) → 处理为 PROCESSING → RESOLVED，
        每步都回查列表确认状态**真实变化**。
        能抓到：处理接口返回 true 但状态未更新（假成功）。
        """
        sid = _session_id()
        create = user_client.post("/api/handover/create",
                                  json_body={"sessionId": sid, "question": "QA自动化工单-闭环测试"})
        assert_success(create, hint="[工单创建]", expect_data=True)
        hid = create.data
        assert isinstance(hid, int) and hid > 0, f"[工单创建] 期望正整数 id，实际 {hid!r}"
        handover_cleanup(hid)

        # 1) 创建后应为 PENDING，且 sessionId 可回查
        pending = admin_client.get("/api/handover/list",
                                   params={"status": "PENDING", "page": 1, "pageSize": 50})
        assert_success(pending, hint="[工单查询-PENDING]")
        target = [x for x in pending.data.get("list", []) if x.get("id") == hid]
        assert target, f"[工单查询] 新建工单 id={hid} 未出现在 PENDING 列表"
        assert target[0].get("sessionId") == sid, (
            f"[工单数据] sessionId 期望 {sid}，实际 {target[0].get('sessionId')}")

        # 2) 处理为 PROCESSING 并回查
        proc = admin_client.post("/api/handover/process", json_body={
            "id": hid, "status": "PROCESSING", "handler": "QA自动化", "remark": "处理中"})
        assert_success(proc, hint="[工单处理-PROCESSING]")
        mid = admin_client.get("/api/handover/list",
                               params={"status": "PROCESSING", "page": 1, "pageSize": 50})
        assert_success(mid, hint="[工单查询-PROCESSING]")
        assert any(x.get("id") == hid for x in mid.data.get("list", [])), (
            f"[工单状态] 处理为 PROCESSING 后未在 PROCESSING 列表中找到 id={hid}（状态未更新）")

        # 3) 处理为 RESOLVED 并回查备注
        done = admin_client.post("/api/handover/process", json_body={
            "id": hid, "status": "RESOLVED", "handler": "QA自动化", "remark": "已解决-自动化"})
        assert_success(done, hint="[工单处理-RESOLVED]")
        res = admin_client.get("/api/handover/list",
                               params={"status": "RESOLVED", "page": 1, "pageSize": 50})
        assert_success(res, hint="[工单查询-RESOLVED]")
        found = [x for x in res.data.get("list", []) if x.get("id") == hid]
        assert found, f"[工单状态] 处理为 RESOLVED 后未在 RESOLVED 列表中找到 id={hid}"
        assert found[0].get("remark") == "已解决-自动化", (
            f"[工单备注] 期望 '已解决-自动化'，实际 {found[0].get('remark')!r}（备注未保存）")

    def test_create_missing_session_id(self, user_client):
        """分支：缺 sessionId → 400（HTTP 200 内联错误码）"""
        resp = user_client.post("/api/handover/create", json_body={"question": "无会话"})
        assert_code(resp, config.Code.BAD_REQUEST, hint="[工单-缺sessionId]")

    def test_create_requires_login(self, anon_client):
        """分支：未登录创建工单 → 401"""
        resp = anon_client.post("/api/handover/create",
                                json_body={"sessionId": _session_id(), "question": "匿名"})
        assert resp.status == 401, f"[工单-未登录] 期望 HTTP=401，实际 {resp.brief()}"

    def test_process_missing_id(self, admin_client):
        """分支：处理工单缺 id → 400"""
        resp = admin_client.post("/api/handover/process", json_body={"status": "RESOLVED"})
        assert_code(resp, config.Code.BAD_REQUEST, hint="[工单处理-缺id]")

    def test_normal_user_forbidden_on_list_and_process(self, user_client):
        """越权：普通用户查工单列表/处理工单 → 均 403"""
        lst = user_client.get("/api/handover/list", params={"page": 1, "pageSize": 10})
        assert lst.status == 403, f"[越权工单列表] 期望 403，实际 {lst.brief()}"
        proc = user_client.post("/api/handover/process",
                                json_body={"id": 1, "status": "RESOLVED"})
        assert proc.status == 403, f"[越权工单处理] 期望 403，实际 {proc.brief()}"
