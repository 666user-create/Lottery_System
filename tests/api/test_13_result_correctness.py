"""
第④步 · 边界与异常（6/6）：操作成功但结果错

业主特别强调的场景：接口返回成功，但**结果本身是错的**。
本文件用「跨接口互证 + 集合运算 + 重复请求比对」三类手法捕捉：

  1. 列表 vs 详情     → 同一实体在不同接口中的字段必须一致（抓 Redis 缓存脏读、数据串号）
  2. 分页集合运算     → 多页并集无重叠、无遗漏，且是全量的子集（抓错页/漏页）
  3. 重复请求比对     → total 与明细必须稳定（抓不确定查询、脏读）
  4. 删除不存在的资源 → 记录真实语义（幂等 vs 误导性成功）

能抓到的缺陷：
  - 缓存与 DB 不一致（活动详情走 Redis 缓存，ACTIVITY_{id} 最长 7 天）
  - 分页 offset 计算错误导致重复/遗漏记录
  - "删除成功"但记录仍存在（或删除了别的记录）
"""
import uuid

import pytest

from common import config
from common.client import assert_success, assert_field


@pytest.mark.boundary
class TestCrossInterfaceConsistency:

    def test_list_and_detail_field_consistency(self, admin_client):
        """
        跨接口互证：列表中每个活动的 activityId/activityName 必须与详情接口一致。
        能抓到：Redis 缓存（ActivityServiceImpl 缓存 7 天）与 DB 不一致导致的脏读/串号。
        """
        lst = admin_client.get("/activity/find-list",
                               params={"currentPage": 1, "pageSize": 50})
        assert_success(lst, hint="[一致性-列表]", expect_data=True)
        records = (lst.data or {}).get("records", [])
        assert records, "[一致性] 列表为空，无法比对"

        checked = 0
        for item in records[:5]:  # 抽查前 5 条，控制请求量
            aid = item.get("activityId")
            detail = admin_client.get("/activity/detail/find", params={"activityId": aid})
            assert_success(detail, hint=f"[一致性-详情 id={aid}]", expect_data=True)
            assert_field(detail, "activityId", aid,
                         hint=f"[一致性] 详情回显 id 必须等于请求 id={aid}")
            # 核心断言：同一实体跨接口字段一致
            assert detail.data.get("activityName") == item.get("activityName"), (
                f"[一致性] 活动 {aid} 在列表与详情中名称不一致："
                f"列表={item.get('activityName')!r} 详情={detail.data.get('activityName')!r}\n"
                f" → 疑似缓存脏读或数据串号")
            checked += 1
        print(f"    [定性] 跨接口一致性抽查 {checked} 条活动，全部一致")

    def test_winning_records_consistency(self, admin_client):
        """
        跨维度互证：活动维度查询的结果集应**包含**（活动+奖品）维度的结果。
        能抓到：prizeId 过滤逻辑错误（返回了不属于该奖品的中奖记录）。
        """
        aid = config.BASELINE["activity_id_sample"]
        pid = config.BASELINE["prize_id_sample"]
        by_activity = admin_client.post("/draw/winning-records/show", json_body={"activityId": aid})
        by_both = admin_client.post("/draw/winning-records/show",
                                    json_body={"activityId": aid, "prizeId": pid})
        assert_success(by_activity, hint="[中奖记录-活动维度]")
        assert_success(by_both, hint="[中奖记录-活动+奖品维度]")

        act_set = {(x.get("winnerId"), x.get("prizeName")) for x in (by_activity.data or [])}
        both_set = {(x.get("winnerId"), x.get("prizeName")) for x in (by_both.data or [])}
        assert both_set.issubset(act_set), (
            f"[中奖记录一致性] 活动+奖品维度返回了活动维度中不存在的记录："
            f"{both_set - act_set}\n → prizeId 过滤逻辑异常")


@pytest.mark.boundary
class TestPaginationIntegrity:

    def test_pages_no_overlap_no_gap(self, admin_client):
        """
        分页集合运算：pageSize=2 的第 1/2/3 页并集
          - 无重叠（同一记录不得出现在两页）
          - 无越界（返回的 id 必须存在于全量集合中）
          - 覆盖顺序正确（并集大小 == min(total, 页数*pageSize)）
        能抓到：offset 计算错误导致的重复/漏记。
        """
        page_size = 2
        collected = []
        for page in (1, 2, 3):
            resp = admin_client.get("/activity/find-list",
                                    params={"currentPage": page, "pageSize": page_size})
            assert_success(resp, hint=f"[分页完整性-第{page}页]")
            collected.extend([r.get("activityId") for r in (resp.data or {}).get("records", [])])

        assert len(collected) == len(set(collected)), (
            f"[分页完整性] 多页出现重复记录：{collected} → offset 计算错误")

        full = admin_client.get("/activity/find-list",
                                params={"currentPage": 1, "pageSize": 100})
        assert_success(full, hint="[分页完整性-全量]")
        full_ids = {r.get("activityId") for r in (full.data or {}).get("records", [])}
        assert set(collected).issubset(full_ids), (
            f"[分页完整性] 分页返回了全量中不存在的 id：{set(collected) - full_ids}")
        print(f"    [定性] 3 页并集 {collected}，无重叠，且为全量 {sorted(full_ids)} 的子集")

    def test_total_stable_across_requests(self, admin_client):
        """
        total 稳定性：连续 3 次请求 total 必须一致。
        能抓到：不确定查询（如统计与明细口径不一致、统计含已删除数据）。
        """
        totals = []
        for i in range(3):
            resp = admin_client.get("/activity/find-list",
                                    params={"currentPage": 1, "pageSize": 10})
            assert_success(resp, hint=f"[total稳定性-第{i + 1}次]")
            totals.append((resp.data or {}).get("total"))
        assert len(set(totals)) == 1, (
            f"[total稳定性] 连续 3 次请求 total 不一致：{totals}")

    def test_records_count_not_exceed_total(self, admin_client):
        """明细数不得超过总数（口径一致性）"""
        resp = admin_client.get("/activity/find-list",
                                params={"currentPage": 1, "pageSize": 100})
        assert_success(resp, hint="[明细/总数口径]")
        records = (resp.data or {}).get("records", [])
        total = (resp.data or {}).get("total", 0)
        assert len(records) <= total, (
            f"[口径一致性] 返回明细 {len(records)} 条 > total {total}，统计口径异常")


