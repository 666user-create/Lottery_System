"""
第⑥步 · 新增接口测试（1/3）：数据统计

覆盖 StatController（`/api/stat/**`，adminOnly，2026-10 新增）：
    GET /api/stat/overview                全局概览
    GET /api/stat/activity?activityId=    单活动维度统计

为什么这样设计：
    统计接口最典型、也最难靠肉眼发现的缺陷是「数字有、但对不上」。
    因此本文件的重点是**跨接口互证**：每个统计值都必须能被列表/明细接口验证。

实测基线（2026-10-16，随数据变化，故断言用「等于/包含」而非硬编码数字）：
    overview.activityTotal  == GET /activity/find-list 的 total
    overview.prizeTotal     == GET /prize/find-list 的 total
    overview.userTotal      == GET /user/base-user/find-list 中 identity=NORMAL 的条数
    sum(tierStats[].count)  == overview.winningTotal
    activity.drawnTotal     == POST /draw/winning-records/show 的条数
"""
import allure
import pytest

from common import config
from common.client import assert_code, assert_http, assert_success

OVERVIEW_KEYS = [
    "activityTotal", "activityRunning", "activityCompleted", "activityDrawn",
    "prizeTotal", "userTotal", "winningTotal", "tierStats",
]


@allure.feature("数据统计")
@allure.story("概览统计")
@pytest.mark.keypath
class TestStatOverview:

    def test_overview_structure(self, admin_client):
        """结构契约：概览返回全部计数字段，计数为非负整数，tierStats 为列表"""
        resp = admin_client.get("/api/stat/overview")
        assert_success(resp, hint="[统计概览]", expect_data=True)
        data = resp.data
        for key in OVERVIEW_KEYS:
            assert key in data, f"[统计概览] 缺字段 {key}；实际字段={list(data.keys())}"
        for key in OVERVIEW_KEYS[:-1]:
            value = data[key]
            assert isinstance(value, int) and value >= 0, (
                f"[统计概览] {key} 应为非负整数，实际 {value!r}")
        assert isinstance(data["tierStats"], list), (
            f"[统计概览] tierStats 应为列表，实际 {type(data['tierStats']).__name__}")

    def test_activity_total_matches_activity_list(self, admin_client):
        """跨接口互证：activityTotal 必须等于活动列表的 total（口径一致性）"""
        overview = admin_client.get("/api/stat/overview")
        assert_success(overview, hint="[统计概览]")
        activity_list = admin_client.get("/activity/find-list",
                                         params={"currentPage": 1, "pageSize": 100})
        assert_success(activity_list, hint="[活动列表]")
        assert overview.data["activityTotal"] == activity_list.data["total"], (
            f"[口径不一致] 统计 activityTotal={overview.data['activityTotal']}，"
            f"活动列表 total={activity_list.data['total']}")

    def test_prize_total_matches_prize_list(self, admin_client):
        """跨接口互证：prizeTotal 必须等于奖品列表的 total"""
        overview = admin_client.get("/api/stat/overview")
        assert_success(overview, hint="[统计概览]")
        prize_list = admin_client.get("/prize/find-list",
                                      params={"currentPage": 1, "pageSize": 100})
        assert_success(prize_list, hint="[奖品列表]")
        assert overview.data["prizeTotal"] == prize_list.data["total"], (
            f"[口径不一致] 统计 prizeTotal={overview.data['prizeTotal']}，"
            f"奖品列表 total={prize_list.data['total']}")

    def test_user_total_counts_normal_identity_only(self, admin_client):
        """
        口径契约：userTotal 只统计 NORMAL 身份（不含管理员）。

        实测（2026-10-16）：人员列表共 17 人（NORMAL 12 + ADMIN 5），stat.userTotal = 12。
        这条用例同时锁住「口径」与「实现」——若将来把管理员也算进去，本用例会失败。
        """
        overview = admin_client.get("/api/stat/overview")
        assert_success(overview, hint="[统计概览]")
        users = admin_client.get("/user/base-user/find-list")
        assert_success(users, hint="[人员列表]")
        rows = users.data or []
        normal_count = sum(1 for u in rows if str(u.get("identity")).upper() == "NORMAL")
        assert overview.data["userTotal"] == normal_count, (
            f"[口径不一致] 统计 userTotal={overview.data['userTotal']}，"
            f"人员列表中 NORMAL={normal_count}（总人数 {len(rows)}）")

    def test_activity_status_partition_and_drawn_bound(self, admin_client):
        """口径契约：进行中 + 已完成 = 总数；已开奖数不得超过已完成数"""
        data = admin_client.get("/api/stat/overview").data
        assert data["activityRunning"] + data["activityCompleted"] == data["activityTotal"], (
            f"[口径不一致] running({data['activityRunning']}) + "
            f"completed({data['activityCompleted']}) != total({data['activityTotal']})")
        assert data["activityDrawn"] <= data["activityCompleted"], (
            f"[口径不一致] 已开奖 {data['activityDrawn']} 超过已完成 {data['activityCompleted']}")

    def test_tier_stats_sum_matches_winning_total(self, admin_client):
        """口径契约：各奖项中奖数之和 == 中奖总数（防漏统计某奖项等级）"""
        data = admin_client.get("/api/stat/overview").data
        tier_sum = sum(int(t.get("count") or 0) for t in data["tierStats"])
        assert tier_sum == data["winningTotal"], (
            f"[口径不一致] tierStats 之和={tier_sum}，winningTotal={data['winningTotal']}")

    def test_overview_rejects_anonymous_and_normal_user(self, anon_client, user_client):
        """权限契约：概览属 adminOnly → 匿名 401、普通用户 403（真 HTTP 状态码）"""
        assert_http(anon_client.get("/api/stat/overview"), 401, hint="[统计概览-匿名]")
        assert_http(user_client.get("/api/stat/overview"), 403, hint="[统计概览-普通用户]")


