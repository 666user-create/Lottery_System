"""
第③步 · 关键路径用例（3/7）：奖品模块

覆盖：列表正常流、分页生效、越权保护、multipart 契约（C-8）。

说明：本文件**不含创建奖品的正常流**——原因：
  `/prize/create` 无对应删除接口（PrizeController 仅 create/find-list/upload），
  写入会产生无法通过 API 清理的数据，违反"跑完就清、绝不污染"约束。
  创建类写操作统一放到第④步，并配套 DB 白名单清理工具后执行。
"""
import pytest

from common import config
from common.client import assert_code, assert_success, assert_field, assert_paging_params_effective


@pytest.mark.smoke
@pytest.mark.keypath
class TestPrizeKeyPath:

    def test_find_list_normal_flow(self, admin_client):
        """正常流：奖品列表 total 与 records 正常，total 不低于基线"""
        resp = admin_client.get("/prize/find-list",
                                params={"currentPage": 1, "pageSize": 10})
        assert_success(resp, hint="[奖品列表]", expect_data=True)
        assert resp.data["total"] >= config.BASELINE["prize_count_min"], (
            f"[奖品列表] total={resp.data['total']} 少于基线 {config.BASELINE['prize_count_min']}")
        records = resp.data.get("records", [])
        assert len(records) > 0, f"[奖品列表] records 为空：{resp.brief()}"
        first = records[0]
        # 能抓到：列表字段缺失（前端渲染空白）
        for k in ("prizeId", "prizeName", "price"):
            assert k in first, f"[奖品列表] 首条缺字段 '{k}'：{first}"

    def test_paging_currentpage_effective(self, admin_client):
        """契约 C-1：`currentPage` 分页在奖品列表同样生效"""
        p1 = admin_client.get("/prize/find-list", params={"currentPage": 1, "pageSize": 2})
        p2 = admin_client.get("/prize/find-list", params={"currentPage": 2, "pageSize": 2})
        assert_paging_params_effective(p1, p2, hint="[奖品列表分页 pageSize=2]")


@pytest.mark.keypath
class TestPrizePermissionAndContract:

    def test_normal_user_cannot_read_prize_list(self, user_client):
        """越权：普通用户访问奖品列表 → 403（/prize/** 属 adminOnly）"""
        resp = user_client.get("/prize/find-list", params={"currentPage": 1, "pageSize": 10})
        assert resp.status == 403, (
            f"[越权读奖品列表] 期望 HTTP=403，实际 {resp.brief()}")

    def test_normal_user_cannot_create_prize(self, user_client):
        """越权：普通用户创建奖品 → 403"""
        resp = user_client.post_multipart(
            "/prize/create",
            data={"param": '{"prizeName":"%s越权奖品","price":1}' % config.TEST_PREFIX},
            files={"prizePic": ("x.png", b"fake-bytes", "image/png")})
        assert resp.status == 403, (
            f"[越权创建奖品] 期望 HTTP=403，实际 {resp.brief()}")

    def test_create_prize_contract_requires_multipart(self, admin_client):
        """
        契约 C-8：`/prize/create` 是 multipart（@RequestPart），用 JSON 调用必须失败。
        能抓到：契约文档与实现不一致（若 JSON 也能成功，说明接口签名已变更）。
        """
        resp = admin_client.post("/prize/create", json_body={
            "prizeName": f"{config.TEST_PREFIX}契约探测奖品", "price": 1})
        assert resp.code != config.Code.SUCCESS or resp.status >= 400, (
            f"[multipart契约] 期望 JSON 调用失败，实际却成功（接口签名可能已变更，"
            f"请同步 docs/test/contract/api-contract.md）: {resp.brief()}")
        print(f"    [契约] JSON 调用 /prize/create → HTTP={resp.status} code={resp.code}")

    def test_upload_pic_requires_admin(self, user_client):
        """越权：普通用户上传图片 → 403"""
        resp = user_client.post_multipart(
            "/prize/pic/upload",
            files={"multipartFile": ("t.png", b"fake-bytes", "image/png")})
        assert resp.status == 403, (
            f"[越权上传图片] 期望 HTTP=403，实际 {resp.brief()}")
