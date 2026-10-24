"""
第⑥步 · 新增接口测试（2/3）：操作日志

覆盖 OperationLogController（`/api/operation-log/list`，adminOnly，2026-10 新增）。

被测设计（源码 OperationLogInterceptor 注释，逐条验证）：
    ① 只记「写操作」与登录，GET/HEAD/OPTIONS 一律不记；
    ② 显式排除 `/draw/winning-records/show`（POST 但语义是查询）；
    ③ 不记录请求体与任何入参（注册参数含密码/手机号/邮箱，落明文等于绕过加密）；
    ④ 落库走专用线程池（**异步**），故断言必须轮询等待，不能立即查；
    ⑤ 拦截器在 LoginInterceptor 之前注册 → 被 401/403 拒绝的请求也会留痕。

查询参数（OperationLogQueryParam extends PageParam）：
    currentPage / pageSize（有边界校验） + module / identity / startTime / endTime

实测基线（2026-10-16）：
    列表默认返回最新在前；pageSize>100 → 400「每页数量不能大于100」；
    currentPage<1 → 400「当前页不能小于1」；module/identity/startTime 筛选均生效。
"""
import time

import allure
import pytest

from common import config
from common.client import assert_code, assert_http, assert_success

RECORD_FIELDS = ["logId", "identity", "module", "operation", "httpMethod",
                 "path", "resultCode", "costMs", "ip", "gmtCreate"]


def _fetch_page(client, **params):
    query = {"currentPage": 1, "pageSize": 20}
    query.update(params)
    resp = client.get("/api/operation-log/list", params=query)
    assert_success(resp, hint=f"[操作日志] params={params}", expect_data=True)
    return resp.data


def _wait_total_at_least(client, expected, timeout=8.0):
    """轮询等待日志总数达到期望（日志异步落库，必须等待而非立即断言）"""
    deadline = time.time() + timeout
    last = -1
    while time.time() < deadline:
        last = _fetch_page(client, pageSize=1)["total"]
        if last >= expected:
            return last
        time.sleep(0.4)
    return last


@allure.feature("操作日志")
@allure.story("列表与筛选")
@pytest.mark.keypath
class TestOperationLogQuery:

    def test_list_structure(self, admin_client):
        """结构契约：records 字段完整，resultCode/costMs 为数字，gmtCreate 非空"""
        data = _fetch_page(admin_client, pageSize=5)
        assert "total" in data and "records" in data, (
            f"[操作日志] 返回应含 total/records，实际字段={list(data.keys())}")
        assert isinstance(data["total"], int) and data["total"] >= 0
        records = data["records"]
        assert isinstance(records, list), "[操作日志] records 应为列表"
        assert records, "[操作日志] 应有历史数据（本套件此前已产生大量写操作日志）"
        for key in RECORD_FIELDS:
            assert key in records[0], f"[操作日志] 记录缺字段 {key}；实际={list(records[0].keys())}"
        assert isinstance(records[0]["costMs"], int), "[操作日志] costMs 应为整数"
        assert records[0]["gmtCreate"], "[操作日志] gmtCreate 不应为空"

    def test_pagination_pages_do_not_overlap(self, admin_client):
        """分页正确性：pageSize=2 的第 1/2 页 logId 集合不得重叠"""
        page1 = _fetch_page(admin_client, currentPage=1, pageSize=2)["records"]
        page2 = _fetch_page(admin_client, currentPage=2, pageSize=2)["records"]
        ids1 = {r["logId"] for r in page1}
        ids2 = {r["logId"] for r in page2}
        assert ids1 and ids2, f"[操作日志] 分页数据不足：page1={len(page1)} page2={len(page2)}"
        assert not (ids1 & ids2), f"[操作日志] 第 1/2 页出现重复记录：{ids1 & ids2}"

    def test_filter_by_module(self, admin_client):
        """筛选：module=用户模块 → 返回记录的 module 必须全部等于筛选值"""
        data = _fetch_page(admin_client, pageSize=10, module="用户模块")
        records = data["records"]
        assert data["total"] > 0, "[操作日志] 模块筛选 total 不应为 0（此前有大量登录/注册）"
        wrong = {r["module"] for r in records if r["module"] != "用户模块"}
        assert not wrong, f"[操作日志] 模块筛选失效，混入其它模块：{wrong}"

    def test_filter_by_identity(self, admin_client):
        """筛选：identity=ANONYMOUS → 返回记录身份全部为 ANONYMOUS（登录前请求）"""
        data = _fetch_page(admin_client, pageSize=10, identity="ANONYMOUS")
        wrong = {r["identity"] for r in data["records"] if r["identity"] != "ANONYMOUS"}
        assert not wrong, f"[操作日志] 身份筛选失效，混入：{wrong}"

    def test_filter_by_future_date_returns_empty(self, admin_client):
        """筛选：startTime 设为未来日期 → total 必须为 0（时间条件真实生效）"""
        data = _fetch_page(admin_client, pageSize=5, startTime="2099-01-01",
                           endTime="2099-12-31")
        assert data["total"] == 0, (
            f"[操作日志] 未来日期区间应为空，实际 total={data['total']}")

    @pytest.mark.boundary
    @pytest.mark.parametrize("params,case", [
        ({"currentPage": 1, "pageSize": 999999}, "pageSize 超上限"),
        ({"currentPage": 1, "pageSize": 0}, "pageSize 下越界"),
        ({"currentPage": -1, "pageSize": 10}, "currentPage 下越界"),
    ])
    def test_paging_boundary_returns_400(self, admin_client, params, case):
        """边界：分页参数越界 → 400 明确提示（复用 PageParam 校验，而非 500）"""
        resp = admin_client.get("/api/operation-log/list", params=params)
        assert_code(resp, config.Code.BAD_REQUEST, hint=f"[操作日志-{case}]")
        assert resp.message, f"[操作日志-{case}] 400 应带可读提示，实际 message={resp.message!r}"

    def test_rejects_anonymous_and_normal_user(self, anon_client, user_client):
        """权限契约：操作日志属 adminOnly → 匿名 401、普通用户 403"""
        assert_http(anon_client.get("/api/operation-log/list",
                                    params={"currentPage": 1, "pageSize": 5}), 401,
                    hint="[操作日志-匿名]")
        assert_http(user_client.get("/api/operation-log/list",
                                    params={"currentPage": 1, "pageSize": 5}), 403,
                    hint="[操作日志-普通用户]")


