"""
第③步 · 关键路径用例（18/18）：存量缺口补齐（2026-10-20）

背景：源码全量核对（45 端点 / 11 控制器）发现 15 个端点早于/晚于本套件建立时加入，
接口层零覆盖。本文件按「端点 × 正向行为 + 鉴权 + 分支」补齐三组：

  A. 中奖履约 4 个：/api/winning/claim、/api/winning/confirm、
     /api/winning/manage/list、/api/winning/manage/status
  B. 账号安全 7 个：/user/account/security、/user/security/verification-code/send、
     /user/receiver-info（GET/POST）、/user/email/update、/user/password/update、
     /user/status/update
  C. 管理收口 4 个：/activity/end、/activity/delete、/prize/delete、/api/handover/delete

全部正向链路只在 qa_auto_ 一次性数据上执行，链路自清理：
  奖品 → 活动 → 抽奖(MQ 落账) → 领奖闭环 → end/delete(级联) → 删奖品。

断言强度说明（每条都能失败，否则不算测过）：
  - 领奖正向走**完整状态机** NOT_CLAIMED→CLAIMED→SHIPPED→COMPLETED，每步断言真实状态回读；
  - 业务失败断 body.code（规则 A，HTTP 仍 200），拦截器失败断真 HTTP 状态码（规则 B）；
  - 停用账号后断**旧 token 立即失效**（LoginInterceptor 每请求回库验状态）；
  - 改邮箱/改密码断「新凭据可登录 + 旧凭据被拒」的服务端反证，不断言"接口返回 200"了事。
"""
import json
import time
import uuid

import pytest
import allure

from common import config
from common.client import ApiClient, assert_code, assert_success

# 真实 1x1 PNG（prizePic 为 @RequestPart 必填，伪造字节可能被后端校验拒绝）
PNG_1X1 = (b"\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01\x00\x00\x00\x01"
           b"\x08\x06\x00\x00\x00\x1f\x15\xc4\x89\x00\x00\x00\nIDATx\x9cc\x00\x01"
           b"\x00\x00\x05\x00\x01\r\n-\xb4\x00\x00\x00\x00IEND\xaeB`\x82")


def _rand_mail(tag):
    return f"qa_auto_{tag}{uuid.uuid4().hex[:6]}@test.com"


def _rand_phone():
    return "137" + ("%.8d" % (uuid.uuid4().int % 10 ** 8))


def _register_login(anon_client, mail, phone, name="QA存量补齐"):
    """注册 + 登录一次性账号（随机邮箱/手机号，避免与历史残留冲突）"""
    reg = anon_client.post("/user/register", json_body={
        "name": name, "mail": mail, "phoneNumber": phone,
        "password": "123456", "identity": "NORMAL"})
    assert_code(reg, config.Code.SUCCESS, hint=f"[一次性账号注册] {mail}")
    login = anon_client.post("/user/password/login", json_body={
        "loginName": mail, "password": "123456", "mandatoryIdentity": "NORMAL"})
    assert_code(login, config.Code.SUCCESS, hint=f"[一次性账号登录] {mail}")
    return login.data["token"]



