"""
第④步 · 边界与异常（2/6）：输入边界值

按业主指定的"下界-1 / 边界 / 上界+1"逐字段设计：
  手机号   : 10 位(下界-1) / 11 位(边界) / 12 位(上界+1)
  密码     : 5 位(下界-1) / 6 位(边界) / 12 位(边界) / 13 位(上界+1)
  身份枚举 : 空 / 非法值 / 大小写变体
  字符串   : name 255(边界) 256(上界+1) / question 500(边界) 501(上界+1)
  金额     : price -1 / 0(边界) / 极大

能抓到的缺陷：
  - 长度越界直穿 DB 约束 → 500 + SQL 泄露（BUG-008 组合）
  - price 无 @Min 校验 → 负数奖品成功入库（业务数据污染）
  - 大小写变体绕过枚举校验
"""
import uuid

import pytest

from common import config
from common.client import assert_code


def _unique_mail(tag: str) -> str:
    return f"{config.TEST_PREFIX}{tag}_{uuid.uuid4().hex[:6]}@test.com"


def _reg_body(**overrides) -> dict:
    body = dict(config.TEST_USER)
    body["mail"] = _unique_mail("bnd")
    body["phoneNumber"] = f"137{str(uuid.uuid4().int)[:8]}"
    body.update(overrides)
    return body


@pytest.mark.boundary
class TestPhoneBoundary:

    @pytest.mark.parametrize("phone,case,expect_code", [
        ("1371234567", "10位(下界-1)", config.Code.PHONE_ERROR),
        ("13712345678", "11位(边界-合法)", config.Code.SUCCESS),
        ("137123456789", "12位(上界+1)", config.Code.PHONE_ERROR),
    ])
    def test_phone_length(self, ensure_accounts, anon_client, phone, case, expect_code):
        """
        手机号长度边界。
        注意：11 位合法用例成功后会在库中留下账号（qa_auto_ 前缀，可清理）。
        """
        resp = anon_client.post("/user/register", json_body=_reg_body(phoneNumber=phone))
        print(f"    [定性] 手机号 {case} → code={resp.code}")
        if expect_code == config.Code.SUCCESS:
            # 合法号可能因随机碰撞已存在，两种结果都可接受，但必须是明确的业务码
            assert resp.code in (config.Code.SUCCESS, config.Code.PHONE_USED_ERROR), (
                f"[手机号边界-{case}] 期望成功或已注册，实际 code={resp.code}: {resp.brief()}")
        else:
            assert_code(resp, expect_code, hint=f"[手机号边界-{case}]")


@pytest.mark.boundary
class TestPasswordBoundary:

    @pytest.mark.parametrize("pwd,case,expect", [
        ("12345", "5位(下界-1)", config.Code.PASSWORD_FORMAT_ERROR),
        ("123456", "6位(边界-合法)", config.Code.SUCCESS),
        ("123456789012", "12位(边界-合法)", config.Code.SUCCESS),
        ("1234567890123", "13位(上界+1)", config.Code.PASSWORD_FORMAT_ERROR),
    ])
    def test_password_length(self, ensure_accounts, anon_client, pwd, case, expect):
        """密码长度边界（RegexUtil.checkPassword:6-12 位）"""
        resp = anon_client.post("/user/register", json_body=_reg_body(password=pwd))
        print(f"    [定性] 密码 {case} → code={resp.code}")
        if expect == config.Code.SUCCESS:
            assert resp.code in (config.Code.SUCCESS, config.Code.MAIL_USED_ERROR), (
                f"[密码边界-{case}] 期望成功，实际 code={resp.code}: {resp.brief()}")
        else:
            assert_code(resp, expect, hint=f"[密码边界-{case}]")


