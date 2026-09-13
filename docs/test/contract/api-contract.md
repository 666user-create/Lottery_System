# 接口契约拆解表（API Contract）

| 文档属性 | 内容 |
|---|---|
| 文档编号 | TS-CONTRACT-002 |
| 版本号 | V2.0（重建版，替代上轮口径） |
| 编写人 | QA Leader |
| 编写日期 | 2026-08-17 |
| 基准来源 | **以代码为准**：`controller/*.java`、`controller/param/*.java`、`controller/result/*.java`、`common/errorcode/*.java`、`common/pojo/CommonResult.java`、`controller/handler/GlobalExceptionHandler.java`、`common/interceptor/LoginInterceptor.java`、`common/config/AppConfig.java` |
| 交叉信源 | `src/main/resources/static/*.html`（前端真实调用） |
| 用途 | 第③④步用例的唯一断言依据；pytest 常量表来源 |

> **核对方式**：本文每条结论均附文件与行号，可用 `grep -n` 复核；与代码不符时以代码为准并回改本文档。

---

## 一、全局契约

### 1.1 统一响应包装

**定义**：`common/pojo/CommonResult.java:11-23`

```json
{ "code": 200, "data": <T>, "message": "成功" }
```

| 项 | 规则 | 行号 |
|---|---|---|
| 成功 | `code=200`，`message="成功"` | `CommonResult.java:25-32` |
| 失败 | `code=业务错误码`，`message=错误描述`，`data=null` | `CommonResult.java:34-45` |
| 保护性断言 | `error()` 传入 200 会抛 `IllegalArgumentException` | `CommonResult.java:35-36` |

