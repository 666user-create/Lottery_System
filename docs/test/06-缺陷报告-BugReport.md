# 缺陷报告（Bug Report）

| 文档属性 | 内容 |
|---|---|
| 文档编号 | TS-BUG-006 |
| 版本号 | **V2.0（重建版：含修复与回归验证）** |
| 编写人 | QA Leader |
| 编写日期 | 2026-09-13 |
| 测试轮次 | 第 2 轮（重建式测试 + 缺陷修复 + 反向验证） |

---

## 1. 缺陷汇总（10 项 + 2 项重构）

| 编号 | 标题 | 级别 | 状态 | 发现方式 | 关联用例 |
|---|---|---|---|---|---|
| BUG-001 | MqReceiver 重复导入 SMSService | P4 | ✅ 已修复 | 代码审查 | — |
| BUG-002 | 抽奖接口在 RabbitMQ 不可达时无快速失败 | P2 | ⏸ **待修（环境依赖）** | 接口测试 | `test_12::test_draw_prize_fails_fast_when_mq_unreachable`（xfail） |
| BUG-003 | ~~活动详情不存在返回 200+null~~ | — | ❌ **撤销（记录有误）** | 接口实测 | `test_02::test_detail_not_exist_must_not_report_success` |
| BUG-004 | 明文存放生产密钥（AK/SK、邮箱授权码、AI key） | P4（风险高） | 📋 风险登记（按约定不修） | 代码审查 | — |
| BUG-005 | 验证码仅限已登记用户，普通用户无法短信登录 | P3 | 🟡 待产品确认 | 代码审查 + 接口测试 | `test_01`（未断言，待定） |
| BUG-006 | 注册页无 token 加载弹错误提示 | P3 | 🟡 登记（本轮未在修复清单内） | UI 测试 | — |
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

### BUG-005（P3，待产品确认）验证码策略

| 项 | 内容 |
|---|---|
| 现象 | 未登记手机号发送验证码返回 `code=114 该手机号未登记为管理员账号` |
| 说明 | 若产品意图为"短信登录仅管理员可用"则行为正确；否则普通用户短信登录不可用 |
| 处置 | 待产品确认 |

### BUG-006（P3，本轮未在修复清单内）注册页加载弹窗

| 项 | 内容 |
|---|---|
| 现象 | `user-register.html` 在无 token 场景加载时弹出"注册失败: 未登录或Token已失效" |
| 说明 | 文案与注册场景不符，体验不佳；本轮修复清单未包含此项 |
| 建议 | 页面加载逻辑去除对受保护接口的调用，或改为静默处理 |

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
| P2 严重 | 4 | 3（BUG-008/009、C-2） | 1（BUG-002 环境依赖） | — |
| P3 一般 | 4 | 2（BUG-007、BUG-010） | 2（BUG-005 待确认、BUG-006 待修） | — |
| P4 轻微 | 2 | 1（BUG-001） | — | 1（BUG-004） |
| 重构项 | 2 | 1（C-10） | 1 登记（C-9 只测不改） | — |
| **合计** | **12** | **7** | **4** | **1** |

**趋势分析**：
1. 本轮缺陷集中在**输入校验与异常处理**（6/12）—— 反映该版本在"防御式编程"上薄弱，建议后续统一引入参数校验规范；
2. 权限、注入、并发三类**安全相关验证全部通过**，说明既有安全设计有效；
3. 上轮存在的"文档数字漂移 + 模拟痕迹"问题（BUG-003 即例证）已通过"真实执行 + 反向验证"机制纠正。