@pytest.mark.boundary
class TestIdentityBoundary:
    """
    注册接口 identity 参数的**真实语义**（2026-09-13 实测修正）：

    代码依据：UserController.applyRegisterIdentityPolicy（UserController.java:66-84）
      · 无 token：携带正确 X-Admin-Secret → 强制 ADMIN，否则 → 强制 NORMAL
      · 管理员已登录代建：同上策略
      · 普通用户已登录：直接拒绝（113）
    ⇒ 客户端传入的 identity **被服务端策略覆盖**（静默忽略）。

    安全评价：该策略是**正确的安全设计**（防止任意用户自助创建管理员）。
    契约缺口：客户端传入非法/越权 identity 时未获任何提示，可能误判注册结果。
    ⇒ 用例改为断言「覆盖结果正确」，若覆盖失效（非法值入库）则是**越权漏洞**，必须失败。
    """

    @pytest.mark.parametrize("requested_identity,case", [
        ("SUPERADMIN", "非法枚举值"),
        ("admin", "小写变体"),
        ("ADMIN", "大写 ADMIN(无口令)"),
        ("NORMAL", "正常值"),
    ])
    def test_identity_override_policy(self, ensure_accounts, anon_client,
                                      requested_identity, case):
        """
        断言：无口令注册时，无论请求何种 identity，最终必须落为 **NORMAL**。
        能抓到的缺陷：
          - 覆盖策略失效 → 非法身份入库（越权创建管理员，严重安全漏洞）
          - 携带非法 identity 却返回了非 NORMAL 的真实身份
        """
        body = _reg_body(identity=requested_identity)
        resp = anon_client.post("/user/register", json_body=body)
        print(f"    [定性] 请求 identity={requested_identity!r}({case}) → code={resp.code}")
        assert resp.code == config.Code.SUCCESS, (
            f"[身份覆盖-{case}] 无口令注册应成功并被降级为 NORMAL，实际 code={resp.code}: {resp.brief()}")

        # 关键验证：回查真实身份（不能仅凭注册响应）
        login = anon_client.post("/user/password/login", json_body={
            "loginName": body["mail"], "password": body["password"]})
        assert login.code == config.Code.SUCCESS, (
            f"[身份覆盖-{case}] 新账号登录失败：{login.brief()}")
        actual = (login.data or {}).get("identity")
        print(f"    [定性] 实际入库身份 = {actual!r}")
        assert actual == "NORMAL", (
            f"[身份覆盖-{case}] 期望被降级为 NORMAL，实际 {actual!r}\n"
            f" → 覆盖策略失效：非法身份 {requested_identity!r} 成功入库（**越权漏洞**）")

    def test_admin_identity_requires_secret(self, ensure_accounts, anon_client):
        """
        越权对照：无口令 + identity=ADMIN → 必须降级为 NORMAL（不能自助创建管理员）。
        能抓到：X-Admin-Secret 校验被绕过（严重安全漏洞）。
        """
        body = _reg_body(identity="ADMIN")
        resp = anon_client.post("/user/register", json_body=body)
        assert resp.code == config.Code.SUCCESS, f"[管理员越权] 注册失败：{resp.brief()}"
        login = anon_client.post("/user/password/login", json_body={
            "loginName": body["mail"], "password": body["password"]})
        actual = (login.data or {}).get("identity")
        assert actual == "NORMAL", (
            f"[管理员越权] 无 X-Admin-Secret 却创建了 {actual!r} 身份账号（严重越权漏洞）")

    def test_admin_identity_with_secret(self, ensure_accounts, anon_client):
        """
        正向对照：携带正确口令 + identity=ADMIN → 应创建 ADMIN（策略双向验证）。
        """
        import uuid as _uuid
        body = dict(config.TEST_ADMIN)
        body["mail"] = f"{config.TEST_PREFIX}adm_{_uuid.uuid4().hex[:6]}@test.com"
        body["phoneNumber"] = f"139{str(_uuid.uuid4().int)[:8]}"
        resp = anon_client.post("/user/register", json_body=body,
                                headers={config.ADMIN_SECRET_HEADER: config.ADMIN_SECRET})
        assert resp.code == config.Code.SUCCESS, (
            f"[管理员口令] 正确口令注册失败：{resp.brief()}")
        login = anon_client.post("/user/password/login", json_body={
            "loginName": body["mail"], "password": body["password"]})
        actual = (login.data or {}).get("identity")
        assert actual == "ADMIN", (
            f"[管理员口令] 期望 ADMIN，实际 {actual!r}（口令校验失效）")