@allure.feature("中奖履约")
@allure.story("claim / confirm / manage 全状态机（存量缺口补齐）")
@pytest.mark.keypath
class TestClaimLifecycle:
    """A 组：中奖履约 4 端点。正向链路依赖 MQ（与 test_12 同一前提）。"""

    # ---------- A 组：独立分支/鉴权（不依赖链路数据）；正向闭环见 chain fixture 用例 ----------

    def test_claim_missing_record_id(self, user_client):
        """分支：申请领奖缺 recordId → 400（规则 A）"""
        resp = user_client.post("/api/winning/claim", json_body={})
        assert resp.status == 200, f"[claim 缺参] 期望 HTTP=200，实际 {resp.brief()}"
        assert_code(resp, config.Code.BAD_REQUEST, hint="[claim 缺参]")
        assert "recordId" in (resp.message or ""), (
            f"[claim 缺参] 文案应指明 recordId，实际 {resp.message!r}")

    def test_claim_nonexistent_record(self, user_client):
        """分支：记录不存在 → 601 中奖记录不存在（而非 200+null 假成功）"""
        resp = user_client.post("/api/winning/claim", json_body={"recordId": 99999999})
        assert resp.status == 200, f"[claim 不存在] 期望 HTTP=200，实际 {resp.brief()}"
        assert_code(resp, 601, hint="[claim 不存在] 期望 601")
        assert resp.data is None, f"[claim 不存在] 期望 data=null，实际 {resp.data!r}"

    def test_claim_requires_login(self, anon_client):
        """鉴权：匿名申请领奖 → 真 HTTP 401（拦截器先于业务拒绝）"""
        resp = anon_client.post("/api/winning/claim", json_body={"recordId": 1})
        assert resp.status == 401, f"[claim 匿名] 期望 HTTP=401，实际 {resp.brief()}"
        assert_code(resp, config.Code.UNAUTHORIZED, hint="[claim 匿名]")

    def test_confirm_missing_record_id(self, user_client):
        """分支：确认收货缺 recordId → 400"""
        resp = user_client.post("/api/winning/confirm", json_body={})
        assert resp.status == 200, f"[confirm 缺参] 期望 HTTP=200，实际 {resp.brief()}"
        assert_code(resp, config.Code.BAD_REQUEST, hint="[confirm 缺参]")

    def test_manage_status_missing_fields(self, admin_client):
        """分支：发货状态接口缺参 → 400（recordId 与 claimStatus 都必填）"""
        resp = admin_client.post("/api/winning/manage/status", json_body={})
        assert resp.status == 200, f"[status 缺参] 期望 HTTP=200，实际 {resp.brief()}"
        assert_code(resp, config.Code.BAD_REQUEST, hint="[status 缺参]")

    def test_manage_list_shape_and_paging(self, admin_client):
        """正向（读）：领奖工单分页列表结构齐全、分页参数真实生效"""
        p1 = admin_client.get("/api/winning/manage/list",
                              params={"claimStatus": "", "page": 1, "pageSize": 5})
        assert_success(p1, hint="[manage/list 第1页]", expect_data=True)
        assert "list" in p1.data and "total" in p1.data, (
            f"[manage/list] data 应含 list/total，实际 {list(p1.data)}")
        assert isinstance(p1.data["total"], int) and p1.data["total"] > 0, (
            f"[manage/list] total 应为正整数，实际 {p1.data['total']!r}")
        rows = p1.data["list"]
        if rows:
            item = rows[0]
            for key in ("recordId", "winnerName", "activityName", "prizeName",
                        "claimStatus", "claimStatusText"):
                assert key in item, (
                    f"[manage/list] 列表项缺字段 {key}，实际 {sorted(item)}")
            # 中文映射必须随状态给出（前端直接渲染 claimStatusText）
            assert item["claimStatusText"], f"[manage/list] claimStatusText 为空：{item}"

        p2 = admin_client.get("/api/winning/manage/list",
                              params={"claimStatus": "", "page": 2, "pageSize": 5})
        assert_success(p2, hint="[manage/list 第2页]", expect_data=True)
        ids1 = [r["recordId"] for r in p1.data["list"]]
        ids2 = [r["recordId"] for r in p2.data["list"]]
        assert ids1 != ids2 or len(ids1) == 0, (
            f"[manage/list] 第1/2 页完全相同，分页参数疑似未生效：{ids1}")

    @pytest.fixture(scope="class")
    def chain(self, admin_client, user_client):
        """建 奖品→活动→抽奖→等 MQ 落账；返回 (prizeId, activityId, activityName, recordId)。

        teardown 只做 best-effort 清理（即便用例中途失败也不留 qa_auto_ 链路数据）。
        匹配中奖记录用 activityName（my-records 明细**没有** activityId 字段——
        2026-10-20 探针实测教训）。
        """
        suffix = uuid.uuid4().hex[:8]
        prize_name = f"qa_auto_gap_prize_{suffix}"
        act_name = f"qa_auto_gap_act_{suffix}"

        me = user_client.get("/user/me")
        assert_success(me, hint="[chain] 取中奖候选人", expect_data=True)
        uid, uname = me.data["userId"], me.data["userName"]

        prize = admin_client.post_multipart(
            "/prize/create",
            files={"prizePic": ("qa.png", PNG_1X1, "image/png")},
            data={"param": json.dumps({"prizeName": prize_name,
                                       "description": "qa_auto 存量补齐链路",
                                       "price": 1}, ensure_ascii=False)})
        assert_success(prize, hint="[chain] 创建奖品", expect_data=True)
        prize_id = prize.data

        act = admin_client.post("/activity/create", json_body={
            "activityName": act_name,
            "description": "qa_auto 存量补齐链路",
            # prizeTier 实测：不传 → 服务端 500 系统异常（2026-10-20 探针），必须带上
            "activityPrizeList": [{"prizeId": prize_id, "prizeAmount": 1,
                                   "prizeTier": "FIRST_PRIZE"}],
            "activityUserList": [{"userId": uid, "userName": uname}]})
        assert_success(act, hint="[chain] 创建活动", expect_data=True)
        activity_id = act.data["activityId"]

        draw = admin_client.post("/draw/prize", timeout=35, json_body={
            "activityId": activity_id, "prizeId": prize_id,
            "winningTime": time.strftime("%Y-%m-%dT%H:%M:%S"),
            "winnerList": [{"userId": uid, "userName": uname}]})
        assert_success(draw, hint="[chain] 抽奖投递 MQ")

        record_id = None
        for _ in range(25):
            time.sleep(1)
            mine = user_client.get("/draw/my-records")
            if mine.code == 200 and isinstance(mine.data, list):
                hit = next((m for m in mine.data
                            if m.get("activityName") == act_name), None)
                if hit:
                    record_id = hit.get("recordId")
                    break
        assert record_id, (
            "[chain] 25s 内未等到 MQ 落账的中奖记录（my-records 按 activityName 匹配）"
            " → 抽奖链路或 MQ 异常，先查 test_12 前提是否成立")

        yield {"prize_id": prize_id, "activity_id": activity_id,
               "activity_name": act_name, "record_id": record_id}

        # best-effort 自清理：删活动（级联中奖记录/关联）→ 删奖品（须在活动删除之后，否则 302）
        try:
            admin_client.delete("/activity/delete",
                                params={"activityId": activity_id})
            admin_client.delete("/prize/delete", params={"prizeId": prize_id})
        except Exception as e:  # 清理失败不得掩盖用例结果，留痕即可
            print(f"    [chain-cleanup][WARN] {e}")

    def test_full_claim_lifecycle(self, chain, admin_client, user_client):
        """正向全状态机：NOT_CLAIMED → 领奖 CLAIMED → 发货 SHIPPED → 签收 COMPLETED，
        随后活动 end（终态）→ delete（级联）→ 删奖品，每步以服务端回读为证。"""
        rid = chain["record_id"]

        def status_now():
            mine = user_client.get("/draw/my-records")
            assert_success(mine, hint="[闭环] 我的中奖记录", expect_data=True)
            row = next((m for m in mine.data if m.get("recordId") == rid), None)
            assert row, f"[闭环] 记录 {rid} 从 my-records 消失（活动已被提前删除？）"
            return row

        # 0) 初始态
        row = status_now()
        assert row["claimStatus"] == "NOT_CLAIMED", (
            f"[闭环] 初始态应为 NOT_CLAIMED，实际 {row['claimStatus']!r}")

        # 1) 申请领奖 → CLAIMED
        claim = user_client.post("/api/winning/claim", json_body={
            "recordId": rid, "receiverName": "QA闭环",
            "receiverPhone": "13800000001",
            "receiverAddress": "qa_auto 存量补齐闭环地址"})
        assert_success(claim, hint="[闭环] 申请领奖", expect_data=True)
        assert isinstance(claim.data, int) and claim.data > 0, (
            f"[闭环] 领奖应返回工单 id，实际 {claim.data!r}")
        row = status_now()
        assert row["claimStatus"] == "CLAIMED", (
            f"[闭环] 领奖后应为 CLAIMED，实际 {row['claimStatus']!r}（申请未生效？）")
        assert row["claimStatusText"] == "已申请待发货", (
            f"[闭环] 中文映射应为「已申请待发货」，实际 {row['claimStatusText']!r}")

        # 2) 管理端按 CLAIMED 过滤能查到该工单
        page = admin_client.get("/api/winning/manage/list",
                                params={"claimStatus": "CLAIMED",
                                        "page": 1, "pageSize": 50})
        assert_success(page, hint="[闭环] manage/list(CLAIMED)", expect_data=True)
        assert any(x.get("recordId") == rid for x in page.data["list"]), (
            f"[闭环] 领奖后 manage/list(CLAIMED) 查不到记录 {rid}")

        # 3) 越权对照：普通用户不得调 manage 系列（真 403）
        forbidden = user_client.get("/api/winning/manage/list",
                                    params={"page": 1, "pageSize": 5})
        assert forbidden.status == 403, (
            f"[闭环] 普通用户调 manage/list 应真 403，实际 {forbidden.brief()}")
        forbidden2 = user_client.post("/api/winning/manage/status", json_body={
            "recordId": rid, "claimStatus": "SHIPPED"})
        assert forbidden2.status == 403, (
            f"[闭环] 普通用户调 manage/status 应真 403，实际 {forbidden2.brief()}")

        # 4) 置 SHIPPED 缺物流两件套 → 606（业务校验先于落库；空 recordId 才是 400）
        no_logi = admin_client.post("/api/winning/manage/status", json_body={
            "recordId": rid, "claimStatus": "SHIPPED"})
        assert no_logi.status == 200, f"[闭环] 期望 HTTP=200，实际 {no_logi.brief()}"
        assert_code(no_logi, 606, hint="[闭环] 发货缺物流参数应 606")
        assert "物流" in (no_logi.message or ""), (
            f"[闭环] 606 文案应指明物流要求，实际 {no_logi.message!r}")

        # 5) 带物流登记发货 → SHIPPED
        ship = admin_client.post("/api/winning/manage/status", json_body={
            "recordId": rid, "claimStatus": "SHIPPED",
            "shippingCompany": "QA快递", "shippingNo": f"QA{uuid.uuid4().hex[:8]}"})
        assert_success(ship, hint="[闭环] 登记发货")
        row = status_now()
        assert row["claimStatus"] == "SHIPPED", (
            f"[闭环] 发货后应为 SHIPPED，实际 {row['claimStatus']!r}")
        assert row.get("shippingCompany") == "QA快递", (
            f"[闭环] 物流公司未落库，实际 {row.get('shippingCompany')!r}")

        # 6) 用户确认收货 → COMPLETED
        confirm = user_client.post("/api/winning/confirm", json_body={"recordId": rid})
        assert_success(confirm, hint="[闭环] 确认收货")
        row = status_now()
        assert row["claimStatus"] == "COMPLETED", (
            f"[闭环] 签收后应为 COMPLETED，实际 {row['claimStatus']!r}")
        assert row["claimStatusText"] == "已签收", (
            f"[闭环] 中文映射应为「已签收」，实际 {row['claimStatusText']!r}")

        # 7) 活动 end → 终态（detail.status=false 且奖品/人员 valid=false）
        end = admin_client.post("/activity/end",
                                params={"activityId": chain["activity_id"]})
        assert_success(end, hint="[闭环] 结束活动")
        detail = user_client.get("/activity/detail/find",
                                 params={"activityId": chain["activity_id"]})
        assert_success(detail, hint="[闭环] end 后查详情", expect_data=True)
        assert detail.data["status"] is False, (
            f"[闭环] end 后活动应为已结束（status=false），实际 {detail.data['status']!r}")

        # 8) 活动 delete → 详情 202（不假成功）+ 中奖记录级联消失
        delete = admin_client.delete("/activity/delete",
                                     params={"activityId": chain["activity_id"]})
        assert_success(delete, hint="[闭环] 删除活动")
        gone = user_client.get("/activity/detail/find",
                               params={"activityId": chain["activity_id"]})
        assert_code(gone, config.Code.C_ACTIVITY_FIND_DETAIL_ERROR,
                    hint="[闭环] 删除后详情应 202 查询活动详细失败")
        mine = user_client.get("/draw/my-records")
        assert_success(mine, hint="[闭环] 级联后 my-records")
        assert not any(m.get("activityName") == chain["activity_name"]
                       for m in mine.data), (
            "[闭环] 活动删除后中奖记录仍存在（级联清理失效）")

        # 9) 删奖品（活动已删，不再被引用 → 应成功；被引用时实测 302）
        prize_del = admin_client.delete("/prize/delete",
                                        params={"prizeId": chain["prize_id"]})
        assert_success(prize_del, hint="[闭环] 删除奖品")
        fl = admin_client.get("/prize/find-list",
                              params={"currentPage": 1, "pageSize": 100})
        assert_success(fl, hint="[闭环] 删后查奖品列表", expect_data=True)
        assert not any(p.get("prizeId") == chain["prize_id"]
                       for p in fl.data.get("records", [])), (
            "[闭环] 删除后奖品仍在列表中")


