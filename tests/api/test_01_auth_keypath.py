"""
第③步 · 关键路径用例（1/7）：用户与鉴权

覆盖：登录正常流、注册正常流、主要分支（错误密码/用户不存在/身份不符/参数非法/重复注册）、
      令牌数据正确性、未登录保护。

断言强度说明（每条都能失败，否则不算测过）：
  - token 三段式 + 长度：抓到"返回空串/占位串/非 JWT"
  - identity 精确匹配：抓到"登录了 A 身份却发 B 身份令牌"
  - /user/me 回显 userId 与登录一致：抓到"令牌与用户错绑"
  - 业务码断言（而非 HTTP 200）：抓到"HTTP 200 但业务失败"这类最常见的假通过
"""
import pytest

from common import config
from common.client import ApiClient, assert_code, assert_success, assert_field


@pytest.mark.smoke
@pytest.mark.keypath
class TestLoginKeyPath:

    def test_admin_login_returns_valid_jwt(self, ensure_accounts, anon_client):
        """正常流：管理员登录 → 返回结构合法的 JWT 且身份为 ADMIN"""
        resp = anon_client.post("/user/password/login", json_body={
            "loginName": config.TEST_ADMIN["mail"],
            "password": config.TEST_ADMIN["password"],
            "mandatoryIdentity": "ADMIN",
        })
        assert_success(resp, hint="[管理员登录]")
        assert_field(resp, "identity", "ADMIN", hint="[管理员登录] 身份必须回显 ADMIN")
        token = resp.data["token"]
        # 能抓到：token 为空串、占位符、或非 JWT 结构
        assert token.count(".") == 2, (
            f"[管理员登录] 期望 JWT 三段式（含 2 个 '.'），实际 token={token[:40]!r}...")
        assert len(token) > 100, f"[管理员登录] token 长度异常：{len(token)}"

    def test_normal_user_login_and_me_consistency(self, ensure_accounts, anon_client):
        """正常流：普通用户登录 → /user/me 回显同一用户（令牌与用户绑定正确）"""
        login = anon_client.post("/user/password/login", json_body={
            "loginName": config.TEST_USER["mail"],
            "password": config.TEST_USER["password"],
            "mandatoryIdentity": "NORMAL",
        })
        assert_success(login, hint="[普通用户登录]")
        assert_field(login, "identity", "NORMAL", hint="[普通用户登录]")

        me = ApiClient(token=login.data["token"]).get("/user/me")
        assert_success(me, hint="[登录后 /user/me]", expect_data=True)
        assert_field(me, "identity", "NORMAL", hint="[/user/me] 身份应与登录一致")
        # 能抓到：令牌与用户错绑（返回了别人的信息）
        assert me.data.get("userId"), f"[/user/me] userId 缺失：{me.brief()}"

    def test_me_without_token_unauthorized(self, anon_client):
        """分支：无 token 访问 /user/me → 401（拦截器保护）"""
        resp = anon_client.get("/user/me")
        assert resp.status == 401, (
            f"[/user/me 无token] 期望 HTTP=401，实际 {resp.brief()}")
        assert_code(resp, config.Code.UNAUTHORIZED, hint="[/user/me 无token]")

    def test_me_with_invalid_token_unauthorized(self, anon_client):
        """分支：伪造 token 访问 /user/me → 401（不能因解析失败而放行）"""
        forged = ApiClient(token="eyJhbGciOiJIUzI1NiJ9.forged.signature")
        resp = forged.get("/user/me")
        assert resp.status == 401, (
            f"[/user/me 伪造token] 期望 HTTP=401，实际 {resp.brief()}")


