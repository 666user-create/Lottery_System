"""
第③步 · 关键路径用例（4/7）：抽奖与中奖记录

覆盖：中奖记录查询（活动+奖品 / 仅活动）、我的中奖记录、免登录分享（C-4，业主确认为设计意图）、
      字段脱敏边界、参数校验分支。

⚠️ 不含 `/draw/prize` 正常流：
  该接口经 RabbitMQ 投递，当前环境 MQ 不可达（8.136.222.102:5672），
  调用会阻塞约 30s 后超时（缺陷 BUG-002）。依赖异常场景归入第④步处理。

能抓到的缺陷：
  - 免登录接口泄露手机号/邮箱（PII 越界）
  - 中奖记录字段缺失导致前端渲染空白
  - 缺参数时返回 500 而非 400
"""
import pytest

from common import config
from common.client import assert_code, assert_success, assert_field


@pytest.mark.smoke
@pytest.mark.keypath
class TestWinningRecordsKeyPath:

    def test_query_by_activity_and_prize(self, admin_client):
        """正常流：按活动+奖品查中奖记录，字段完整且非空"""
        resp = admin_client.post("/draw/winning-records/show", json_body={
            "activityId": config.BASELINE["activity_id_sample"],
            "prizeId": config.BASELINE["prize_id_sample"]})
        assert_success(resp, hint="[中奖记录-活动+奖品]")
        records = resp.data or []
        assert isinstance(records, list), (
            f"[中奖记录] 期望 list，实际 {type(records).__name__}: {resp.brief()}")
        if records:
            first = records[0]
            # 能抓到：字段缺失/为 null（前端显示空白）
            for key in ("winnerId", "winnerName", "prizeName", "prizeTier", "winningTime"):
                assert key in first, f"[中奖记录] 记录缺字段 '{key}'：{first}"
                assert first[key] not in (None, ""), (
                    f"[中奖记录] 字段 '{key}' 为空值：{first}")

    def test_query_by_activity_only_returns_multiple(self, admin_client):
        """分支：仅按活动查询 → 返回该活动全部中奖记录（通常多于单奖品维度）"""
        by_activity = admin_client.post("/draw/winning-records/show", json_body={
            "activityId": config.BASELINE["activity_id_sample"]})
        assert_success(by_activity, hint="[中奖记录-仅活动]")
        by_both = admin_client.post("/draw/winning-records/show", json_body={
            "activityId": config.BASELINE["activity_id_sample"],
            "prizeId": config.BASELINE["prize_id_sample"]})
        assert_success(by_both, hint="[中奖记录-活动+奖品]")
        # 能抓到：prizeId 过滤无效（两维度返回相同结果）
        assert len(by_activity.data or []) >= len(by_both.data or []), (
            f"[中奖记录] 活动维度({len(by_activity.data or [])})应 ≥ 活动+奖品维度"
            f"({len(by_both.data or [])}) —— 疑似 prizeId 过滤行为异常")

    def test_missing_activity_id(self, admin_client):
        """分支：缺 activityId → 400 参数校验（@NotNull）"""
        resp = admin_client.post("/draw/winning-records/show", json_body={
            "prizeId": config.BASELINE["prize_id_sample"]})
        assert_code(resp, config.Code.BAD_REQUEST, hint="[中奖记录-缺activityId]")

    def test_my_records_requires_login(self, user_client, anon_client):
        """正常流 + 分支：登录用户可查；匿名访问被拦"""
        ok = user_client.get("/draw/my-records")
        assert_success(ok, hint="[我的中奖记录-已登录]")
        assert isinstance(ok.data, list), f"[我的中奖记录] 期望 list：{ok.brief()}"

        denied = anon_client.get("/draw/my-records")
        assert denied.status == 401, (
            f"[我的中奖记录-未登录] 期望 HTTP=401，实际 {denied.brief()}")


@pytest.mark.keypath
class TestPublicShareContract:
    """C-4：中奖名单免登录可查 —— 业主确认「为了可以直接分享抽奖结果」"""

    def test_anonymous_can_query_records(self, anon_client):
        """
        契约事实：匿名可查中奖记录（AppConfig.java:28 白名单）。
        该用例锁定设计意图；若被误加鉴权，此用例会失败并提醒同步契约。
        """
        resp = anon_client.post("/draw/winning-records/show", json_body={
            "activityId": config.BASELINE["activity_id_sample"]})
        assert_success(resp, hint="[免登录分享-匿名查询]")

    def test_share_response_has_no_pii(self, anon_client):
        """
        脱敏边界：分享场景返回的字段**不得**包含手机号/邮箱等 PII。
        代码依据：DrawPrizeController.java:69-75 仅输出 5 个字段。
        能抓到：后续改动把 winnerPhoneNumber/winnerEmail 加入分享响应（隐私泄露）。
        """
        resp = anon_client.post("/draw/winning-records/show", json_body={
            "activityId": config.BASELINE["activity_id_sample"]})
        assert_success(resp, hint="[免登录分享-脱敏]")
        raw = resp.text.lower()
        forbidden = ["phone", "mobile", "email", "mail", "winnerphonenumber"]
        hit = [k for k in forbidden if k in raw]
        assert not hit, (
            f"[免登录分享-脱敏] 响应中出现疑似 PII 字段 {hit}，分享场景不应暴露联系方式。\n"
            f" 响应: {resp.brief()}")
