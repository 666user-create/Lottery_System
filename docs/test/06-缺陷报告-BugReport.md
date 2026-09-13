# 缺陷报告（Bug Report）

| 文档属性 | 内容 |
|---|---|
| 文档编号 | TS-BUG-006 |
| 版本号 | **V2.0（重建版：含修复与回归验证）** |
| 编写人 | QA Leader |
| 编写日期 | 2026-09-13 |
| 测试轮次 | 第 2 轮（重建式测试 + 缺陷修复 + 反向验证） |

---

## 1. 缺陷汇总（BUG-001~010 共 10 项 + 契约/重构项 3 项：C-2 / C-9 / C-10）

| 编号 | 标题 | 级别 | 状态 | 发现方式 | 关联用例 |
|---|---|---|---|---|---|
| BUG-001 | MqReceiver 重复导入 SMSService | P4 | ✅ 已修复 | 代码审查 | — |
| BUG-002 | 抽奖接口在 RabbitMQ 不可达时无快速失败 | P2 | ⏸ **待修（环境依赖）** | 接口测试 | `test_12::test_draw_prize_fails_fast_when_mq_unreachable`（xfail） |
| BUG-003 | ~~活动详情不存在返回 200+null~~ | — | ❌ **撤销（记录有误）** | 接口实测 | `test_02::test_detail_not_exist_must_not_report_success` |
| BUG-004 | 明文存放生产密钥（AK/SK、邮箱授权码、AI key） | P4（风险高） | 📋 风险登记（按约定不修） | 代码审查 | — |
| **BUG-005** | 未注册手机号错误码/文案语义错误（误用 114「未登记为管理员账号」）<br>**原描述「普通用户无法短信登录」经实测不成立** | P4 | ✅ **已修复 + 回归** | 接口实测 | `test_14::TestBug005VerificationCode` |
| **BUG-006** | **前端成功码误判（`!== 0` 应为 `200`）→ 工单列表 / 工单处理 / 转人工 3 处功能完全不可用**；未登录时连弹 2 个弹窗 | **P2** | ✅ **已修复 + 回归** | UI 实测（Selenium 取证） | `test_14::TestBug006FrontendSuccessCode` |
| **BUG-007** | 邮箱校验拒绝大写字母（合法邮箱无法注册） | P3 | ✅ **已修复 + 回归** | 边界用例 | `test_01::test_register_mail_uppercase_after_fix` |
| **BUG-008** | 500 响应回显完整 SQL / 表结构 / 包路径 | **P2** | ✅ **已修复 + 回归** | 边界用例 | `test_10::test_no_internal_leak_on_error` |
| **BUG-009** | 畸形 JSON / 错误 Content-Type / 空 body → 500（应 400/415）且回显内部类名与方法签名 | **P2** | ✅ **已修复 + 回归** | 边界用例 | `test_10::TestMalformedRequest`（8 条） |
| **BUG-010** | 无长度校验 → DB `Data too long` 直穿 500 | P3 | ✅ **已修复 + 回归** | 边界用例 | `test_09::TestStringLengthBoundary`（3 条） |
| **C-2** | `PageParam` 无边界校验 → 负 offset → SQL `LIMIT -10,10` → 500 | **P2** | ✅ **已修复 + 回归** | 边界用例 | `test_08::TestPagingBoundary`（6 条） |
| C-9 | 上传文件名无扩展名 → `StringIndexOutOfBoundsException` | P4 | 📋 登记（文件属业主未提交改动，**只测不改**） | 代码审查 | `test_03`（越权路径已覆盖） |
| C-10 | 前端资源无版本指纹 + 无 no-cache | 重构 | ✅ **已完成** | 代码审查 | `test_13` + 静态检查 |

---

## 2. 已修复缺陷详情（含回归验证）

### BUG-007（P3）邮箱校验拒绝大写字母

| 项 | 内容 |
|---|---|
| 现象 | 含大写字母的合法邮箱（如 `User@Example.COM`）注册返回 `code=101 邮箱校验失败` |
| 根因 | `RegexUtil.java:31` 正则仅允许 `[a-z0-9]` |
| 修复 | 正则放开为 `[a-zA-Z0-9]`（local-part 与域名均支持大小写） |
| 回归 | 用例由 FAILED → PASSED；且验证新账号可正常登录 |
| 反向验证 | ✅ 正则改回小写后该用例如实失败 |

### BUG-008（P2）500 响应回显完整 SQL 与内部结构

| 项 | 内容 |
|---|---|
| 现象 | 500 响应体含：`### SQL: insert into faq(question, answer, category) values(?, ?, ?)` + `FaqMapper.java` 包路径 + 完整 MyBatis 堆栈 |
| 根因 | `GlobalExceptionHandler` 兜底分支 `CommonResult.error(..., e.getMessage())` 原样回显 |
| 修复 | 兜底仅返回统一文案"系统异常"；完整堆栈**仅写服务端日志** |
| 回归 | 泄露检测用例 FAILED → PASSED |
| 反向验证 | ✅ 恢复 `e.getMessage()` 后该用例如实失败 |

