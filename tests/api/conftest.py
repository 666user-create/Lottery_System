"""
pytest 全局 fixtures

⚠️ 关键设计：app_fingerprint —— 实例指纹校验
   业主错误 #1（环境/版本错配造假故障）的自动化防线。
   2026-09-13 实测踩坑：8080 被另一项目（BlogSystemApplication）占用，
   抽奖系统特征接口/页面全部 404；若不校验会"测到别人的系统"。
   本 fixture 会：
     1) 校验抽奖系统特征页/接口存在（404 → 明确失败并给出诊断）
     2) 打印被测实例的指纹（便于执行记录留痕）
"""
import sys

import pytest

from common import config
from common.client import ApiClient, assert_code

# ── 编码加固（2026-09-13 修复）────────────────────────────────────────────
# 现象：`python -m pytest -s` 或输出重定向（CI）时，Windows 默认 GBK 控制台/
#       管道无法编码 emoji 与非 GBK 字符 → UnicodeEncodeError → **全部用例 ERROR**。
# 处置：强制 stdout/stderr 为 UTF-8（errors=replace 兜底），并避免在输出中使用 emoji。
for _stream in (sys.stdout, sys.stderr):
    if hasattr(_stream, "reconfigure"):
        try:
            _stream.reconfigure(encoding="utf-8", errors="replace")
        except Exception:  # noqa: BLE001 — 任何环境不支持时静默降级，不影响测试逻辑
            pass


# ==================== 实例指纹（防环境错配） ====================

@pytest.fixture(scope="session", autouse=True)
def app_fingerprint():
    """
    校验被测实例确为「抽奖系统」，并打印指纹。
    失败即终止本次会话（环境不就绪时跑出来的结论都是假故障）。
    """
    print("\n" + "=" * 78)
    print("【被测实例指纹校验】" + config.describe())
    print("=" * 78)

    probe = ApiClient(verbose=False)

    # 1) 静态页特征：抽奖系统登录页必须存在（AppConfig 白名单）
    try:
        page = probe.get("/user-login.html", timeout=10)
    except Exception as e:
        pytest.fail(
            f"无法连接被测服务 {config.BASE_URL}：{e}\n"
            f" → 请先启动抽奖系统：mvnw.cmd spring-boot:run", pytrace=False)

    if page.status == 404:
        pytest.fail(
            f"实例指纹校验失败：{config.BASE_URL}/user-login.html 返回 404。\n"
            f" 该端口上运行的可能不是「抽奖系统」（实测 2026-09-13 曾为 BlogSystemApplication）。\n"
            f" 请核对：netstat -ano | findstr :8080  并确认占用进程，或设置 LOTTERY_BASE_URL 指向正确端口。\n"
            f" 响应: {page.brief()}", pytrace=False)
    assert page.status == 200, f"登录页异常：{page.brief()}"

    # 2) 接口特征：抽奖系统管理接口无 token 必返 401（而非 404/其他）
    api_probe = probe.get("/api/faq/list", params={"page": 1, "pageSize": 10}, timeout=10)
    assert api_probe.status == 401, (
        f"接口指纹校验失败：GET /api/faq/list 期望 HTTP=401（未登录），实际 HTTP={api_probe.status}。\n"
        f" 该实例可能不是抽奖系统。响应: {api_probe.brief()}")

    print(f"[OK] 指纹校验通过：连到的是抽奖系统（{config.BASE_URL}）")
    print(f"   登录页 HTTP={page.status} | 管理接口无token HTTP={api_probe.status}(code={api_probe.code})")
    return True


# ==================== 账号与令牌 ====================

