"""
第③步 · 关键路径用例（7/7）：鉴权矩阵契约

来源：LoginInterceptor.java:39-46（adminOnly）+ AppConfig.java:17-31（excludes）

断言基线（与契约表一致）：
  - 管理接口：匿名 → HTTP 401；普通用户 → HTTP 403；管理员 → 业务可达
  - 公开接口：匿名可直达（不得返回 401）

能抓到的缺陷：
  - 权限白名单被误扩大（管理接口对匿名/普通用户开放）
  - 鉴权从"401/403"退化为"业务层拦截"（HTTP 200 + 业务码），前端跳转逻辑失效
"""
import pytest

from common import config
from common.client import assert_success


def _call(client, method: str, path: str):
    """按方法派发最小化请求（鉴权在拦截器层先于参数校验，故无需构造完整参数）"""
    if method == "GET":
        return client.get(path)
    if method == "POST":
        return client.post(path, json_body={})
    if method == "DELETE":
        return client.delete(path)
    raise ValueError(f"未支持的方法: {method}")


@pytest.mark.smoke
@pytest.mark.keypath
class TestAuthMatrix:

    @pytest.mark.parametrize("method,path", config.ADMIN_ONLY_PATHS,
                             ids=[f"{m} {p}" for m, p in config.ADMIN_ONLY_PATHS])
    def test_admin_path_rejects_anonymous(self, anon_client, method, path):
        """管理接口：匿名访问必须 401（而非 200/403/404）"""
        resp = _call(anon_client, method, path)
        assert resp.status == 401, (
            f"[鉴权矩阵] {method} {path} 匿名期望 HTTP=401，实际 {resp.brief()}\n"
            f" → 若为 200 说明权限白名单被误扩大（安全隐患）")

    @pytest.mark.parametrize("method,path", config.ADMIN_ONLY_PATHS,
                             ids=[f"{m} {p}" for m, p in config.ADMIN_ONLY_PATHS])
    def test_admin_path_rejects_normal_user(self, user_client, method, path):
        """管理接口：普通用户访问必须 403（登录但权限不足）"""
        resp = _call(user_client, method, path)
        assert resp.status == 403, (
            f"[鉴权矩阵] {method} {path} 普通用户期望 HTTP=403，实际 {resp.brief()}\n"
            f" → 若为 200 说明越权漏洞；若为 401 说明登录态未被识别")

    @pytest.mark.parametrize("method,path", config.PUBLIC_PATHS,
                             ids=[f"{m} {p}" for m, p in config.PUBLIC_PATHS])
    def test_public_path_not_blocked_by_auth(self, anon_client, method, path):
        """公开接口：匿名不得被鉴权拦截（401），应为业务响应"""
        resp = _call(anon_client, method, path)
        assert resp.status != 401, (
            f"[鉴权矩阵] {method} {path} 属免登录白名单，但被返回 401：{resp.brief()}")

    def test_admin_can_reach_admin_path(self, admin_client):
        """管理员：可达管理接口（正向对照，避免"全员 403"也能通过矩阵测试的假象）"""
        resp = admin_client.get("/api/faq/list", params={"page": 1, "pageSize": 1})
        assert_success(resp, hint="[鉴权矩阵-管理员正向]")
