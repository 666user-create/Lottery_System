"""
第③步 · 关键路径用例（2/7）：活动模块

覆盖：列表正常流、分页参数真实生效（契约风险 C-1）、详情正常流与数据回显、
      不存在活动的错误码定性（契约风险 C-3 / 上轮 BUG-003 真伪）、越权保护。

能抓到的缺陷：
  - 分页参数被静默忽略（错误参数名/未绑定）→ 两页数据完全相同
  - 详情接口返回 200+null（前端无法区分"不存在"与"成功"）
  - 详情数据串号（查 34 却返回别的活动）
  - 普通用户越权创建活动
"""
import pytest

from common import config
from common.client import assert_code, assert_success, assert_field, assert_paging_params_effective


@pytest.mark.smoke
@pytest.mark.keypath
class TestActivityKeyPath:

    def test_find_list_normal_flow(self, admin_client):
        """正常流：活动列表返回 total 与 records，且 total 与基础数据吻合"""
        resp = admin_client.get("/activity/find-list",
                                params={"currentPage": 1, "pageSize": 10})
        assert_success(resp, hint="[活动列表]", expect_data=True)
        assert_field(resp, "total", resp.data["total"], hint="[活动列表] total 应存在")
        assert resp.data["total"] >= config.BASELINE["activity_count_min"], (
            f"[活动列表] total={resp.data['total']} 少于基线 "
            f"{config.BASELINE['activity_count_min']}（疑似数据被误删）")
        records = resp.data.get("records", [])
        assert len(records) > 0, f"[活动列表] records 为空：{resp.brief()}"
        assert_field(resp, "records.0.activityId", records[0]["activityId"],
                     hint="[活动列表] 首条应含 activityId")

    def test_paging_currentpage_effective(self, admin_client):
        """
        契约 C-1：验证 `currentPage`（而非 page）是后端识别的参数名。
        能抓到：分页参数不被识别 → 第 2 页返回与第 1 页相同数据。
        """
        p1 = admin_client.get("/activity/find-list",
                              params={"currentPage": 1, "pageSize": 2})
        p2 = admin_client.get("/activity/find-list",
                              params={"currentPage": 2, "pageSize": 2})
        assert_paging_params_effective(p1, p2, hint="[活动列表分页 currentPage=1 vs 2, pageSize=2]")

    def test_paging_wrong_param_name_is_ignored(self, admin_client):
        """
        契约 C-1 反面证据：`page` 参数被后端**忽略**（PageParam 只接 currentPage）。
        该用例锁定"当前契约行为"：若未来后端改为接受 page，此用例会失败并提醒同步契约文档。
        """
        right = admin_client.get("/activity/find-list",
                                 params={"currentPage": 1, "pageSize": 2})
        wrong = admin_client.get("/activity/find-list",
                                 params={"page": 2, "pageSize": 2})
        assert_success(right, hint="[分页-正确参数名]")
        assert_success(wrong, hint="[分页-错误参数名]")
        r_right = right.data.get("records", [])
        r_wrong = wrong.data.get("records", [])
        assert r_right == r_wrong, (
            "[分页契约] 期望 `page` 被忽略（与 currentPage=1 结果一致），"
            f"实际不同 —— 后端可能已支持 `page`，请同步更新 docs/test/contract/api-contract.md\n"
            f" currentPage=1 → {[r.get('activityId') for r in r_right]}\n"
            f" page=2        → {[r.get('activityId') for r in r_wrong]}")

    def test_detail_normal_flow_echo_consistency(self, admin_client):
        """正常流：活动详情回显 activityId 与被请求一致，且含奖品与用户列表"""
        aid = config.BASELINE["activity_id_sample"]
        resp = admin_client.get("/activity/detail/find", params={"activityId": aid})
        assert_success(resp, hint=f"[活动详情 id={aid}]", expect_data=True)
        # 能抓到：查 34 却返回别的活动（串号）
        assert_field(resp, "activityId", aid, hint=f"[活动详情 id={aid}] 回显必须一致")
        assert isinstance(resp.data.get("prizes"), list), (
            f"[活动详情 id={aid}] prizes 应为列表：{resp.brief()}")
        assert isinstance(resp.data.get("users"), list), (
            f"[活动详情 id={aid}] users 应为列表：{resp.brief()}")

    def test_detail_not_exist_must_not_report_success(self, admin_client):
        """
        契约 C-3（定性上轮 BUG-003）：不存在的活动 id **不得**返回成功码。
        代码依据：ActivityController.java:63-66 在 detailDTO==null 时抛 ControllerException(202)。
        能抓到：返回 code=200 + data=null（前端无法区分"不存在"与"成功"），
                及 HTTP 5xx（未处理异常）。
        """
        resp = admin_client.get("/activity/detail/find", params={"activityId": 999999})
        print(f"    [定性] 不存在活动 → HTTP={resp.status} code={resp.code} msg={resp.message!r}")
        assert resp.status == 200, (
            f"[活动详情-不存在] 期望 HTTP=200（业务失败走 body.code），实际 HTTP={resp.status}")
        assert_code(resp, config.Code.C_ACTIVITY_FIND_DETAIL_ERROR,
                    hint="[活动详情-不存在] 期望 ControllerException(202) 查询活动详细失败")

    def test_detail_type_mismatch(self, admin_client):
        """分支：activityId 传非数字 → 400 参数类型不匹配（而非 500）"""
        resp = admin_client.get("/activity/detail/find", params={"activityId": "abc"})
        assert_code(resp, config.Code.BAD_REQUEST, hint="[活动详情-类型错误]")

    def test_detail_missing_param(self, admin_client):
        """分支：缺 activityId → 400 缺少请求参数"""
        resp = admin_client.get("/activity/detail/find")
        assert_code(resp, config.Code.BAD_REQUEST, hint="[活动详情-缺参数]")


@pytest.mark.keypath
class TestActivityPermission:

    def test_normal_user_cannot_create_activity(self, user_client):
        """越权：普通用户创建活动 → 403（LoginInterceptor adminOnly）"""
        resp = user_client.post("/activity/create", json_body={
            "activityName": f"{config.TEST_PREFIX}越权活动",
            "description": "不应创建成功",
            "activityPrizeList": [{"prizeId": 25, "prizeAmount": 1, "prizeTier": "FIRST_PRIZE"}],
            "activityUserList": [{"userId": 1, "userName": "test"}],
        })
        assert resp.status == 403, (
            f"[越权创建活动] 期望 HTTP=403，实际 {resp.brief()} —— 权限漏洞！")

    def test_normal_user_can_read_activity_list(self, user_client):
        """正常流：普通用户可读活动列表（读接口不设 ADMIN 限制）"""
        resp = user_client.get("/activity/find-list",
                               params={"currentPage": 1, "pageSize": 10})
        assert_success(resp, hint="[普通用户读活动列表]")