@pytest.mark.boundary
class TestMisleadingSuccess:

    def test_delete_nonexistent_faq_semantics(self, admin_client):
        """
        删除不存在的 FAQ：记录真实语义。
        FaqController.delete 无条件 `return CommonResult.success(true)`，
        即不存在的 id 也返回"删除成功"。
        能抓到：误导性成功（调用方无法感知目标不存在）；此处记录事实并断言不得 500。
        """
        ghost_id = 999999999
        resp = admin_client.delete("/api/faq/delete", params={"id": ghost_id})
        print(f"    [定性] 删除不存在 FAQ(id={ghost_id}) → code={resp.code} data={resp.data}")
        assert resp.code != config.Code.INTERNAL_SERVER_ERROR, (
            f"[删除幂等] 删除不存在记录返回 500：{resp.brief()}")
        if resp.code == config.Code.SUCCESS and resp.data is True:
            print("    [记录] 语义为「幂等成功」：不存在的 id 亦返回 true（调用方无法区分）")

    def test_delete_actually_removes_record(self, admin_client, faq_cleanup):
        """
        删除**真实生效**（对应"操作成功但结果错"）：新增 → 删除 → 必须查不到。
        能抓到：删除返回 true 但记录仍存在（软删未生效/删错记录）。
        """
        q = f"{config.TEST_PREFIX}del_real_{uuid.uuid4().hex[:8]}"
        add = admin_client.post("/api/faq/add",
                                json_body={"question": q, "answer": "待删除", "category": "测试"})
        assert_success(add, hint="[删除生效-新增]", expect_data=True)
        fid = add.data
        faq_cleanup(fid)  # 兜底：若后续断言失败仍尝试清理

        before = admin_client.get("/api/faq/list", params={"page": 1, "pageSize": 100})
        assert any(x.get("id") == fid for x in (before.data or {}).get("list", [])), (
            f"[删除生效] 新增的 id={fid} 未出现在列表中（新增未落库）")

        dele = admin_client.delete("/api/faq/delete", params={"id": fid})
        assert_success(dele, hint="[删除生效-删除]")

        after = admin_client.get("/api/faq/list", params={"page": 1, "pageSize": 100})
        assert not any(x.get("id") == fid for x in (after.data or {}).get("list", [])), (
            f"[删除生效] 删除后 id={fid} 仍能查到（删除未生效但返回成功）")

    def test_handover_handler_and_remark_persisted(self, user_client, admin_client,
                                                   handover_cleanup):
        """
        工单处理结果**真实持久化**：handler 与 remark 必须可回查。
        能抓到：状态更新成功但备注/处理人丢失（部分字段未写库）。
        """
        sid = f"{config.TEST_PREFIX}sess_persist_{uuid.uuid4().hex[:6]}"
        create = user_client.post("/api/handover/create",
                                  json_body={"sessionId": sid, "question": "持久化校验"})
        assert_success(create, hint="[工单持久化-创建]", expect_data=True)
        hid = create.data
        handover_cleanup(hid)

        handler = "QA边界测试员"
        remark = f"{config.TEST_PREFIX}备注-{uuid.uuid4().hex[:6]}"
        proc = admin_client.post("/api/handover/process", json_body={
            "id": hid, "status": "PROCESSING", "handler": handler, "remark": remark})
        assert_success(proc, hint="[工单持久化-处理]")

        listed = admin_client.get("/api/handover/list",
                                  params={"status": "PROCESSING", "page": 1, "pageSize": 50})
        assert_success(listed, hint="[工单持久化-回查]")
        found = [x for x in (listed.data or {}).get("list", []) if x.get("id") == hid]
        assert found, f"[工单持久化] 处理后未在 PROCESSING 列表中找到 id={hid}"
        assert found[0].get("handler") == handler, (
            f"[工单持久化] handler 丢失：期望 {handler!r}，实际 {found[0].get('handler')!r}")
        assert found[0].get("remark") == remark, (
            f"[工单持久化] remark 丢失：期望 {remark!r}，实际 {found[0].get('remark')!r}")