@allure.feature("操作日志")
@allure.story("旁路采集行为（源码设计约束验证）")
@pytest.mark.keypath
class TestOperationLogCollection:

    def test_write_operation_is_logged(self, admin_client, faq_cleanup):
        """
        约束①（写操作留痕）：管理员新增 FAQ → 日志总数增加，且能查到该操作。

        FAQ 新增走 POST → 应被记录为「客服模块 / 新增FAQ」。
        日志异步落库，故用轮询等待（源码注释：落库走专用线程池）。
        """
        before = _fetch_page(admin_client, pageSize=1)["total"]
        created = admin_client.post("/api/faq/add", json_body={
            "question": f"{config.TEST_PREFIX}日志采集验证",
            "answer": "用于验证写操作留痕",
            "category": "自动化测试"})
        assert_success(created, hint="[准备数据-FAQ新增]", expect_data=True)
        faq_cleanup(created.data)

        after = _wait_total_at_least(admin_client, before + 1)
        assert after >= before + 1, (
            f"[写操作留痕] FAQ 新增后日志总数未增加：before={before} after={after}")

        records = _fetch_page(admin_client, pageSize=50)["records"]
        hit = [r for r in records
               if r["path"] == "/api/faq/add" and r["httpMethod"] == "POST"]
        assert hit, "[写操作留痕] 未能查到 POST /api/faq/add 的记录"
        assert hit[0]["resultCode"] == config.Code.SUCCESS, (
            f"[写操作留痕] 成功操作应记录 resultCode=200，实际={hit[0]['resultCode']}")

    @pytest.mark.boundary
    def test_read_operation_is_not_logged(self, admin_client, faq_cleanup):
        """
        约束②（只记写操作）：GET 查询不产生日志。

        为避免「上一条写操作的日志延迟落库」造成误判，先做一次写操作并等它落库，
        再取基准总数，然后发 GET，等待 2 秒后比对。
        """
        # 建立静默基线：先触发一次写操作并等它落库
        baseline_before = _fetch_page(admin_client, pageSize=1)["total"]
        created = admin_client.post("/api/faq/add", json_body={
            "question": f"{config.TEST_PREFIX}静默基线",
            "answer": "用于建立日志静默基线",
            "category": "自动化测试"})
        assert_success(created, hint="[静默基线-FAQ新增]")
        faq_cleanup(created.data)   # 跑完自动删除，绝不残留测试数据

        stable = _wait_total_at_least(admin_client, baseline_before + 1)
        time.sleep(1.0)  # 让线程池队列排空
        before_get = _fetch_page(admin_client, pageSize=1)["total"]
        assert before_get >= stable

        for _ in range(3):
            admin_client.get("/activity/find-list", params={"currentPage": 1, "pageSize": 5})
        time.sleep(2.0)

        after_get = _fetch_page(admin_client, pageSize=1)["total"]
        assert after_get == before_get, (
            f"[只记写操作] GET 请求不应产生日志：before={before_get} after={after_get}")

    @pytest.mark.boundary
    def test_rejected_request_is_still_logged(self, anon_client, admin_client):
        """
        约束⑤（被拒也留痕）：未登录请求管理接口 → 401/403，但日志仍应记录。

        源码注释：本拦截器必须注册在 LoginInterceptor 之前，这样被 401/403 拒绝的请求也能留下记录。
        """
        before = _fetch_page(admin_client, pageSize=1)["total"]
        rejected = anon_client.post("/activity/create", json_body={
            "activityName": f"{config.TEST_PREFIX}被拒留痕", "description": "-"})
        assert_http(rejected, 401, hint="[被拒请求]")

        after = _wait_total_at_least(admin_client, before + 1)
        assert after >= before + 1, (
            f"[被拒也留痕] 被 401 拒绝的请求未留下日志：before={before} after={after}")

    def test_no_credential_plaintext_in_logs(self, admin_client):
        """
        约束③（不记录入参）：日志中不得出现密码明文。

        做法：读最新若干条“密码登录”记录，断言其序列化内容不含测试账号密码，
        且不含 token 字段（日志表结构本身无入参列）。
        """
        data = _fetch_page(admin_client, pageSize=50)
        login_records = [r for r in data["records"]
                         if r["path"] == "/user/password/login"]
        assert login_records, "[隐私检查] 应能查到密码登录日志（本套件大量登录）"
        password = config.TEST_ADMIN["password"]
        for record in login_records:
            blob = str(record)
            assert password not in blob, (
                f"[隐私检查] 日志中出现密码明文：{record}")
            assert "token" not in blob.lower(), (
                f"[隐私检查] 日志中出现 token 字段：{record}")
