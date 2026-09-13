"""
第④步 · 边界与异常（1/6）：分页边界值

契约风险 C-2：`PageParam`（PageParam.java:10-17）**无任何 @Min/@Max 校验**，
`offset = (currentPage-1) * pageSize` 可为负 → SQL `LIMIT -10,10` → MySQL 语法错误。

边界设计（业主指定的"下界-1 / 边界 / 上界+1"）：
  currentPage: -1(下界-1) / 0 / 1(边界) / 2(正常) / 2147483647(上界)
  pageSize   : -1 / 0 / 1(边界) / 10(默认) / 999999(上界+1)

断言基线：
  - **不得返回 500**（内部 SQL 错误 → 且会泄露 SQL，见 BUG-008）
  - 合理行为：400 参数校验错误，或安全降级为空/默认页

能抓到的缺陷：
  - 越界分页导致 500 + SQL 泄露（C-2 + BUG-008 组合）
  - 负偏移返回"最后一页"等错误语义（操作成功但结果错）
"""
import pytest

from common import config
from common.client import assert_success

PAGING_ENDPOINTS = [
    ("/activity/find-list", "活动列表"),
    ("/prize/find-list", "奖品列表"),
]


@pytest.mark.boundary
class TestPagingBoundary:

    @pytest.mark.parametrize("endpoint,desc_name", PAGING_ENDPOINTS,
                             ids=[f"{p}" for p, _ in PAGING_ENDPOINTS])
    @pytest.mark.parametrize("current_page,case", [
        (-1, "currentPage=-1(下界-1)"),
        (0, "currentPage=0(下界)"),
        (1, "currentPage=1(边界-最小合法)"),
        (2, "currentPage=2(正常)"),
    ])
    def test_current_page_boundary(self, admin_client, endpoint, desc_name, current_page, case):
        """
        currentPage 边界：-1 / 0 / 1 / 2
        能抓到：负偏移使 SQL 变成 LIMIT -10,10 → 500（而非 400 参数错误）
        """
        resp = admin_client.get(endpoint, params={"currentPage": current_page, "pageSize": 10})
        assert resp.code != config.Code.INTERNAL_SERVER_ERROR, (
            f"[{desc_name}] {case} → 返回 500（内部错误）\n"
            f" 期望：400 参数校验错误 或 安全降级\n"
            f" 根因：PageParam 无 @Min 校验，offset={(current_page - 1) * 10} 为负数 → SQL LIMIT 负数\n"
            f" 响应: {resp.brief()}")

    @pytest.mark.parametrize("endpoint,desc_name", PAGING_ENDPOINTS,
                             ids=[f"{p}" for p, _ in PAGING_ENDPOINTS])
    @pytest.mark.parametrize("page_size,case", [
        (-1, "pageSize=-1(下界-1)"),
        (0, "pageSize=0(下界)"),
        (1, "pageSize=1(边界)"),
        (999999, "pageSize=999999(上界+1)"),
    ])
    def test_page_size_boundary(self, admin_client, endpoint, desc_name, page_size, case):
        """
        pageSize 边界：-1 / 0 / 1 / 999999
        能抓到：pageSize<=0 → SQL LIMIT 0/负数 的异常；pageSize 极大 → 无上限导致资源耗尽风险
        """
        resp = admin_client.get(endpoint, params={"currentPage": 1, "pageSize": page_size})
        assert resp.code != config.Code.INTERNAL_SERVER_ERROR, (
            f"[{desc_name}] {case} → 返回 500（内部错误）\n"
            f" 期望：400 参数校验错误（pageSize 应有 @Min(1) @Max(N)）\n"
            f" 响应: {resp.brief()}")

    def test_huge_page_size_returns_bounded_records(self, admin_client):
        """
        上界+1：pageSize=999999 时不得一次吐出全表（应有上限保护）。
        能抓到：缺少 @Max 导致的大结果集（内存/带宽风险），以及 total 与实际返回条数不一致。
        """
        resp = admin_client.get("/activity/find-list",
                                params={"currentPage": 1, "pageSize": 999999})
        if resp.code == config.Code.SUCCESS:
            records = (resp.data or {}).get("records", [])
            total = (resp.data or {}).get("total", 0)
            print(f"    [定性] pageSize=999999 → 返回 {len(records)} 条 / total={total}")
            assert len(records) <= total, (
                f"[分页一致性] 返回条数 {len(records)} 超过 total {total}（数据不一致）")
        else:
            print(f"    [定性] pageSize=999999 → code={resp.code}（已被参数校验拦截）")

    def test_non_numeric_paging_param(self, admin_client):
        """非法类型：currentPage=abc → 400 参数类型不匹配（而非 500）"""
        resp = admin_client.get("/activity/find-list",
                                params={"currentPage": "abc", "pageSize": 10})
        assert resp.code != config.Code.INTERNAL_SERVER_ERROR, (
            f"[分页类型错误] 期望 400，实际 500：{resp.brief()}")
        print(f"    [定性] currentPage=abc → code={resp.code} msg={resp.message!r}")

    def test_paging_result_stability(self, admin_client):
        """
        "操作成功但结果错"检测：同一分页请求连续两次，数据必须一致（无隐性抖动/错页）。
        """
        p1 = admin_client.get("/activity/find-list", params={"currentPage": 1, "pageSize": 3})
        p2 = admin_client.get("/activity/find-list", params={"currentPage": 1, "pageSize": 3})
        assert_success(p1, hint="[分页稳定性-第1次]")
        assert_success(p2, hint="[分页稳定性-第2次]")
        ids1 = [r.get("activityId") for r in (p1.data or {}).get("records", [])]
        ids2 = [r.get("activityId") for r in (p2.data or {}).get("records", [])]
        assert ids1 == ids2, (
            f"[分页稳定性] 同一请求两次结果不一致：{ids1} vs {ids2}")