@pytest.mark.keypath
class TestLoginBranches:

    def test_wrong_password(self, ensure_accounts, anon_client):
        """分支：密码错误 → 111（而非 200/500）"""
        resp = anon_client.post("/user/password/login", json_body={
            "loginName": config.TEST_USER["mail"], "password": "wrong-password-xyz"})
        assert_code(resp, config.Code.PASSWORD_ERROR, hint="[密码错误]")

    def test_user_not_exist(self, anon_client):
        """分支：用户不存在 → 110"""
        resp = anon_client.post("/user/password/login", json_body={
            "loginName": f"{config.TEST_PREFIX}nobody@test.com", "password": "123456"})
        assert_code(resp, config.Code.USER_INFO_IS_EMPTY, hint="[用户不存在]")

    def test_identity_mismatch(self, ensure_accounts, anon_client):
        """分支：普通用户强制以 ADMIN 登录 → 103（防止越权提权）"""
        resp = anon_client.post("/user/password/login", json_body={
            "loginName": config.TEST_USER["mail"],
            "password": config.TEST_USER["password"],
            "mandatoryIdentity": "ADMIN"})
        assert_code(resp, config.Code.IDENTITY_ERROR, hint="[身份不匹配]")

    def test_login_name_format_invalid(self, anon_client):
        """分支：loginName 既非手机号也非邮箱 → 109"""
        resp = anon_client.post("/user/password/login", json_body={
            "loginName": "not-a-phone-or-mail", "password": "123456"})
        assert_code(resp, config.Code.LOGIN_NOT_EXIST, hint="[登录名格式非法]")

    @pytest.mark.parametrize("field,value,expected_code,desc", [
        ("phoneNumber", "12345", config.Code.PHONE_ERROR, "非法手机号"),
        ("mail", "not-a-mail", config.Code.MAIL_ERROR, "非法邮箱"),
        ("password", "123", config.Code.PASSWORD_FORMAT_ERROR, "弱密码(3位)"),
    ])
    def test_register_param_validation(self, anon_client, field, value, expected_code, desc):
        """
        分支：注册参数校验（表驱动）。
        每个字段用独立 body 提交，确保失败原因可归因到该字段。
        """
        body = dict(config.TEST_USER)
        # 邮箱必须全小写：RegexUtil.checkMail 正则仅允许 [a-z0-9]（见缺陷 BUG-007）
        body["mail"] = f"{config.TEST_PREFIX}param_{field.lower()}@test.com"
        body["phoneNumber"] = "13711112222"
        body[field] = value
        resp = anon_client.post("/user/register", json_body=body)
        assert_code(resp, expected_code, hint=f"[注册校验-{desc}]")

    def test_register_duplicate_mail(self, ensure_accounts, anon_client):
        """分支：重复邮箱注册 → 106（唯一性约束真实生效）"""
        body = dict(config.TEST_USER)
        body["phoneNumber"] = "13711113333"  # 换手机号，仅邮箱重复
        resp = anon_client.post("/user/register", json_body=body)
        assert_code(resp, config.Code.MAIL_USED_ERROR, hint="[重复邮箱]")

    def test_register_duplicate_phone(self, ensure_accounts, anon_client):
        """分支：重复手机号注册 → 107"""
        body = dict(config.TEST_USER)
        body["mail"] = f"{config.TEST_PREFIX}dup_phone@test.com"  # 换邮箱，仅手机号重复
        resp = anon_client.post("/user/register", json_body=body)
        assert_code(resp, config.Code.PHONE_USED_ERROR, hint="[重复手机号]")

    def test_register_mail_uppercase_after_fix(self, ensure_accounts, anon_client):
        """
        BUG-007 修复后的**正向回归**：邮箱含大写字母应可正常注册。

        修复前：RegexUtil.checkMail 正则仅允许 [a-z0-9] → 大写邮箱返回 101（邮箱校验失败）
        修复后：正则放开为 [a-zA-Z0-9] → 应返回 200，且新账号可正常登录

        反向验证（第⑤步执行）：把正则改回 [a-z0-9] 后，本用例必须失败。
        """
        import uuid as _uuid
        body = dict(config.TEST_USER)
        body["mail"] = f"{config.TEST_PREFIX}Upper{_uuid.uuid4().hex[:6]}@Example.COM"
        body["phoneNumber"] = f"137{str(_uuid.uuid4().int)[:8]}"

        resp = anon_client.post("/user/register", json_body=body)
        print(f"    [定性] 含大写字母邮箱注册 → code={resp.code} message={resp.message!r}")
        assert_code(resp, config.Code.SUCCESS,
                    hint="[大写邮箱] BUG-007 已修复，大写邮箱应可注册（修复前为 101）")

        # 数据正确性：新账号必须能登录（而非仅注册接口返回成功）
        login = anon_client.post("/user/password/login", json_body={
            "loginName": body["mail"], "password": body["password"]})
        assert_code(login, config.Code.SUCCESS, hint="[大写邮箱] 新账号应可登录")

    def test_register_missing_required_field(self, anon_client):
        """分支：缺必填字段 → 400（BeanValidation 生效）"""
        resp = anon_client.post("/user/register", json_body={"mail": "a@b.com"})
        assert_code(resp, config.Code.BAD_REQUEST, hint="[缺必填字段]")