@allure.feature("账号安全")
@allure.story("邮箱/密码/收货信息/停启账号（存量缺口补齐）")
@pytest.mark.keypath
class TestUserSecurityEndpoints:
    """B 组：7 个账号安全端点。变更类用例全部跑在随机后缀的一次性账号上。"""

    # B 组端点如下（docstring 已列全 7 端点）

    def test_account_security_shape(self, user_client):
        """正向：账号安全回填 = 当前邮箱 + 脱敏手机号；明文手机号绝不出现"""
        resp = user_client.get("/user/account/security")
        assert_success(resp, hint="[account/security]", expect_data=True)
        data = resp.data
        assert set(data.keys()) == {"email", "maskedPhoneNumber"}, (
            f"[account/security] 字段应恰为 email/maskedPhoneNumber，实际 {sorted(data)}")
        assert data["email"] == config.TEST_USER["mail"], (
            f"[account/security] 邮箱应回显本人：期望 {config.TEST_USER['mail']!r}，"
            f"实际 {data['email']!r}")
        phone = config.TEST_USER["phoneNumber"]
        expected_mask = phone[:3] + "****" + phone[-4:]
        assert data["maskedPhoneNumber"] == expected_mask, (
            f"[account/security] 脱敏格式应为 {expected_mask}，实际 {data['maskedPhoneNumber']!r}")
        assert phone not in resp.text, (
            f"[account/security] 响应泄露明文手机号：{resp.brief()}")

    def test_account_security_requires_login(self, anon_client):
        """鉴权：匿名 → 真 HTTP 401"""
        resp = anon_client.get("/user/account/security")
        assert resp.status == 401, f"[account/security 匿名] 期望 401，实际 {resp.brief()}"

    def test_receiver_info_roundtrip(self, user_client):
        """正向闭环：读结构 → 保存 → 回读逐字段一致（写在白名单测试账号自身）"""
        resp = user_client.get("/user/receiver-info")
        assert_success(resp, hint="[receiver GET]", expect_data=True)
        assert set(resp.data.keys()) == {"receiverName", "receiverPhone",
                                         "receiverAddress", "complete"}, (
            f"[receiver GET] 字段应含 receiver* 三项 + complete，实际 {sorted(resp.data)}")
        assert isinstance(resp.data["complete"], bool), (
            f"[receiver GET] complete 应为 bool，实际 {resp.data['complete']!r}")

        partial = user_client.post("/user/receiver-info",
                                   json_body={"receiverName": "只填收货人"})
        assert partial.status == 200, f"[receiver 缺项] 期望 HTTP=200，实际 {partial.brief()}"
        assert_code(partial, 600, hint="[receiver 缺项] 期望 600 收货信息不完整")

        save = user_client.post("/user/receiver-info", json_body={
            "receiverName": "QA存量", "receiverPhone": "13800000001",
            "receiverAddress": f"qa_auto 地址 {uuid.uuid4().hex[:6]}"})
        assert_success(save, hint="[receiver 保存]")

        back = user_client.get("/user/receiver-info")
        assert_success(back, hint="[receiver 回读]", expect_data=True)
        assert back.data["receiverName"] == "QA存量", (
            f"[receiver 回读] 收货人未落库：{back.data!r}")
        assert back.data["complete"] is True, (
            f"[receiver 回读] 三项齐全后 complete 应为 True：{back.data!r}")

    def test_receiver_info_requires_login(self, anon_client):
        """鉴权：匿名读收货信息 → 真 401"""
        resp = anon_client.get("/user/receiver-info")
        assert resp.status == 401, f"[receiver 匿名] 期望 401，实际 {resp.brief()}"

    def test_security_send_requires_login(self, anon_client):
        """鉴权：匿名调账号安全发码 → 真 401（与公开的登录发码形成对照）"""
        resp = anon_client.post("/user/security/verification-code/send")
        assert resp.status == 401, (
            f"[安全发码 匿名] 期望 401（该端点不在免登录白名单），实际 {resp.brief()}")

    def test_security_send_allowed_for_owner(self, user_client):
        """正向：已登录本人发码应放行；容忍 115/116 频控（与 test_14 同口径），
        但不得为鉴权/业务拒绝（401/403/118）"""
        resp = user_client.post("/user/security/verification-code/send", timeout=25)
        allowed = (config.Code.SUCCESS, config.Code.SMS_SEND_TOO_FREQUENT,
                   config.Code.SMS_SEND_HOUR_LIMIT)
        assert resp.code in allowed, (
            f"[安全发码] 本人发码被拒（不应 401/403/118）：{resp.brief()}")

    def test_email_update_wrong_password(self, user_client):
        """分支：原密码错误 → 111（且不得改成功）"""
        resp = user_client.post("/user/email/update", json_body={
            "verifyType": "password", "oldPassword": "definitely-wrong",
            "newEmail": "qa_auto_should_not_change@test.com"})
        assert resp.status == 200, f"[email 改-错密码] 期望 HTTP=200，实际 {resp.brief()}"
        assert_code(resp, config.Code.PASSWORD_ERROR, hint="[email 改-错密码] 期望 111")

    def test_email_update_wrong_code(self, user_client):
        """分支：验证码错误 → 112"""
        resp = user_client.post("/user/email/update", json_body={
            "verifyType": "code", "verificationCode": "000000",
            "newEmail": "qa_auto_should_not_change@test.com"})
        assert resp.status == 200, f"[email 改-错码] 期望 HTTP=200，实际 {resp.brief()}"
        assert_code(resp, config.Code.VERIFICATION_CODE_ERROR,
                    hint="[email 改-错码] 期望 112")

    def test_email_update_positive_and_reverse_proof(self, anon_client):
        """正向闭环：一次性账号改邮箱 → 新邮箱可登录、旧邮箱不可登录（服务端反证）"""
        old_mail, new_mail = _rand_mail("sec"), _rand_mail("sec2")
        tok = _register_login(anon_client, old_mail, _rand_phone())
        client = ApiClient(token=tok)  # 改邮箱是登录接口，必须带本人 token

        resp = client.post("/user/email/update", json_body={
            "verifyType": "password", "oldPassword": "123456",
            "newEmail": new_mail})
        assert_success(resp, hint="[email 改-正向]")

        new_login = anon_client.post("/user/password/login", json_body={
            "loginName": new_mail, "password": "123456",
            "mandatoryIdentity": "NORMAL"})
        assert_code(new_login, config.Code.SUCCESS,
                    hint="[email 改-反证] 新邮箱必须可登录")

        old_login = anon_client.post("/user/password/login", json_body={
            "loginName": old_mail, "password": "123456",
            "mandatoryIdentity": "NORMAL"})
        assert old_login.code != config.Code.SUCCESS, (
            f"[email 改-反证] 旧邮箱仍可登录（改邮箱未真实生效）：{old_login.brief()}")

    def test_password_update_wrong_password(self, user_client):
        """分支：原密码错误 → 111"""
        resp = user_client.post("/user/password/update", json_body={
            "verifyType": "password", "oldPassword": "definitely-wrong",
            "newPassword": "Qa1234567"})
        assert resp.status == 200, f"[pwd 改-错密码] 期望 HTTP=200，实际 {resp.brief()}"
        assert_code(resp, config.Code.PASSWORD_ERROR, hint="[pwd 改-错密码] 期望 111")

    def test_password_update_positive_and_reverse_proof(self, anon_client):
        """正向闭环：一次性账号改密码 → 新密码可登录、旧密码被拒（111）"""
        mail, phone = _rand_mail("pwd"), _rand_phone()
        tok = _register_login(anon_client, mail, phone)
        client = ApiClient(token=tok)  # 改密码必须带本人 token
        new_pwd = "Qa" + uuid.uuid4().hex[:8]

        resp = client.post("/user/password/update", json_body={
            "verifyType": "password", "oldPassword": "123456",
            "newPassword": new_pwd})
        assert_success(resp, hint="[pwd 改-正向]")

        new_login = anon_client.post("/user/password/login", json_body={
            "loginName": mail, "password": new_pwd,
            "mandatoryIdentity": "NORMAL"})
        assert_code(new_login, config.Code.SUCCESS,
                    hint="[pwd 改-反证] 新密码必须可登录")

        old_login = anon_client.post("/user/password/login", json_body={
            "loginName": mail, "password": "123456",
            "mandatoryIdentity": "NORMAL"})
        assert_code(old_login, config.Code.PASSWORD_ERROR,
                    hint="[pwd 改-反证] 旧密码必须被拒（111）")

    def test_status_update_disable_invalidates_old_token(self, anon_client, admin_client):
        """正向闭环：停用 → 旧 token 立即 401（回库验状态）→ 启用 → 可重新登录"""
        mail, phone = _rand_mail("disb"), _rand_phone()
        tok = _register_login(anon_client, mail, phone)
        victim = ApiClient(token=tok)

        me_before = victim.get("/user/me")
        assert_success(me_before, hint="[停用-前置] 正常查 me", expect_data=True)
        victim_id = me_before.data["userId"]

        dis = admin_client.post("/user/status/update",
                                params={"userId": victim_id, "status": "DISABLED"})
        assert_success(dis, hint="[停用] DISABLED")

        me_after = victim.get("/user/me")
        assert me_after.status == 401, (
            f"[停用-反证] 停用后旧 token 应真 401，实际 {me_after.brief()}")
        assert "停用" in (me_after.message or ""), (
            f"[停用-反证] 401 文案应提示账号已停用，实际 {me_after.message!r}")

        en = admin_client.post("/user/status/update",
                               params={"userId": victim_id, "status": "ACTIVE"})
        assert_success(en, hint="[启用] ACTIVE")

        relogin = anon_client.post("/user/password/login", json_body={
            "loginName": mail, "password": "123456",
            "mandatoryIdentity": "NORMAL"})
        assert_code(relogin, config.Code.SUCCESS,
                    hint="[启用-反证] 重新登录必须恢复")


