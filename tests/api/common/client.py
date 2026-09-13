"""
HTTP 客户端与断言工具

设计原则（对应业主的错误清单）：
  #3 假通过断言 → 所有断言默认校验业务码 body.code，而非仅 HTTP 200；
                  失败时抛出带完整响应上下文的 AssertionError，可定位、可反向验证。
  #4 数据污染   → 统一 TEST_PREFIX 前缀 + 上下文管理器自动清理。
  #9 编码       → 全工程 UTF-8，JSON 显式 ensure_ascii=False。
"""
import json
from typing import Any, Optional

import requests

from . import config


class ApiResponse:
    """响应包装：保留原始响应 + 解析后的 JSON，便于断言与排查"""

    def __init__(self, resp: requests.Response, elapsed_ms: float):
        self.raw = resp
        self.status = resp.status_code
        self.elapsed_ms = elapsed_ms
        self.text = resp.text
        try:
            self.json: Optional[dict] = resp.json()
        except ValueError:
            self.json = None  # 非 JSON 响应（如 /prize/pic/upload 的裸 String）

    @property
    def code(self) -> Optional[int]:
        return None if self.json is None else self.json.get("code")

    @property
    def data(self) -> Any:
        return None if self.json is None else self.json.get("data")

    @property
    def message(self) -> Optional[str]:
        return None if self.json is None else self.json.get("message")

    def brief(self) -> str:
        body = self.text if len(self.text) <= 300 else self.text[:300] + "...(truncated)"
        return (f"HTTP={self.status} code={self.code} msg={self.message!r} "
                f"elapsed={self.elapsed_ms:.0f}ms body={body}")

    def __repr__(self) -> str:
        return f"<ApiResponse {self.brief()}>"


class ApiClient:
    """轻量 HTTP 客户端：自动携带 token，集中记录耗时"""

    def __init__(self, base_url: str = None, token: str = None, verbose: bool = True):
        self.base_url = (base_url or config.BASE_URL).rstrip("/")
        self.token = token
        self.verbose = verbose
        self.session = requests.Session()

    # ---------- 内部 ----------
    def _headers(self, extra: dict = None) -> dict:
        h = {}
        if self.token:
            h["token"] = self.token
        if extra:
            h.update(extra)
        return h

    def _log(self, method: str, path: str, resp: ApiResponse):
        if self.verbose:
            print(f"    → {method} {path} | {resp.brief()}")

    def _send(self, method: str, path: str, *, params=None, json_body=None,
              headers=None, files=None, data=None, timeout=None) -> ApiResponse:
        import time
        url = f"{self.base_url}{path}"
        t0 = time.time()
        resp = self.session.request(
            method=method, url=url, params=params, json=json_body,
            headers=self._headers(headers), files=files, data=data,
            timeout=timeout or config.HTTP_TIMEOUT,
        )
        wrapped = ApiResponse(resp, (time.time() - t0) * 1000)
        self._log(method, path, wrapped)
        return wrapped

    # ---------- 公开方法 ----------
    def get(self, path, params=None, headers=None, timeout=None) -> ApiResponse:
        return self._send("GET", path, params=params, headers=headers, timeout=timeout)

    def post(self, path, json_body=None, headers=None, params=None, timeout=None) -> ApiResponse:
        return self._send("POST", path, json_body=json_body, headers=headers,
                          params=params, timeout=timeout)

    def delete(self, path, params=None, headers=None, timeout=None) -> ApiResponse:
        return self._send("DELETE", path, params=params, headers=headers, timeout=timeout)

    def post_multipart(self, path, files=None, data=None, headers=None, timeout=None) -> ApiResponse:
        """multipart/form-data（/prize/create 的契约：@RequestPart）"""
        return self._send("POST", path, files=files, data=data, headers=headers, timeout=timeout)

    def post_raw(self, path, raw_body, content_type: str = "application/json",
                 timeout=None) -> ApiResponse:
        """
        发送**原始 body**（不做 JSON 序列化）——用于畸形 JSON / 非法 Content-Type 等
        非法输入用例；能抓到"解析异常未被专门处理 → 500 + 内部细节泄露"。
        """
        import time
        url = f"{self.base_url}{path}"
        headers = self._headers({"Content-Type": content_type})
        t0 = time.time()
        body = raw_body.encode("utf-8") if isinstance(raw_body, str) else raw_body
        resp = self.session.post(url, data=body, headers=headers,
                                 timeout=timeout or config.HTTP_TIMEOUT)
        wrapped = ApiResponse(resp, (time.time() - t0) * 1000)
        self._log("POST-RAW", path, wrapped)
        return wrapped

    def get_raw(self, path, headers=None, timeout=None) -> ApiResponse:
        """GET 且不附加任何查询参数（用于验证缺参行为）"""
        return self._send("GET", path, headers=headers, timeout=timeout)

    def with_token(self, token: str) -> "ApiClient":
        return ApiClient(self.base_url, token, self.verbose)


