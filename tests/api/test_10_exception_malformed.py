"""
第④步 · 边界与异常（3/6）：非法输入与畸形请求

覆盖：畸形 JSON、空 body、错误 Content-Type、类型错配、SQL 注入、XSS 输入、超长参数。

断言基线（业主规则）：非法输入应返回 **400 参数错误**，
不得返回 500（内部异常直穿）、不得在响应中泄露 SQL/堆栈/包路径（BUG-008）。

能抓到的缺陷：
  - HttpMessageNotReadableException 未被专门处理 → 500 + 异常原文
  - SQL/表结构/包路径泄露（信息泄露 P2）
  - XSS 未转义（存储型）
"""
import uuid

import pytest

from common import config
from common.client import assert_code, assert_no_internal_leak


@pytest.mark.boundary
class TestMalformedRequest:

    @pytest.mark.parametrize("raw,case", [
        ('{"mail": "a@b.com", ', "JSON 截断"),
        ('{invalid json}', "非法 JSON"),
        ('[]', "顶层为数组（应为对象）"),
        ('"just-a-string"', "顶层为字符串"),
        ('', "空 body"),
    ])
    def test_malformed_json(self, ensure_accounts, anon_client, raw, case):
        """
        畸形 JSON → 期望 400；**不得 500**。
        能抓到：HttpMessageNotReadableException 落到 Exception 兜底 → 500 + 异常原文。
        """
        resp = anon_client.post_raw("/user/password/login", raw)
        print(f"    [定性] {case} → HTTP={resp.status} code={resp.code} msg={(resp.message or '')[:80]!r}")
        assert resp.code != config.Code.INTERNAL_SERVER_ERROR, (
            f"[畸形请求-{case}] 返回 500（应 400 参数错误）\n 响应: {resp.brief()}")

    def test_wrong_content_type(self, ensure_accounts, anon_client):
        """错误 Content-Type：text/plain 发送 JSON → 期望 400/415，而非 500"""
        resp = anon_client.post_raw("/user/password/login",
                                    '{"loginName":"a@b.com","password":"123456"}',
                                    content_type="text/plain")
        print(f"    [定性] Content-Type=text/plain → HTTP={resp.status} code={resp.code}")
        assert resp.code != config.Code.INTERNAL_SERVER_ERROR, (
            f"[Content-Type] 期望 400/415，实际 500：{resp.brief()}")

    def test_no_internal_leak_on_error(self, admin_client):
        """
        信息泄露检测（BUG-008）：错误响应中不得出现 SQL/包路径/异常类名/方法签名。

        触发方式：**分页越界**（currentPage=-1）。选择该路径的原因：
          - 修复后：@Min 拦截 → 400 业务码，无任何内部细节
          - 修复前（或 @Min 被移除）：offset 为负 → SQL 语法错误 → 落到兜底分支 →
            响应回显 `### SQL: select * from activity order by id desc limit ?,?` 等完整内部信息
        因此本用例同时覆盖 **C-2（边界校验）** 与 **BUG-008（信息泄露）** 两个修复点。

        反向验证（第⑤步）：注释 PageParam 的 @Min 或恢复兜底 `e.getMessage()` → 本用例必须失败。
        """
        resp = admin_client.get("/activity/find-list",
                                params={"currentPage": -1, "pageSize": 10})
        print(f"    [定性] 分页越界响应 → HTTP={resp.status} code={resp.code} "
              f"msg={(resp.message or '')[:100]!r}")
        assert resp.code != config.Code.INTERNAL_SERVER_ERROR, (
            f"[信息泄露/边界校验] 分页越界返回 500（应 400 参数校验）\n 响应: {resp.brief()}")
        assert_no_internal_leak(
            resp, hint="[信息泄露] 错误响应泄露内部实现细节（SQL/包路径/异常类名）")


