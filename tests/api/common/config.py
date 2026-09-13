"""
测试配置 — 环境 / 业务错误码 / 测试数据（单一事实来源）

来源对照（不凭记忆，均可在代码中核对）：
  错误码      → common/errorcode/ServiceErrorCodeConstants.java、GlobalErrorCodeConstants.java
  鉴权路径    → common/interceptor/LoginInterceptor.java:39-46、common/config/AppConfig.java:17-31
  契约明细    → docs/test/contract/api-contract.md

⚠️ 已知错误码冲突（代码事实，非测试臆造）：
  202 = ServiceErrorCodeConstants.ACTIVITY_PRIZE_ERROR（活动关联奖品异常）
  202 = ControllerErrorCodeConstants.ACTIVITY_FIND_DETAIL_ERROR（查询活动详细失败）
  两者语义不同却同码 → 调用方无法区分，已登记为契约风险待业主确认。
"""
import os

# ==================== 环境 ====================
BASE_URL = os.getenv("LOTTERY_BASE_URL", "http://localhost:8080")
HTTP_TIMEOUT = float(os.getenv("LOTTERY_TIMEOUT", "15"))
# AI 接口依赖外部大模型（DeepSeek），响应较慢
AI_TIMEOUT = float(os.getenv("LOTTERY_AI_TIMEOUT", "90"))
# 抽奖接口经 RabbitMQ 投递；MQ 不可达时会阻塞（缺陷 BUG-002）
MQ_TIMEOUT = float(os.getenv("LOTTERY_MQ_TIMEOUT", "35"))

# ==================== 测试数据（统一前缀，可识别、可清理） ====================
TEST_PREFIX = "qa_auto_"
# 固定测试账号：复用而非每次新建，避免数据累积污染
TEST_USER = {
    "name": "QA自动化-普通用户",
    "mail": f"{TEST_PREFIX}user@test.com",
    "phoneNumber": "13800000001",
    "password": "123456",
    "identity": "NORMAL",
}
TEST_ADMIN = {
    "name": "QA自动化-管理员",
    "mail": f"{TEST_PREFIX}admin@test.com",
    "phoneNumber": "13900000001",
    "password": "123456",
    "identity": "ADMIN",
}
# 管理员注册口令（来源：UserController.applyRegisterIdentityPolicy）
ADMIN_SECRET = "lottery-admin-2024"
ADMIN_SECRET_HEADER = "X-Admin-Secret"

# 真实基础数据（只读，绝不修改）——用于"不碰真实数据"的断言基线
BASELINE = {
    "user_count_min": 14,      # 2026-09-13 清理实测：14
    "activity_count_min": 5,
    "prize_count_min": 5,
    "faq_count_min": 2,
    "activity_id_sample": 34,  # 已知存在且有中奖记录
    "prize_id_sample": 25,
}


class Code:
    """业务码常量（断言基线：业务失败时 HTTP 仍为 200，必须断 body.code）"""
    SUCCESS = 200
    # 全局（GlobalErrorCodeConstants）
    BAD_REQUEST = 400
    UNAUTHORIZED = 401
    FORBIDDEN = 403
    INTERNAL_SERVER_ERROR = 500
    UNKNOWN = 999
    # 用户（100-117）
    REGISTER_ERROR = 100
    MAIL_ERROR = 101
    PHONE_ERROR = 102
    IDENTITY_ERROR = 103
    PASSWORD_EMPTY_ERROR = 104
    PASSWORD_FORMAT_ERROR = 105
    MAIL_USED_ERROR = 106
    PHONE_USED_ERROR = 107
    LOGIN_INFO_NOT_EXIST = 108
    LOGIN_NOT_EXIST = 109
    USER_INFO_IS_EMPTY = 110
    PASSWORD_ERROR = 111
    VERIFICATION_CODE_ERROR = 112
    REGISTER_FORBIDDEN = 113
    PHONE_NOT_ADMIN = 114
    SMS_SEND_TOO_FREQUENT = 115
    SMS_SEND_HOUR_LIMIT = 116
    SMS_SEND_FAILED = 117
    # 活动（200-206；注意 202 与控制器错误码冲突）
    CREATE_ACTIVITY_EMPTY = 200
    ACTIVITY_USER_ERROR = 201
    ACTIVITY_PRIZE_ERROR = 202
    CACHE_ACTIVITY_NOT_EXIST = 206
    # 奖品（300-301）
    PRIZE_ERROR = 300
    PRIZE_NOT_FOUND_ERROR = 301
    # 抽奖（400-403）
    ACTIVITY_OR_PRIZE_IS_EMPTY = 400
    # 图片（500）
    PIC_ERROR = 500
    # 控制器层（ControllerErrorCodeConstants）
    C_ACTIVITY_FIND_DETAIL_ERROR = 202


# ==================== 鉴权路径（契约基线） ====================
# 需 ADMIN 身份（LoginInterceptor.java:39-46）
ADMIN_ONLY_PATHS = [
    ("POST", "/activity/create"),
    ("POST", "/prize/pic/upload"),
    ("POST", "/prize/create"),
    ("GET", "/prize/find-list"),
    ("GET", "/user/base-user/find-list"),
    ("POST", "/api/faq/add"),
    ("DELETE", "/api/faq/delete"),
    ("GET", "/api/faq/list"),
    ("GET", "/api/handover/list"),
    ("POST", "/api/handover/process"),
]
# 免登录（AppConfig.java:17-31）
PUBLIC_PATHS = [
    ("GET", "/user-login.html"),
    ("POST", "/user/register"),
    ("POST", "/user/password/login"),
    ("POST", "/user/message/login"),
    ("POST", "/user/verification-code/send"),
    ("POST", "/draw/winning-records/show"),   # 有意设计：便于分享抽奖结果
    ("POST", "/api/chat/sync"),
    ("POST", "/api/ai/chat/sync"),
]


def describe() -> str:
    """打印当前测试配置（用于执行日志留痕）"""
    return (
        f"BASE_URL={BASE_URL} | HTTP_TIMEOUT={HTTP_TIMEOUT}s | "
        f"AI_TIMEOUT={AI_TIMEOUT}s | MQ_TIMEOUT={MQ_TIMEOUT}s | "
        f"PREFIX={TEST_PREFIX} | USER={TEST_USER['mail']} | ADMIN={TEST_ADMIN['mail']}"
    )