def _login(client: ApiClient, account: dict, is_admin: bool):
    """登录；若账号不存在（110）则注册后重试（幂等，避免测试数据累积）"""
    payload = {
        "loginName": account["mail"],
        "password": account["password"],
        "mandatoryIdentity": account["identity"],
    }
    resp = client.post("/user/password/login", json_body=payload)
    if resp.code == config.Code.USER_INFO_IS_EMPTY:
        # 账号不存在 → 注册（管理员需携带口令）
        headers = {config.ADMIN_SECRET_HEADER: config.ADMIN_SECRET} if is_admin else None
        reg = client.post("/user/register", json_body=account, headers=headers)
        assert_code(reg, config.Code.SUCCESS, hint="[fixture] 注册测试账号")
        assert reg.data is not None and reg.data.get("userId"), (
            f"[fixture] 注册未返回 userId：{reg.brief()}")
        resp = client.post("/user/password/login", json_body=payload)

    assert_code(resp, config.Code.SUCCESS, hint="[fixture] 登录测试账号")
    token = (resp.data or {}).get("token")
    assert token and len(token) > 50, (
        f"[fixture] token 异常：{token!r}\n 响应: {resp.brief()}")
    assert (resp.data or {}).get("identity") == account["identity"], (
        f"[fixture] 身份不符：期望 {account['identity']}，实际 {resp.data.get('identity')}")
    return token


@pytest.fixture(scope="session")
def ensure_accounts(app_fingerprint):
    """
    确保两个测试账号**已存在**（幂等：存在则登录校验，不存在则注册）。

    为什么需要它（2026-09-13 首轮执行暴露的测试缺陷）：
      部分用例自身不持有 token（如"管理员登录返回合法 JWT"），
      若只依赖 admin_client/user_client fixture，用例执行顺序靠前时账号尚未创建，
      登录返回 110(用户信息为空) → 误判为产品缺陷。
      本 fixture 把"账号准备"与"令牌获取"解耦，消除隐式顺序依赖。
    """
    _login(ApiClient(verbose=False), config.TEST_ADMIN, is_admin=True)
    _login(ApiClient(verbose=False), config.TEST_USER, is_admin=False)
    print("   [fixture] 测试账号已就绪（qa_auto_admin / qa_auto_user）")
    return True


@pytest.fixture(scope="session")
def admin_client(app_fingerprint, ensure_accounts) -> ApiClient:
    """管理员客户端（ADMIN 身份，用于管理接口）"""
    token = _login(ApiClient(), config.TEST_ADMIN, is_admin=True)
    print(f"   [fixture] 管理员令牌就绪: {token[:24]}... (len={len(token)})")
    return ApiClient(token=token)


@pytest.fixture(scope="session")
def user_client(app_fingerprint, ensure_accounts) -> ApiClient:
    """普通用户客户端（NORMAL 身份，用于用户端接口与越权用例）"""
    token = _login(ApiClient(), config.TEST_USER, is_admin=False)
    print(f"   [fixture] 普通用户令牌就绪: {token[:24]}... (len={len(token)})")
    return ApiClient(token=token)


@pytest.fixture(scope="session")
def anon_client(app_fingerprint) -> ApiClient:
    """匿名客户端（无 token，用于鉴权/公开接口用例）"""
    return ApiClient(token=None)


# ==================== 数据清理（跑完就清，绝不残留） ====================

@pytest.fixture
def faq_cleanup(admin_client):
    """
    FAQ 清理器：用例内新增的 FAQ 在用例结束后自动删除。
    用法：
        def test_x(admin_client, faq_cleanup):
            fid = admin_client.post("/api/faq/add", ...).data
            faq_cleanup(fid)
    """
    created = []

    def register(faq_id: int):
        created.append(faq_id)
        return faq_id

    yield register

    for fid in created:
        try:
            resp = admin_client.delete("/api/faq/delete", params={"id": fid})
            print(f"    [cleanup] 删除测试 FAQ id={fid} → code={resp.code}")
        except Exception as e:  # 清理失败不掩盖用例结果，但必须显式告警
            print(f"    [cleanup][WARN] 删除 FAQ id={fid} 失败：{e}")


@pytest.fixture
def handover_cleanup(admin_client):
    """工单清理器：记录创建的工单 id 并在结束后置为 CLOSED（无删除接口，故关闭+标注）"""
    created = []

    def register(hid: int):
        created.append(hid)
        return hid

    yield register

    for hid in created:
        try:
            resp = admin_client.post("/api/handover/process", json_body={
                "id": hid, "status": "CLOSED",
                "handler": "QA自动化清理", "remark": f"{config.TEST_PREFIX}cleanup"})
            print(f"    [cleanup] 关闭测试工单 id={hid} → code={resp.code}")
        except Exception as e:
            print(f"    [cleanup][WARN] 关闭工单 id={hid} 失败：{e}")