@allure.feature("数据统计")
@allure.story("单活动维度统计")
@pytest.mark.keypath
class TestStatActivity:

    def test_activity_stat_structure_and_consistency(self, admin_client):
        """结构 + 内部一致性：prizeStats 明细之和必须等于汇总值"""
        activity_id = config.BASELINE["activity_id_sample"]
        resp = admin_client.get("/api/stat/activity", params={"activityId": activity_id})
        assert_success(resp, hint="[活动统计]", expect_data=True)
        data = resp.data

        for key in ("activityId", "activityName", "participantTotal",
                    "prizeAmountTotal", "drawnTotal", "prizeStats"):
            assert key in data, f"[活动统计] 缺字段 {key}；实际字段={list(data.keys())}"
        assert data["activityId"] == activity_id, (
            f"[活动统计] activityId 回显不一致：{data['activityId']} != {activity_id}")
        assert data["statusMessage"], "[活动统计] statusMessage 不应为空"

        stats = data["prizeStats"] or []
        amount_sum = sum(int(p.get("prizeAmount") or 0) for p in stats)
        drawn_sum = sum(int(p.get("drawn") or 0) for p in stats)
        assert amount_sum == data["prizeAmountTotal"], (
            f"[口径不一致] prizeStats.prizeAmount 之和={amount_sum}，"
            f"prizeAmountTotal={data['prizeAmountTotal']}")
        assert drawn_sum == data["drawnTotal"], (
            f"[口径不一致] prizeStats.drawn 之和={drawn_sum}，drawnTotal={data['drawnTotal']}")

    def test_drawn_total_matches_winning_records(self, admin_client):
        """跨接口互证：活动统计的 drawnTotal == 该活动中奖记录条数"""
        activity_id = config.BASELINE["activity_id_sample"]
        stat = admin_client.get("/api/stat/activity", params={"activityId": activity_id})
        assert_success(stat, hint="[活动统计]")
        records = admin_client.post("/draw/winning-records/show",
                                    json_body={"activityId": activity_id})
        assert_success(records, hint="[中奖记录]", expect_data=True)
        assert stat.data["drawnTotal"] == len(records.data or []), (
            f"[口径不一致] 统计 drawnTotal={stat.data['drawnTotal']}，"
            f"中奖记录条数={len(records.data or [])}")

    def test_not_exist_activity_returns_explicit_error(self, admin_client):
        """分支：不存在的活动 id → 明确错误码（不得返回 200+空数据假装成功）"""
        resp = admin_client.get("/api/stat/activity", params={"activityId": 999999})
        assert resp.code != config.Code.SUCCESS, (
            f"[活动统计-不存在] 不得报成功，实际 {resp.brief()}")
        assert_code(resp, config.Code.C_ACTIVITY_FIND_DETAIL_ERROR,
                    hint="[活动统计-不存在] 期望 202 查询活动详细失败")

    def test_missing_activity_id_is_rejected(self, admin_client):
        """分支：缺 activityId → 400 缺少请求参数（@RequestParam 必填生效）"""
        resp = admin_client.get("/api/stat/activity")
        assert_code(resp, config.Code.BAD_REQUEST, hint="[活动统计-缺参数]")

    def test_activity_stat_rejects_anonymous_and_normal_user(self, anon_client, user_client):
        """权限契约：单活动统计属 adminOnly → 匿名 401、普通用户 403"""
        params = {"activityId": config.BASELINE["activity_id_sample"]}
        assert_http(anon_client.get("/api/stat/activity", params=params), 401,
                    hint="[活动统计-匿名]")
        assert_http(user_client.get("/api/stat/activity", params=params), 403,
                    hint="[活动统计-普通用户]")