@allure.feature("管理收口")
@allure.story("工单删除（存量缺口补齐；活动/奖品 end/delete 见 TestClaimLifecycle）")
@pytest.mark.keypath
class TestAdminLifecycleEndpoints:
    """C 组：/api/handover/delete（活动 end/delete、奖品 delete 的正向已在 A 组闭环内断言）。"""

    def test_handover_create_then_admin_delete(self, admin_client, user_client):
        """正向闭环：用户建单 → 管理列表可见 → 删除 → 列表不可见"""
        sid = f"qa_auto_gap_{uuid.uuid4().hex[:8]}"
        created = user_client.post("/api/handover/create", json_body={
            "sessionId": sid, "question": "qa_auto 存量补齐-工单删除闭环"})
        assert_success(created, hint="[工单闭环] 创建", expect_data=True)
        hid = created.data

        before = admin_client.get("/api/handover/list",
                                  params={"page": 1, "pageSize": 50})
        assert_success(before, hint="[工单闭环] 删除前列表", expect_data=True)
        assert any(x.get("id") == hid for x in before.data.get("list", [])), (
            f"[工单闭环] 新建工单 {hid} 不在管理列表中")

        deleted = admin_client.delete("/api/handover/delete", params={"id": hid})
        assert_success(deleted, hint="[工单闭环] 删除")

        after = admin_client.get("/api/handover/list",
                                 params={"page": 1, "pageSize": 50})
        assert_success(after, hint="[工单闭环] 删除后列表", expect_data=True)
        assert not any(x.get("id") == hid for x in after.data.get("list", [])), (
            f"[工单闭环] 删除后工单 {hid} 仍在列表中（删除未生效）")

    def test_handover_delete_requires_admin(self, user_client):
        """越权：普通用户删工单 → 真 HTTP 403"""
        resp = user_client.delete("/api/handover/delete", params={"id": 1})
        assert resp.status == 403, (
            f"[工单删除 越权] 普通用户应真 403，实际 {resp.brief()}")

    def test_activity_end_delete_require_admin(self, user_client):
        """越权：普通用户结束/删除活动 → 真 403（adminOnly 兜底）"""
        end = user_client.post("/activity/end", params={"activityId": 1})
        assert end.status == 403, f"[activity/end 越权] 期望 403，实际 {end.brief()}"
        delete = user_client.delete("/activity/delete", params={"activityId": 1})
        assert delete.status == 403, f"[activity/delete 越权] 期望 403，实际 {delete.brief()}"

    def test_prize_delete_requires_admin(self, user_client):
        """越权：普通用户删奖品 → 真 HTTP 403"""
        resp = user_client.delete("/prize/delete", params={"prizeId": 1})
        assert resp.status == 403, f"[prize/delete 越权] 期望 403，实际 {resp.brief()}"