**例外**：`POST /prize/pic/upload` 返回**裸 String**（`PrizeController.java:38-41`），未包装。详见 [风险 C-7](#c-7-响应格式例外裸-string)。

### 1.2 异常 → 错误码映射

**来源**：`controller/handler/GlobalExceptionHandler.java`

| 异常类型 | 行号 | HTTP | 业务 code | message |
|---|---|---|---|---|
| `MethodArgumentNotValidException` | :25-35 | 200 | `400` | 首条 BeanValidation 消息（如"活动名称不能为空"） |
| `BindException` | :25-35 | 200 | `400` | 同上 |
| `MissingServletRequestParameterException` | :40-44 | 200 | `400` | `缺少请求参数: {name}` |
| `MethodArgumentTypeMismatchException` | :49-53 | 200 | `400` | `参数类型不匹配: {name}` |
| `ServiceException` | :55-61 | 200 | 透传 | 透传 |
| `ControllerException` | :63-69 | 200 | 透传 | 透传 |
| `Exception`（兜底） | :71-77 | 200 | `500` | **`e.getMessage()`（内部异常信息外泄）** |

> ⚠️ **关键断言基线**：业务失败时 **HTTP 状态码仍为 200**，必须断 `body.code`，只断 HTTP 200 会全部假通过。

### 1.3 鉴权契约

**来源**：`common/interceptor/LoginInterceptor.java:20-56` + `common/config/AppConfig.java:17-31`

| 场景 | 结果 | 证据 |
|---|---|---|
| 无 token / token 无效（非白名单路径） | **HTTP 401** + `{"code":401,"message":"未登录或Token已失效"}` | `LoginInterceptor.java:31-36` |
| 非 ADMIN 访问 adminOnly 路径 | **HTTP 403** + `{"code":403,...}` | `LoginInterceptor.java:45-49` |
| 未登录访问 `/user/me` | HTTP 200 + `code=401`（Controller 内联返回，**与拦截器路径不同**） | `UserController.java:121-126` |

**adminOnly 路径清单**（`LoginInterceptor.java:39-46`）：
```
/activity/create
/prize/**                    (startsWith)
/user/base-user/find-list
/api/faq/**                  (startsWith)
/api/handover/list
/api/handover/process
```

**免登录白名单 excludes**（`AppConfig.java:17-31`）：
```
/**/*.html, /css/**, /js/**, /pic/**, /*.jpg, /favicon.ico
/user/password/login, /user/message/login, /user/register, /user/verification-code/send
/draw/winning-records/show      ← 有意设计：便于分享抽奖结果（2026-08-17 业主确认）
/api/chat/**, /api/ai/chat/**
```

---

## 二、接口契约明细（25 个）

### 2.1 用户模块 `/user`（6）

| # | 方法 路径 | 鉴权 | 入参（绑定方式 / 字段 / 校验） | 业务规则 | 出参 | 错误码 |
|---|---|---|---|---|---|---|
| 1 | POST `/user/register` | 公开 | JSON `UserRegisterParam`<br>`name`@NotBlank、`mail`@NotBlank、`phoneNumber`@NotBlank、`password`(可空)、`identity`@NotBlank | 邮箱格式(101)、手机格式(102)、身份枚举(103)、ADMIN 必填密码(104)、密码 6-12 位(105)、邮箱重复(106)、手机重复(107)；**`identity` 由服务端策略强制覆盖**（见下方说明） | `CommonResult<UserRegisterResult{userId}>` | 100/101/102/103/104/105/106/107/113 |

> **⚠️ 契约要点：注册接口的 `identity` 参数被服务端策略覆盖（2026-09-13 实测定性）**
> 依据 `UserController.java:66-84`（`applyRegisterIdentityPolicy`）：
> | 场景 | 实际落库身份 |
> |---|---|
> | 无 token + **无** `X-Admin-Secret` | **NORMAL**（无论请求体传什么，含 `SUPERADMIN`/`ADMIN`） |
> | 无 token + **正确** `X-Admin-Secret` | **ADMIN** |
> | 管理员已登录代建（无口令） | **NORMAL** |
> | 管理员已登录代建（带口令） | **ADMIN** |
> | 普通用户已登录代建 | **拒绝**（113 REGISTER_FORBIDDEN） |
>
> 实测：请求 `identity=SUPERADMIN` 返回 200，但登录后身份为 **NORMAL**（静默降级）。
> **安全评价**：策略正确（防止自助提权）✅；**契约缺口**：客户端被静默忽略，无任何提示，
> 调用方可能误判注册结果 → 建议响应中显式回传最终身份。
> 对应用例：`tests/api/test_09_boundary_input.py::TestIdentityBoundary`
| 2 | POST `/user/verification-code/send` | 公开 | Query `phoneNumber` | 格式(102)、用户须已登记(114)、60s 冷却(115)、1h≤5 次(116)、发送失败(117) | `CommonResult<Boolean>` | 102/114/115/116/117 |
| 3 | POST `/user/password/login` | 公开 | JSON `UserPasswordLoginParam`<br>`loginName`@NotBlank、`password`@NotBlank、`mandatoryIdentity`(父类可空) | 手机/邮箱二选一识别(109)、用户存在(110)、身份匹配(103)、密码校验(111) | `CommonResult<UserLoginResult{token,identity}>` | 103/109/110/111 |
| 4 | POST `/user/message/login` | 公开 | JSON `UserShortMessageLoginParam`<br>`loginMobile`@NotBlank、`verificationCode`@NotBlank、`mandatoryIdentity` | 格式(102)、用户存在(110)、身份(103)、验证码(112)；成功即清除验证码 | `CommonResult<UserLoginResult>` | 102/103/110/112 |
| 5 | GET `/user/base-user/find-list` | **ADMIN** | Query `identity`(可空=NORMAL/ADMIN) | — | `CommonResult<List<BaseUserInfoResult{userId,userName,identity}>>` | 401/403 |
| 6 | GET `/user/me` | 登录 | Header `token` | 解析失败→内联 401；用户不存在→内联 401("用户不存在") | `CommonResult<BaseUserInfoResult>` | 200+code401 |

### 2.2 活动模块 `/activity`（3）

| # | 方法 路径 | 鉴权 | 入参 | 业务规则 | 出参 | 错误码 |
|---|---|---|---|---|---|---|
| 7 | POST `/activity/create` | **ADMIN** | JSON `CreateActivityParam`<br>`activityName`@NotBlank、`description`@NotBlank、`activityPrizeList`@NotEmpty@Valid、`activityUserList`@NotEmpty@Valid | 用户存在(201)、奖品存在(202)、人数≥奖品总量(203)、奖品等级枚举(204)；`ActivityServiceImpl.java:259-305` | `CommonResult<CreateActivityResult{activityId}>` | 200/201/202/203/204 |
| 8 | GET `/activity/find-list` | 登录 | **`currentPage`** + `pageSize`（`PageParam.java:10-12`，**无 `@Min/@Max`**） | `offset=(currentPage-1)*pageSize`（`:15-17`） | `CommonResult<FindActivityListResult{total,records[{activityId,activityName,description,valid}]}>` | 400（类型错） |
| 9 | GET `/activity/detail/find` | 登录 | Query `activityId`（Long，必填） | `detailDTO==null` → 抛 `ControllerException(202)`（`ActivityController.java:63-66`） | `CommonResult<GetActivityDetailResult{activityId,activityName,desc,status,prizes[],users[]}>` | **202**/400 |

### 2.3 奖品模块 `/prize`（3）

| # | 方法 路径 | 鉴权 | 入参 | 出参 | 错误码 |
|---|---|---|---|---|---|
| 10 | POST `/prize/pic/upload` | **ADMIN** | multipart `multipartFile` | **裸 `String`**（文件名 = UUID+后缀，`PictureServiceImpl.java:18-38`） | 500(PIC_ERROR) / **500(未捕获越界)** |
| 11 | POST `/prize/create` | **ADMIN** | **multipart/form-data**：`param`(JSON part → `CreatePrizeParam{prizeName`@NotBlank`,description,price`@NotNull` BigDecimal}`) + `prizePic`(file part) | `CommonResult<Long>` | 400/500 |
| 12 | GET `/prize/find-list` | **ADMIN** | **`currentPage`** + `pageSize` | `CommonResult<FindPrizeListResult{total,records[{prizeId,prizeName,description,price,imageUrl}]}>` | 301/400 |

### 2.4 抽奖模块 `/draw`（3）

| # | 方法 路径 | 鉴权 | 入参 | 业务/依赖 | 出参 | 错误码 |
|---|---|---|---|---|---|---|
| 13 | POST `/draw/prize` | 登录 | JSON `DrawPrizeParam`<br>`activityId`@NotNull、`prizeId`@NotNull、`winningTime`@NotNull(Date)、`winnerList`@NotEmpty@Valid[{`userId`@NotNull,`userName`@NotBlank}] | 投递 RabbitMQ（`DrawPrizeServiceImpl.java:48-56`）；**MQ 不可达时阻塞** | `CommonResult<Boolean>` | 500(超时) |
| 14 | POST `/draw/winning-records/show` | **免登录**（有意设计） | JSON `ShowWinningRecordsParam{activityId`@NotNull`,prizeId}` | 先 Redis 后 DB；返回字段仅 winnerId/winnerName/prizeName/prizeTier/winningTime（**不含手机号邮箱**，`DrawPrizeController.java:69-75`） | `CommonResult<List<WinningRecordsResult>>` | 400 |
| 15 | GET `/draw/my-records` | 登录 | Header `token`→userId | — | `CommonResult<List<WinningRecordsResult>>` | 200+code401 |

### 2.5 AI 客服 `/api`（4）

| # | 方法 路径 | 鉴权 | 入参 | 出参 | 外部依赖 |
|---|---|---|---|---|---|
| 16 | POST `/api/chat` | 游客 | JSON `Map{message}` | **SSE** `text/event-stream`，末尾 `[DONE]` | DeepSeek（工具调用≤3 轮） |
| 17 | POST `/api/chat/sync` | 游客 | JSON `Map{message}` | `CommonResult<String>` | 同上 |
| 18 | POST `/api/ai/chat` | 游客 | `Map{message,sessionId}` + 可选 header `token` | **SSE** | DeepSeek + RAG + Redis 记忆 |
| 19 | POST `/api/ai/chat/sync` | 游客 | 同上 | `CommonResult<String>` | 同上 |

### 2.6 FAQ 知识库 `/api/faq`（3，全部 ADMIN）

| # | 方法 路径 | 入参 | 出参 | 备注 |
|---|---|---|---|---|
| 20 | POST `/api/faq/add` | JSON `FaqDO{question,answer,category}`（**无校验注解**） | `CommonResult<Long>` | 向量生成失败不阻断（`FaqController.java:42-51`） |
| 21 | DELETE `/api/faq/delete` | Query `id` | `CommonResult<Boolean>` | 连带删 `faq_embedding` |
| 22 | GET `/api/faq/list` | **`page`** + `pageSize`（`FaqController.java:65-66`） | `CommonResult<Map{list,total}>` | 与活动/奖品的参数名不同 |

### 2.7 交接班 `/api/handover`（3）

| # | 方法 路径 | 鉴权 | 入参 | 出参 | 备注 |
|---|---|---|---|---|---|
| 23 | POST `/api/handover/create` | 登录 | JSON `Map{sessionId,question}` | `CommonResult<Long>` | sessionId 空 → **HTTP 200 + code=400**（`HandoverController.java:42-44`） |
| 24 | GET `/api/handover/list` | **ADMIN** | Query `status`+**`page`**+`pageSize` | `CommonResult<Map{list,total}>` | — |
| 25 | POST `/api/handover/process` | **ADMIN** | JSON `HumanHandoverDO{id,status,handler,remark}` | `CommonResult<Boolean>` | 缺 id/status → HTTP 200 + code=400（`:79-81`） |

---

## 三、外部依赖契约

| 依赖 | 端点 / 配置 | 证据 | 不可用时行为 |
|---|---|---|---|
| MySQL | `jdbc:mysql://127.0.0.1:3306/lottery_system` | `application.properties:9` | 启动失败 |
| Redis | `localhost:8888` | `:25-26` | 验证码/缓存/记忆功能异常 |
| RabbitMQ | `8.136.222.102:5672`（retry max-attempts=5） | `:43-51` | **抽奖接口阻塞超时（实测 30043ms）** |
| DeepSeek | `https://api.deepseek.com` + `/v1/chat/completions` | `:74-76`、`XiaoDongChatService.java:142-146`、`AiCustomerService.java:134-138` | 返回错误文案 |
| Embedding | `https://api.siliconflow.cn/v1`，**api-key 为空** | `:80-84` | 降级关键词检索（`RagService`） |
| 短信 | 阿里云 `dypnsapi` / 短信宝 | `:22-23,68-71` | `code=117` |
| 邮件 | `smtp.qq.com:465` | `:53-61` | 日志报错，不阻断主流程 |

**Redis Key 前缀清单**（清理白名单用）
```
verification_code:{phone}          VerificationCodeServiceImpl.java:23
sms_send_cooldown:{phone}          :24
sms_send_hour_count:{phone}        :25
ACTIVITY_{activityId}              ActivityServiceImpl.java:42
WINNING_RECORDS_{...}              DrawPrizeServiceImpl.java:31
ai:memory:{sessionId}              AiCustomerService.java:182
```

---

## 四、契约风险清单（第③④步用例靶子）

| ID | 风险 | 证据（文件:行） | 影响 | 用例方向 | 状态 |
|---|---|---|---|---|---|
| **C-1** | **分页参数名分裂**：Activity/Prize 用 `currentPage`，FAQ/Handover 用 `page` | `PageParam.java:10` vs `FaqController.java:65` | 调用方易传错；`user-lobby.html:164` 实传 `pageNum`（不匹配，靠默认值 1 掩盖） | 参数名生效性断言（正确名生效 / 错误名被忽略） | 🔴 待测 |
| **C-2** | `PageParam` **零边界校验** | `PageParam.java:10-17` | `currentPage=0/-1` → offset 负数 → `LIMIT -10,10` → SQL 异常 500；`pageSize` 极大 → 性能/内存 | 下界-1 / 边界 / 上界+1 | 🔴 待测 |
| **C-3** | BUG-003 证据冲突（上轮记录"200+null" vs 代码抛 202） | `ActivityController.java:63-66` | 上轮缺陷结论可能不成立 | 实测 `activityId=999999` 断 code | 🔴 待实测 |
| **C-4** | 免登录可查中奖名单 | `AppConfig.java:28` | **业主人确认：有意设计**（分享抽奖结果）；暴露面不含手机号/邮箱 | 断言"免登录可访问=预期"，并覆盖字段脱敏边界 | ✅ 设计意图 |
| **C-5** | 500 回显内部异常消息 | `GlobalExceptionHandler.java:76` | 信息泄露（堆栈/类名） | 畸形 JSON/类型错 → 断言 message 不含 `java.`/`Exception` | 🔴 待测 |
| **C-6** | `FaqDO` 无入参校验 | `FaqController.java:39` + `FaqDO` 无注解 | 空 `question`/`answer` → DB NOT NULL → 500 | 空值/超长值边界 | 🔴 待测 |
| **C-7** | 响应格式例外（裸 String） | `PrizeController.java:38-41` | 契约不统一；前端**零调用**（grep 证实） | 见"改进方案" | 🟡 待决策 |
| **C-8** | multipart 契约（`@RequestPart`） | `PrizeController.java:44-46`；前端 `create-prizes.html:156-174` | 用 JSON 调用必失败 | `requests files=` 构造正/反向用例 | 🔴 待测 |
| **C-9** | **文件名无扩展名 → `StringIndexOutOfBoundsException`** | `PictureServiceImpl.java:29`（`lastIndexOf(".")=-1`，且该行在 `try`(:32) 之外） | 上传无后缀文件 → 未捕获异常 → 500 + 内部消息 | 文件边界用例（无后缀/仅"."/"a."） | 🔴 待测（**该文件在排除清单，只测不改**） |
| **C-10** | 静态资源无版本指纹 + 无 no-cache | `user-login.html:8-10` 等 56 处无 `?v=`；`application.properties:41` 仅 static-locations | 新 HTML + 旧 CSS → 样式失效 | UI 用例断言关键样式 + 资源指纹校验 | 🟡 属重构项 |

---

## 五、待确认项（进入第③步前）

| # | 事项 | 建议 |
|---|---|---|
| 1 | **C-7 处理方式** | 方案 A：`PrizeController.uploadPic` 返回 `CommonResult<String>`（孤儿接口，影响半径 0）；方案 B：保持现状，测试写特例 + 文档登记 |
| 2 | C-1（分页参数名分裂）是否纳入重构 | 建议：**仅登记 + 用例覆盖**，不统一命名（改动涉及 4 个 Controller + 5 个页面，超出"修正"范围） |
| 3 | C-10（资源指纹/no-cache）是否纳入重构 | 建议：纳入（15 个 HTML + 1 处配置），但需你确认后执行 |