### BUG-009（P2）请求解析异常未处理 → 500 + 内部签名泄露

| 项 | 内容 |
|---|---|
| 现象（实测原文） | `JSON parse error: Cannot deserialize value of type 'org.example.lottery_system.controller.param.UserPasswordLoginParam' from Array value`<br>`Required request body is missing: public org.example...UserController.userPasswordLogin(...)`<br>`Content-Type 'text/plain;charset=UTF-8' is not supported` |
| 根因 | `HttpMessageNotReadableException` / `HttpMediaTypeNotSupportedException` / `HttpRequestMethodNotSupportedException` 均无专门 handler → 落到 `Exception` 兜底 → 500 + 原始消息 |
| 修复 | 新增 4 个 handler：请求体不可读→**400**、Content-Type 不支持→**415**、方法不支持→**405**、静态资源不存在→**404**（并顺带消除 `/mcp/*` 探测产生的 ERROR 级日志噪音） |
| 回归 | 8 条用例 FAILED → PASSED |
| 反向验证 | ✅ 通过分页越界路径验证兜底分支仍可被检测 |

### BUG-010（P3）长度校验缺失导致 DB 异常直穿

| 项 | 内容 |
|---|---|
| 现象 | `name=300` → `Data too long for column 'user_name'` → 500；`question=600` 同理；FAQ 缺 `answer` → `Column 'answer' cannot be null` → 500 |
| 根因 | `UserRegisterParam` / `FaqDO` 无 `@Size`/`@NotBlank`；`FaqController.add` 未启用 `@Validated` |
| 修复 | 两个入参类补齐 Jakarta Validation 注解；`FaqController.add` 加 `@Validated` |
| 回归 | 3 条用例 FAILED → PASSED |

### C-2（P2）分页参数无边界校验

| 项 | 内容 |
|---|---|
| 现象（实测原文） | `currentPage=-1` → `### SQL: select * from activity order by id desc limit ?,?` + `SQLSyntaxErrorException: ... near '-10,10'` → 500 |
| 根因 | `PageParam` 无 `@Min/@Max`；`offset=(currentPage-1)*pageSize` 可为负 |
| 修复 | `currentPage` 加 `@Min(1)`；`pageSize` 加 `@Min(1) @Max(100)`；`ActivityController`/`PrizeController` 参数启用 `@Validated` |
| 回归 | 6 条边界用例 FAILED → PASSED（现在返回 400 + 明确提示） |
| 反向验证 | ✅ 注释 `@Min` 后 6 条用例如实失败 |

### C-10（重构）前端资源缓存策略

| 项 | 内容 |
|---|---|
| 问题 | 本地 css/js 引用无版本指纹；未配置 no-cache → 可能"新 HTML + 旧 CSS"导致样式整体失效 |
| 修复 | 10 个页面本地资源加 `?v=20260913`；`application.properties` **追加** no-cache/no-store/must-revalidate/max-age=0 |
| 边界遵守 | `register.html` 属业主未提交改动 → **跳过未改**；`application.properties` **仅追加**，未修改任何既有配置行 |
| 静态检查 | ✅ 无 BOM、中文字符完整、指纹数量正确 |

---

### BUG-005（P4，已修复）未注册手机号错误码/文案语义

| 项 | 内容 |
|---|---|
| 现象（原描述） | 「验证码仅限已登记用户，普通用户无法短信登录」 |
| **实测纠正** | 已注册**普通用户**发码返回 `code=200 data=True` → **功能本身正常，原描述不成立**；真正问题是未注册手机号返回 `114` + 「该手机号未登记为管理员账号」（文案误导、错误码语义不符） |
| 修复（方案 A，业主选定） | 新增 `PHONE_NOT_REGISTERED = 118, "该手机号尚未注册，请先注册后再登录"`；`VerificationCodeServiceImpl` 拆分「未注册(118)」与「已注册但身份缺失(114)」两个分支 |
| 验证 | 未注册手机号 → `code=118` ✅；已注册普通用户 → `code=200` ✅（未误伤正常路径） |
| 防退化用例 | `test_14::TestBug005VerificationCode`（2 条） |

### BUG-006（P2，已修复）前端成功码误判导致 3 处功能完全不可用