@pytest.mark.boundary
class TestInjectionAndXss:

    @pytest.mark.parametrize("payload,case", [
        ("' OR '1'='1", "经典 OR 注入"),
        ("admin'--", "注释截断"),
        ("1; DROP TABLE user;--", "堆叠语句"),
        ("' UNION SELECT 1,2,3--", "UNION 注入"),
    ])
    def test_sql_injection_login(self, ensure_accounts, anon_client, payload, case):
        """
        SQL 注入：登录名注入不得绕过认证、不得 500。
        能抓到：字符串拼接 SQL（本项目用 MyBatis #{} 参数化，预期安全）。
        """
        resp = anon_client.post("/user/password/login", json_body={
            "loginName": payload, "password": payload})
        print(f"    [定性] {case} → code={resp.code}")
        # 关键：绝不能因注入而登录成功
        assert resp.code != config.Code.SUCCESS, (
            f"[SQL注入-{case}] 注入载荷登录成功，存在认证绕过风险！载荷={payload!r}")
        assert resp.code != config.Code.INTERNAL_SERVER_ERROR, (
            f"[SQL注入-{case}] 注入导致 500（SQL 未参数化或异常未处理）\n 响应: {resp.brief()}")

    def test_sql_injection_query_param(self, admin_client):
        """SQL 注入（查询参数）：identity 参数注入不得 500、不得返回异常数据"""
        resp = admin_client.get("/user/base-user/find-list",
                                params={"identity": "' OR '1'='1"})
        print(f"    [定性] identity 注入 → code={resp.code}")
        assert resp.code != config.Code.INTERNAL_SERVER_ERROR, (
            f"[SQL注入-查询参数] 返回 500：{resp.brief()}")

    def test_xss_payload_stored_safely(self, admin_client, faq_cleanup):
        """
        XSS 输入：写入含 <script> 的 FAQ，检查存储/回显是否原样（后端不转义属常见设计，
        但需确认前端有转义；此处记录事实并断言接口不报错）。
        能抓到：接口层对特殊字符处理异常（500）。
        """
        payload = f"{config.TEST_PREFIX}xss_<script>alert(1)</script>_{uuid.uuid4().hex[:6]}"
        resp = admin_client.post("/api/faq/add", json_body={
            "question": payload, "answer": "<img src=x onerror=alert(1)>", "category": "测试"})
        print(f"    [定性] XSS 载荷写入 → code={resp.code}")
        if resp.code == config.Code.SUCCESS and resp.data:
            faq_cleanup(resp.data)
            listing = admin_client.get("/api/faq/list", params={"page": 1, "pageSize": 50})
            matched = [x for x in (listing.data or {}).get("list", []) if x.get("id") == resp.data]
            if matched:
                stored = matched[0].get("question", "")
                print(f"    [定性] 回显内容: {stored[:60]!r}")
                assert "<script>" in stored or "&lt;script&gt;" in stored, (
                    f"[XSS] 存储内容异常丢失：{stored!r}")
        assert resp.code != config.Code.INTERNAL_SERVER_ERROR, (
            f"[XSS] 特殊字符导致 500：{resp.brief()}")


@pytest.mark.boundary
class TestOversizedInput:

    def test_oversized_query_param(self, admin_client):
        """超长查询参数（10KB）→ 不得 500（应有长度限制）"""
        resp = admin_client.get("/user/base-user/find-list",
                                params={"identity": "X" * 10000})
        print(f"    [定性] identity=10000字符 → HTTP={resp.status} code={resp.code}")
        assert resp.code != config.Code.INTERNAL_SERVER_ERROR, (
            f"[超长参数] 返回 500：{resp.brief()}")

    def test_oversized_json_body(self, admin_client):
        """超大 body（1MB 无关字段）→ 不得 500"""
        big = "A" * (1024 * 1024)
        resp = admin_client.post("/api/faq/add",
                                 json_body={"question": big, "answer": big, "category": "测试"})
        print(f"    [定性] 1MB body → HTTP={resp.status} code={resp.code}")
        assert resp.code != config.Code.INTERNAL_SERVER_ERROR, (
            f"[超大body] 返回 500：{resp.brief()}")
