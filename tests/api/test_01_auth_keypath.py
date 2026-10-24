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
import allure

from common import config
from common.client import ApiClient, assert_code, assert_success, assert_field


@allure.feature("用户与认证")
@allure.story("密码/短信登录关键路径")
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


@allure.feature("用户与认证")
@allure.story("用户详情查询（管理端「查看详情」）")
@pytest.mark.keypath
class TestUserDetail:
    """
    新增端点 GET /user/base-user/find-detail（2026-10-20，配套 user-detail.html 页面）。

    契约（源码 + 实测双重确认）：
      - UserController.java:162-171 —— @RequestParam("userId") 必填，仅 ADMIN
      - LoginInterceptor.java:89 —— 精确登记为 adminOnly
      - ServiceErrorCodeConstants.java:47 —— USER_NOT_EXIST = 133
      - 返回 UserDetailResult 15 字段；密码属凭证，任何情况下都不返回

    断言强度说明（每条都能失败，否则不算测过）：
      - 与列表接口逐字段互证：抓到"详情与列表数据不一致"（读了不同表/缓存串号）
      - 密码字段绝不出现：抓到"凭证泄露给前端"
      - 不存在 → 133 而非 200+null：抓到"假成功"（与「200+null」同类风险）
      - 普通用户 403 / 匿名 401：抓到 adminOnly 规则被误删或路径前缀写错
    """

    @staticmethod
    def _first_normal_user_id(admin_client):
        """从列表接口取一个真实存在的 NORMAL 用户 id（详情与列表必须同源互证）"""
        resp = admin_client.get("/user/base-user/find-list", params={"identity": "NORMAL"})
        assert_success(resp, hint="[前置] 管理员查用户列表", expect_data=True)
        rows = resp.data or []
        assert rows, "[前置] 用户列表为空，无法构造详情用例"
        return rows[0]["userId"], rows[0]

    def test_detail_returns_all_fields_and_no_password(self, admin_client):
        """正常流：管理员查详情 → 15 字段齐全、密码绝不返回"""
        uid, _ = self._first_normal_user_id(admin_client)
        resp = admin_client.get("/user/base-user/find-detail", params={"userId": uid})
        assert_success(resp, hint="[详情 正常流]", expect_data=True)
        d = resp.data

        # 字段齐全（缺任何一个都算契约破坏）
        for key in ("userId", "userName", "email", "phoneNumber", "identity",
                    "identityText", "status", "statusText", "receiverName",
                    "receiverPhone", "receiverAddress", "gmtCreate", "gmtModified"):
            assert key in d, f"[详情 正常流] 缺字段 {key}，实际返回 {sorted(d)}"

        assert_field(resp, "userId", uid, hint="[详情 正常流] userId 必须回显请求值")

        # 凭证泄露防线：密码字段在任何层级都不得出现
        assert "password" not in d, f"[详情 正常流] 响应泄露 password 字段：{d}"
        assert "password" not in resp.text.lower(), (
            f"[详情 正常流] 响应文本中出现 password：{resp.brief()}")

    def test_detail_matches_list_consistency(self, admin_client):
        """数据正确性：详情字段与列表接口同源互证（抓数据不一致/读错表）"""
        uid, row = self._first_normal_user_id(admin_client)
        resp = admin_client.get("/user/base-user/find-detail", params={"userId": uid})
        assert_success(resp, hint="[详情 一致性]", expect_data=True)
        d = resp.data

        # 列表 BaseUserInfoResult{userId,userName,identity,status} 应与详情逐字段一致
        assert_field(resp, "userName", row["userName"], hint="[详情 一致性] userName 与列表不符")
        assert_field(resp, "identity", row["identity"], hint="[详情 一致性] identity 与列表不符")
        assert_field(resp, "status", row["status"], hint="[详情 一致性] status 与列表不符")

    def test_detail_identity_and_status_text_mapping(self, admin_client):
        """派生字段：identityText/statusText 中文映射不能为空且须与枚举对应"""
        uid, row = self._first_normal_user_id(admin_client)
        resp = admin_client.get("/user/base-user/find-detail", params={"userId": uid})
        assert_success(resp, hint="[详情 中文映射]", expect_data=True)
        d = resp.data
        # NORMAL → 普通用户；ACTIVE → 正常（空值会让前端渲染成空白）
        assert d.get("identityText"), f"[详情 中文映射] identityText 为空：{d}"
        assert d.get("statusText"), f"[详情 中文映射] statusText 为空：{d}"

    def test_detail_nonexistent_user_returns_133(self, admin_client):
        """分支：userId 不存在 → HTTP 200 + 133（而非 200+null 的假成功）"""
        resp = admin_client.get("/user/base-user/find-detail",
                                params={"userId": 99999999})
        assert resp.status == 200, (
            f"[详情 不存在] 业务失败应为 HTTP=200 + body.code，实际 HTTP={resp.status}")
        assert_code(resp, 133, hint="[详情 不存在] 期望 USER_NOT_EXIST=133")
        assert resp.data is None, (
            f"[详情 不存在] 期望 data=null，实际 {resp.data!r}（疑似假成功）")

    def test_detail_missing_user_id_param(self, admin_client):
        """分支：缺 userId 参数 → HTTP 200 + 400（@RequestParam 校验经全局异常处理器）"""
        resp = admin_client.get("/user/base-user/find-detail")
        assert resp.status == 200, (
            f"[详情 缺参] 业务失败应为 HTTP=200 + body.code，实际 HTTP={resp.status}")
        assert_code(resp, config.Code.BAD_REQUEST, hint="[详情 缺参] 期望 400")

    def test_detail_forbidden_for_normal_user(self, user_client):
        """越权：普通用户查他人详情 → 真 HTTP 403（adminOnly 生效）"""
        resp = user_client.get("/user/base-user/find-detail", params={"userId": 1})
        assert resp.status == 403, (
            f"[详情 越权] 期望 HTTP=403，实际 {resp.brief()}")
        assert_code(resp, config.Code.FORBIDDEN, hint="[详情 越权] 期望业务码 403")

    def test_detail_unauthorized_without_token(self, anon_client):
        """越权：匿名查详情 → 真 HTTP 401（拦截器保护，不能因缺 token 而 500）"""
        resp = anon_client.get("/user/base-user/find-detail", params={"userId": 1})
        assert resp.status == 401, (
            f"[详情 匿名] 期望 HTTP=401，实际 {resp.brief()}")
        assert_code(resp, config.Code.UNAUTHORIZED, hint="[详情 匿名] 期望业务码 401")

    def test_detail_with_forged_token(self, anon_client):
        """越权：伪造 token → 401（不能因解析失败而放行）"""
        forged = ApiClient(token="eyJhbGciOiJIUzI1NiJ9.forged.signature")
        resp = forged.get("/user/base-user/find-detail", params={"userId": 1})
        assert resp.status == 401, (
            f"[详情 伪造token] 期望 HTTP=401，实际 {resp.brief()}")


@allure.feature("用户与认证")
@allure.story("登录失败分支")
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
        # 邮箱必须全小写：RegexUtil.checkMail 正则仅允许 [a-z0-9]（见缺陷 BUG-003）
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
        BUG-003 修复后的**正向回归**：邮箱含大写字母应可正常注册。

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
                    hint="[大写邮箱] BUG-003 已修复，大写邮箱应可注册（修复前为 101）")

        # 数据正确性：新账号必须能登录（而非仅注册接口返回成功）
        login = anon_client.post("/user/password/login", json_body={
            "loginName": body["mail"], "password": body["password"]})
        assert_code(login, config.Code.SUCCESS, hint="[大写邮箱] 新账号应可登录")

    def test_register_missing_required_field(self, anon_client):
        """分支：缺必填字段 → 400（BeanValidation 生效）"""
        resp = anon_client.post("/user/register", json_body={"mail": "a@b.com"})
        assert_code(resp, config.Code.BAD_REQUEST, hint="[缺必填字段]")