| 项 | 内容 |
|---|---|
| 现象（原描述） | 「注册页无 token 加载弹错误提示」 |
| **实测纠正** | Selenium 逐页取证：`user-register.html` 加载时**无弹窗**；真正问题在 `admin-handover.html` / `chat.html`。且实际文案是「加载失败」而非「注册失败」（原记录为转录笔误） |
| 根因 | 前端写 `if (res.code !== 0)`，而本项目成功码为 **200**（`CommonResult.success`）→ **把成功当失败** |
| 影响面（3 处） | `admin-handover.html:56` 工单列表永远空白；`:91` 工单处理（处理中/已解决）永远失败；`chat.html:133` 用户端转人工按钮永远失败 |
| 附加问题 | `admin-handover.html` 无 token 时 alert 后**不中断流程**，`loadList()` 仍执行 → 连弹 2 个弹窗 |
| 修复 | 成功码 3 处改为 `!== 200`；未登录改为 `location.assign('blogin.html')` 并加 `if (token)` 守卫 |
| 验证（Selenium 实测，4 场景） | S1 无token：1 弹窗 + 跳转 ✅；S2 有token：**0 弹窗 + 表格渲染 1 行** ✅；S3 工单处理：提示「已更新」✅；S4 转人工：无失败弹窗 ✅ |
| 防退化用例 | `test_14::TestBug006FrontendSuccessCode`（3 条静态检查，可抓同类回归） |
| **教训** | 第③步 Selenium 用例只断「元素存在」未断「功能可用」，故漏检此缺陷 → 已补充静态检查与功能验证 |

---
## 3. 未修复 / 待确认缺陷

### BUG-002（P2，环境依赖）MQ 不可达时无快速失败

| 项 | 内容 |
|---|---|
| 现象 | `POST /draw/prize` 在 RabbitMQ（`8.136.222.102:5672`）不可达时阻塞约 30s 后超时 |
| 影响 | MQ 抖动时抽奖接口长时间挂起，存在 Tomcat 线程耗尽风险 |
| 当前处置 | 用例标记 **xfail**（预期失败），MQ 就绪后自动转为 xpass 提醒 |
| 建议修复 | 为 `RabbitTemplate` 配置连接/回复超时；捕获 `AmqpConnectException` 返回明确错误码；引入发送确认与重试告警 |

### BUG-004（P4，风险登记）明文密钥

| 项 | 内容 |
|---|---|
| 内容 | `application.properties` 明文含阿里云 AK/SK、QQ 邮箱授权码、DeepSeek key、短信宝 key；`UserController` 硬编码管理员口令 `lottery-admin-2024` |
| 风险等级 | 高（仓库泄露即可被滥用） |
| 处置 | 按业主约定本轮**不修复**，仅登记 |
| 建议 | 发布前迁移至环境变量/配置中心；轮换已泄露密钥 |

> **说明**：BUG-005 / BUG-006 原登记于本节，**已于 2026-09-13 修复**（业主确认方案 A / 认可修法），
> 详细修复记录与实测纠正见第 2 节末尾。

### C-9（P4，只测不改）上传文件名越界

| 项 | 内容 |
|---|---|
| 现象 | `PictureServiceImpl.java:29` `fileName.substring(fileName.lastIndexOf("."))`：文件名无扩展名时 `lastIndexOf(".") = -1` → `StringIndexOutOfBoundsException`（该行在 `try` 块之外，异常直穿） |
| 处置 | 该文件在业主**未提交改动清单**内 → 按排除项**只测不改**，仅登记 |
| 建议 | 增加扩展名判空：无 `.` 时使用默认后缀或抛 `PIC_ERROR` |

---

## 4. BUG-003 撤销说明（重要自我纠错）

| 项 | 内容 |
|---|---|
| 上轮记录 | "活动详情查询不校验活动存在性，`activityId=999999` 返回 `200 + data=null`"，登记为 P3 |
| 本轮实测（客户端） | `[定性] 不存在活动 → HTTP=200 code=202 msg='查询活动详细失败'` |
| 本轮实测（服务端日志） | `ActivityController.java:65` 抛出 `ControllerException: 查询活动详细失败`，全局异常处理器返回 202 |
| 结论 | **上轮缺陷记录有误**（可能为"合理补全"的模拟痕迹，非真实执行）。本轮以真实执行纠正，缺陷撤销 |
| 教训 | 缺陷必须基于**可复现的真实证据**（命令 + 输出），不得以推测填充 |

---

## 5. 缺陷统计与趋势

| 级别 | 总数 | 已修复并回归 | 待修/待确认 | 风险登记 |
|---|---|---|---|---|
| P1 致命 | 0 | — | — | — |
| P2 严重 | 5 | **4**（BUG-006↑、BUG-008、BUG-009、C-2） | 1（BUG-002 环境依赖） | — |
| P3 一般 | 2 | **2**（BUG-007、BUG-010） | — | — |
| P4 轻微 | 4 | **2**（BUG-001、BUG-005↓） | — | 2（BUG-004、C-9） |
| 重构项 | 1 | **1**（C-10） | — | — |
| **合计** | **12** | **9** | **1** | **2** |

**趋势分析**：
1. 本轮缺陷集中在**输入校验与异常处理**（6/12）—— 反映该版本在"防御式编程"上薄弱，建议后续统一引入参数校验规范；
2. 权限、注入、并发三类**安全相关验证全部通过**，说明既有安全设计有效；
3. 上轮存在的"文档数字漂移 + 模拟痕迹"问题（BUG-003 即例证）已通过"真实执行 + 反向验证"机制纠正。