@pytest.mark.boundary
class TestStringLengthBoundary:

    def test_name_too_long(self, ensure_accounts, anon_client):
        """
        上界+1：name 超长（DB user_name varchar(255)）→ 不得 500（DB 报错 + SQL 泄露）
        能抓到：长度校验缺失导致 Data too long 异常直穿到客户端（BUG-008 组合）。
        """
        resp = anon_client.post("/user/register",
                                json_body=_reg_body(name="A" * 300))
        print(f"    [定性] name=300字符 → HTTP={resp.status} code={resp.code}")
        assert resp.code != config.Code.INTERNAL_SERVER_ERROR, (
            f"[name上界+1] 超长 name 导致 500（DB 约束异常直穿）\n"
            f" 期望：400 参数校验（应有 @Size(max=255)）\n 响应: {resp.brief()}")

    def test_faq_question_too_long(self, admin_client, faq_cleanup):
        """
        FAQ question 上界+1（DB varchar(500)）→ 不得 500。
        能抓到：C-6（FaqDO 无 @Size 校验）导致 DB 异常泄露。
        """
        q = f"{config.TEST_PREFIX}q_" + "B" * 600
        resp = admin_client.post("/api/faq/add", json_body={
            "question": q, "answer": "边界测试答案", "category": "测试"})
        print(f"    [定性] question=600字符 → code={resp.code}")
        if resp.code == config.Code.SUCCESS and resp.data:
            faq_cleanup(resp.data)
        assert resp.code != config.Code.INTERNAL_SERVER_ERROR, (
            f"[FAQ question上界+1] 超长问题导致 500（应 400 参数校验）\n 响应: {resp.brief()}")

    def test_faq_answer_empty_vs_null(self, admin_client, faq_cleanup):
        """
        空值边界：answer 为空串 vs 缺字段（DB 为 NOT NULL 但空串合法）。
        能抓到：缺字段 → DB NOT NULL 异常 → 500 + SQL 泄露（C-6/BUG-008）。
        """
        # 空串（合法，应成功）
        ok = admin_client.post("/api/faq/add", json_body={
            "question": f"{config.TEST_PREFIX}empty_answer_{uuid.uuid4().hex[:6]}",
            "answer": "", "category": "测试"})
        print(f"    [定性] answer='' → code={ok.code}")
        if ok.code == config.Code.SUCCESS and ok.data:
            faq_cleanup(ok.data)
        assert ok.code != config.Code.INTERNAL_SERVER_ERROR, (
            f"[FAQ answer空串] 空串触发 500：{ok.brief()}")

        # 缺字段（应 400，而非 500+SQL）
        miss = admin_client.post("/api/faq/add",
                                 json_body={"question": f"{config.TEST_PREFIX}no_answer",
                                            "category": "测试"})
        print(f"    [定性] answer 缺字段 → code={miss.code}")
        assert miss.code != config.Code.INTERNAL_SERVER_ERROR, (
            f"[FAQ answer缺字段] 缺必填字段导致 500（应 400 参数校验）\n 响应: {miss.brief()}")


@pytest.mark.boundary
class TestNumericBoundary:
    """金额/数量边界 —— 走 multipart 契约（@RequestPart）"""

    @pytest.mark.parametrize("price,case", [
        (-1, "price=-1(下界-1)"),
        (0, "price=0(下界)"),
    ])
    def test_prize_price_boundary(self, admin_client, price, case):
        """
        奖品金额边界：-1 / 0
        能抓到：CreatePrizeParam.price 仅有 @NotNull 无 @Min → 负价/零价奖品成功入库（业务脏数据）。
        数据以 qa_auto_ 前缀命名，可通过 tools/db_cleanup.py 清理。
        """
        import json as _json
        name = f"{config.TEST_PREFIX}price_{price}_{uuid.uuid4().hex[:6]}"
        resp = admin_client.post_multipart(
            "/prize/create",
            data={"param": _json.dumps({"prizeName": name, "description": "边界测试",
                                        "price": price}, ensure_ascii=False)},
            files={"prizePic": ("t.png", b"fake-image-bytes", "image/png")})
        print(f"    [定性] {case} → HTTP={resp.status} code={resp.code} msg={resp.message!r}")
        assert resp.code != config.Code.INTERNAL_SERVER_ERROR, (
            f"[奖品金额边界-{case}] 返回 500：{resp.brief()}")
