"""
第④步 · 边界与异常（4/6）：并发与重复提交

覆盖：并发注册同邮箱/同手机号（唯一性校验竞态）、并发创建工单、重复提交幂等性。

能抓到的缺陷：
  - "先查后插"非原子 → 并发下重复账号入库（唯一约束被绕过）
  - 并发下部分请求静默失败（未能返回明确错误码）
  - 重复提交产生重复业务数据

数据说明：并发用例创建的数据均带 qa_auto_ 前缀，可用 tools/db_cleanup.py 清理。
"""
import uuid
from concurrent.futures import ThreadPoolExecutor

import pytest
import allure

from common import config
from common.client import assert_success


def _unique(tag: str) -> str:
    return f"{config.TEST_PREFIX}{tag}_{uuid.uuid4().hex[:8]}"


@allure.feature("边界与异常")
@allure.story("并发注册/并发工单")
@pytest.mark.boundary
class TestConcurrency:

    def test_concurrent_register_same_mail(self, ensure_accounts, anon_client):
        """
        并发注册**同一邮箱**（5 并发）→ 期望恰好 1 个成功，其余返回明确业务码（106 已注册）。
        能抓到：唯一性校验竞态 → 重复账号入库。
        """
        tag = uuid.uuid4().hex[:8]
        mail = f"{config.TEST_PREFIX}race_{tag}@test.com"
        base_phone = str(uuid.uuid4().int)[:8]

        def do_register(i: int):
            body = dict(config.TEST_USER)
            body["mail"] = mail
            body["phoneNumber"] = f"137{base_phone}"  # 邮箱相同、手机号相同 → 双重唯一约束
            return anon_client.post("/user/register", json_body=body)

        with ThreadPoolExecutor(max_workers=5) as pool:
            results = [f.result() for f in [pool.submit(do_register, i) for i in range(5)]]

        codes = [r.code for r in results]
        success = [c for c in codes if c == config.Code.SUCCESS]
        print(f"    [定性] 并发注册同邮箱 5 次 → 各响应码 {codes}")
        assert len(success) == 1, (
            f"[并发注册] 期望恰好 1 个成功，实际 {len(success)} 个成功（codes={codes}）\n"
            f" → 唯一性校验存在竞态，可能产生重复账号")

    def test_concurrent_register_same_phone(self, ensure_accounts, anon_client):
        """
        并发注册**同一手机号**（不同邮箱，4 并发）→ 期望恰好 1 个成功。
        能抓到：手机号唯一性竞态。
        """
        tag = uuid.uuid4().hex[:6]
        phone = f"138{str(uuid.uuid4().int)[:8]}"

        def do_register(i: int):
            body = dict(config.TEST_USER)
            body["mail"] = f"{config.TEST_PREFIX}racep_{tag}_{i}@test.com"
            body["phoneNumber"] = phone
            return anon_client.post("/user/register", json_body=body)

        with ThreadPoolExecutor(max_workers=4) as pool:
            results = [f.result() for f in [pool.submit(do_register, i) for i in range(4)]]

        codes = [r.code for r in results]
        success = [c for c in codes if c == config.Code.SUCCESS]
        print(f"    [定性] 并发注册同手机号 4 次 → 各响应码 {codes}")
        assert len(success) == 1, (
            f"[并发注册-手机号] 期望恰好 1 个成功，实际 {len(success)} 个（codes={codes}）")

    def test_concurrent_create_handover(self, user_client, admin_client, handover_cleanup):
        """
        并发创建工单（5 并发）→ 全部应成功且 id 互不相同（不得丢请求/返回重复 id）。
        能抓到：并发写入丢失、id 重复、部分请求静默失败。
        """
        sid_prefix = _unique("concsess")

        def do_create(i: int):
            return user_client.post("/api/handover/create",
                                    json_body={"sessionId": f"{sid_prefix}_{i}",
                                               "question": f"{config.TEST_PREFIX}并发工单{i}"})

        with ThreadPoolExecutor(max_workers=5) as pool:
            results = [f.result() for f in [pool.submit(do_create, i) for i in range(5)]]

        codes = [r.code for r in results]
        ids = [r.data for r in results if r.code == config.Code.SUCCESS]
        print(f"    [定性] 并发创建工单 5 次 → codes={codes}, ids={ids}")

        assert all(c == config.Code.SUCCESS for c in codes), (
            f"[并发工单] 期望全部成功，实际 codes={codes}")
        assert len(set(ids)) == len(ids), (
            f"[并发工单] 出现重复 id：{ids}（并发写入异常）")
        for hid in ids:
            handover_cleanup(hid)

    def test_repeated_submit_idempotency(self, admin_client, faq_cleanup):
        """
        重复提交（同一 FAQ 连续提交 3 次）→ 记录行为。
        FAQ 无幂等键，预期产生 3 条记录（属设计选择，非缺陷）；断言：不得静默丢请求或报错。
        """
        q = f"{config.TEST_PREFIX}dup_{uuid.uuid4().hex[:8]}"
        results = [admin_client.post("/api/faq/add",
                                     json_body={"question": q, "answer": "重复提交测试",
                                                "category": "测试"}) for _ in range(3)]
        ids = [r.data for r in results if r.code == config.Code.SUCCESS]
        for fid in ids:
            faq_cleanup(fid)
        print(f"    [定性] 重复提交 3 次 → ids={ids}")
        assert len(ids) == 3, (
            f"[重复提交] 期望 3 次均成功（无幂等约束），实际成功 {len(ids)} 次")
        assert len(set(ids)) == 3, f"[重复提交] id 重复：{ids}"