# ==================== 断言工具（必须能失败，否则不算测过） ====================

def assert_http(resp: ApiResponse, expected: int, hint: str = ""):
    """断 HTTP 状态码"""
    assert resp.status == expected, (
        f"{hint}\n 期望 HTTP={expected}，实际 HTTP={resp.status}\n 响应: {resp.brief()}")


def assert_code(resp: ApiResponse, expected: int, hint: str = ""):
    """
    断业务码（核心断言）。
    能抓到的缺陷：HTTP 200 但业务失败（参数校验、鉴权、业务规则不通过）——
    若只断 HTTP 200，这类缺陷会全部漏过。
    """
    assert resp.json is not None, (
        f"{hint}\n 期望 JSON 响应（含 code 字段），实际非 JSON: {resp.brief()}")
    assert resp.code == expected, (
        f"{hint}\n 期望 code={expected}，实际 code={resp.code}（message={resp.message!r}）\n"
        f" 响应: {resp.brief()}")


def assert_success(resp: ApiResponse, hint: str = "", expect_data: bool = False):
    """
    断业务成功（code=200）。
    expect_data=True 时额外断 data 非空 —— 能抓到"code=200 但 data 为 null"的假成功。
    """
    assert_code(resp, config.Code.SUCCESS, hint)
    if expect_data:
        assert resp.data is not None, (
            f"{hint}\n 期望 data 非空，实际 data=None（假成功：业务码正常但内容缺失）\n"
            f" 响应: {resp.brief()}")


def assert_data_has(resp: ApiResponse, key: str, hint: str = ""):
    """断 data 中含指定字段（dict）"""
    assert isinstance(resp.data, dict), (
        f"{hint}\n 期望 data 为 dict，实际 {type(resp.data).__name__}: {resp.data!r}")
    assert key in resp.data, (
        f"{hint}\n 期望 data 含字段 '{key}'，实际字段: {list(resp.data.keys())}")


def assert_field(resp: ApiResponse, path: str, expected: Any, hint: str = ""):
    """
    按点号路径断 data 字段值，如 assert_field(resp, "activityId", 34)。
    能抓到的缺陷：接口返回 200 但数据错乱（回显了别的活动/别的用户）。
    """
    cur = resp.data
    for part in path.split("."):
        if isinstance(cur, dict) and part in cur:
            cur = cur[part]
        elif isinstance(cur, list) and part.isdigit():
            cur = cur[int(part)]
        else:
            raise AssertionError(
                f"{hint}\n 路径 '{path}' 在响应中不存在（当前节点={cur!r}）\n 响应: {resp.brief()}")
    assert cur == expected, (
        f"{hint}\n 期望 data.{path}={expected!r}，实际={cur!r}\n 响应: {resp.brief()}")


def assert_no_internal_leak(resp: ApiResponse, hint: str = ""):
    """
    断错误响应未泄露内部实现细节（契约风险 C-5：GlobalExceptionHandler.java:76 回显 e.getMessage()）。
    能抓到的缺陷：堆栈/类名/包名外泄（如 java.lang.、org.springframework.、SQL 片段）。
    """
    msg = (resp.message or "") + (resp.text or "")
    leaks = ["java.", "org.springframework", "org.apache", "Exception", "at ", "SQL", "sql"]
    hit = [k for k in leaks if k in msg]
    assert not hit, (
        f"{hint}\n 期望错误信息不泄露内部细节，实际命中 {hit}\n 响应: {resp.brief()}")


def assert_paging_params_effective(resp_page1: ApiResponse, resp_page2: ApiResponse, hint: str = ""):
    """
    断分页参数真实生效（契约风险 C-1）。
    能抓到的缺陷：参数名不被后端识别 → 被静默忽略 → 两页返回相同数据（默认值掩盖）。
    """
    assert_success(resp_page1, hint + " [第1页]")
    assert_success(resp_page2, hint + " [第2页]")
    r1 = (resp_page1.data or {}).get("records", [])
    r2 = (resp_page2.data or {}).get("records", [])
    if len(r1) == 0:
        print(f"    [WARN] 数据量不足，无法验证分页差异（第1页为空）: {hint}")
        return
    assert r1 != r2, (
        f"{hint}\n 期望第1页与第2页 records 不同（分页参数生效），实际完全相同（{len(r1)} 条）\n"
        f" → 疑似分页参数名不被识别而被静默忽略")
