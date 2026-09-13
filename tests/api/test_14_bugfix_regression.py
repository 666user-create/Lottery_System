"""
第⑤步 · 缺陷修复防退化回归（BUG-005 / BUG-006）

背景：
  BUG-005：未注册手机号原返回 114「该手机号未登记为管理员账号」（文案语义错误）
  BUG-006：前端成功码误写为 `!== 0`，而本项目成功码是 200 →
           实测导致 3 处功能完全不可用（工单列表 / 工单处理 / 转人工）

本文件的价值：把两个缺陷的**修复契约**固化为断言，防止再次静默退化。
其中 BUG-006 用**静态检查**覆盖（前端 JS 缺陷无法由接口层捕获），
能抓到：任何页面重新引入 `code !== 0` 之类的成功码误判。
"""
import os
import re
import uuid

import pytest

from common import config
from common.client import assert_code, assert_success

STATIC_DIR = os.path.abspath(
    os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "static"))


@pytest.mark.keypath
class TestBug005VerificationCode:
    """BUG-005：未注册手机号应返回语义明确的 118，而非误导性的 114"""

    def test_unregistered_phone_returns_118(self, ensure_accounts, anon_client):
        """
        未注册手机号发验证码 → 期望 code=118（该手机号尚未注册）。
        能抓到：回退为 114 的旧文案，或误放行（返回 200 却未真正发送）。
        """
        phone = "135" + str(uuid.uuid4().int)[:8]
        resp = anon_client.post(f"/user/verification-code/send?phoneNumber={phone}",
                                timeout=25)
        print(f"    [定性] 未注册手机号 {phone} → code={resp.code} msg={resp.message!r}")
        assert_code(resp, config.Code.PHONE_NOT_REGISTERED,
                    hint="[BUG-005] 未注册手机号应返回 118（原为 114，文案误导）")

    def test_registered_normal_user_still_allowed(self, ensure_accounts, anon_client):
        """
        正向保护：已注册**普通用户**发验证码仍应放行（功能本无缺陷）。
        能抓到：修复 BUG-005 时误伤正常路径（例如误把普通用户判为未注册/非管理员）。
        允许 115/116（冷却/小时限额），但**不得**为 118/114。
        """
        phone = config.TEST_USER["phoneNumber"]
        resp = anon_client.post(f"/user/verification-code/send?phoneNumber={phone}",
                                timeout=25)
        print(f"    [定性] 已注册普通用户 {phone} → code={resp.code} msg={resp.message!r}")
        allowed = (config.Code.SUCCESS, config.Code.SMS_SEND_TOO_FREQUENT,
                   config.Code.SMS_SEND_HOUR_LIMIT)
        assert resp.code in allowed, (
            f"[BUG-005 回归] 已注册普通用户被拒：code={resp.code}（不应为 118/114）\n"
            f" 响应: {resp.brief()}")


@pytest.mark.boundary
class TestBug006FrontendSuccessCode:
    """BUG-006：前端成功码判断必须与后端一致（200）"""

    # 允许出现的前端成功码写法（本项目 CommonResult.success → code=200）
    WRONG_PATTERNS = [
        r"code\s*!==\s*0\b",
        r"code\s*!=\s*0\b",
        r"code\s*===\s*0\b",
        r"code\s*==\s*0\b",
    ]

    def test_no_wrong_success_code_judgement(self):
        """
        静态检查：`static/*.html` 中不得存在 `code !== 0` 之类的成功码误判。
        能抓到：BUG-006 同类回归（该缺陷曾在 3 处导致功能完全不可用）。
        """
        assert os.path.isdir(STATIC_DIR), f"静态资源目录不存在: {STATIC_DIR}"
        offenders = []
        checked = 0
        for name in sorted(os.listdir(STATIC_DIR)):
            if not name.endswith(".html"):
                continue
            path = os.path.join(STATIC_DIR, name)
            with open(path, encoding="utf-8") as f:
                text = f.read()
            checked += 1
            for lineno, line in enumerate(text.splitlines(), start=1):
                for pat in self.WRONG_PATTERNS:
                    if re.search(pat, line):
                        offenders.append(f"{name}:{lineno} → {line.strip()[:90]}")
                        break
        print(f"    [静态检查] 扫描 {checked} 个页面，命中误判 {len(offenders)} 处")
        assert not offenders, (
            "[BUG-006 回归] 前端成功码判断错误（本项目成功码为 200，非 0）：\n  "
            + "\n  ".join(offenders))

    def test_frontend_success_code_is_200(self):
        """
        正向检查：曾出错的 2 个页面必须使用 `code !== 200`。
        能抓到：只删除了错误判断却没补上正确判断（成功码校验缺失）。
        """
        for name in ("admin-handover.html", "chat.html"):
            path = os.path.join(STATIC_DIR, name)
            assert os.path.isfile(path), f"页面不存在: {name}"
            with open(path, encoding="utf-8") as f:
                text = f.read()
            assert "code !== 200" in text, (
                f"[BUG-006 回归] {name} 缺少 `code !== 200` 成功码校验")
        print("    [静态检查] admin-handover.html / chat.html 均含 code !== 200 校验 ✓")

    def test_admin_handover_guards_unauthenticated(self):
        """
        静态检查：工单页在无 token 时必须中断流程（跳转登录页），避免连弹 2 个弹窗。
        能抓到：只改成功码却漏改未登录守卫 → 未登录时仍产生第二个错误弹窗。
        """
        path = os.path.join(STATIC_DIR, "admin-handover.html")
        with open(path, encoding="utf-8") as f:
            text = f.read()
        assert "location.assign('blogin.html')" in text, (
            "[BUG-006 回归] admin-handover.html 未实现未登录跳转登录页")
        assert re.search(r"if\s*\(token\)\s*\{\s*loadList", text), (
            "[BUG-006 回归] admin-handover.html 未对 loadList() 加 token 守卫")
        print("    [静态检查] admin-handover.html 未登录守卫完整 ✓")
