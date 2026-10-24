# 中奖通知链路改造设计：短信宝 Mock 化 + 两级通知队列（短信/邮件）

- 日期：2026-10-11
- 状态：已批准（对话中逐节确认；两级队列形态经二次质询后确认；邮件入队为用户在审阅阶段主动扩展的范围）
- 范围：中奖通知全链路——短信（`SMSService` → `SmsBaoServiceImpl`）与邮件（`QQEmailService.notifyWinners`）
- 不在范围：验证码短信（阿里云 `dypnsapi` 链路）、站内信

---

## 1. 背景与问题

短信宝 API 因政策改动（通知类短信要求发送方实名认证 + 签名报备 + 模板报备）实际调用失败，现状是代码还在、通道已死的"壳子"。项目决策约束：

- 只能个人实名认证：无法可靠通过云厂商签名报备（需签名来源证明，个人无备案网站/APP/公众号），真实短信通道不可行。
- 项目性质：中奖通知链路的真实定位是 **MQ 消费端的异步扇出效果器**，服务"高并发消息队列"的演示目的；能真发最好，不能真发可接受。
- 邮件现状的两个问题（本次一并解决）：`MqReceiver` 内同步调 SMTP，逐封串行、阻塞消费线程；发送失败仅 log，无重试无存档。

## 2. 决策记录

| 决策 | 结论 | 理由 |
|---|---|---|
| 通道 | Mock 发送器替代短信宝 | 个人实名过不了签名报备，真实短信不可行；Mock 保证链路演示完整 |
| 形态 | **两级队列**（对比"单队列 + @Async 最小改动"） | ① 通知本身成为完整 MQ 消息流，补全"MQ 高并发"演示叙事；② 复用项目已有 DLX 死信基建（`DlxQueue` → `DlxReceiver`），发送失败从"log 丢失"升级为"重试+死信存档"；③ 为未来真实驱动预留切换管道（加一个 `SmsSender` 实现即继承重试/死信/并发）；④ `MqReceiver` 职责收敛，通知入队即收尾 |
| 邮件范围 | **邮件通知一并进二级队列**（用户审阅阶段扩展） | 与"通知都走 MQ"的心智模型对齐；`MqReceiver` 不再被 SMTP 阻塞；邮件获得重试+死信 |
| 队列布局 | 短信/邮件**两条独立二级队列 + 各自监听器**，共用一个自定义容器工厂 | 镜像项目现有"一队列一交换机一路由键"的显式风格；两通道重试/死信/并发互不干扰；演示 UI 里两队列深度可分别观察 |
| 站内信方案 | 本次不做 | 曾作为替代方向讨论；用户最终选择 Mock + 两级队列形态 |
| 新增对外接口 | 无 | 152 条接口用例、41 条 UI 用例零新增 |

**已知取舍**：

- 两级队列第一跳（MqReceiver → 二级队列）是 at-most-once：入队成功后、消费前应用崩溃则该批通知丢失。与现状语义一致（现状通知同样是发了就丢），不是本次改造引入的回归；第二跳（二级队列 → 消费）为 at-least-once（重试 + 死信）。
- **邮件第二跳 at-least-once 意味着真实邮件可能重复投递**（SMTP 发成功但 ack 前消费端崩溃的极端场景）。演示场景接受；如将来不可接受，加发送去重表（范围外）。

## 3. 链路架构

```
抽奖请求(1条) → DirectExchange → DirectQueue → MqReceiver.process
 ├─ 校验/状态扭转/保存中奖记录（同步，失败回滚——现状不动）
 ├─ 【改】for 每个中奖者：smsService.sendSmsAsync(winner)          @Async
 │        └─ 构造 SmsNotifyMessage → SmsNotifyExchange → SmsNotifyQueue
 ├─ 【改】for 每个中奖者：qqEmailService.sendEmailNotifyAsync(winner) @Async
 │        └─ 构造 EmailNotifyMessage → EmailNotifyExchange → EmailNotifyQueue
 └─ 结束（快速 ack；不再直接发 SMTP）

SmsNotifyQueue  ─→ SmsNotifyListener  ─┐ 共用 notifyListenerContainerFactory
EmailNotifyQueue ─→ EmailNotifyListener ─┘（concurrency=5 / prefetch=5）
 SmsNotifyListener  → SmsSender.send → MockSmsSender 打「【模拟短信】」日志
 EmailNotifyListener → 拼主题/正文 → qqEmailService.sendSimpleMail 真实发 SMTP
      消费失败 → 重试 5 次 → 死信 DlxQueue → DlxReceiver 记录存档
```

## 4. 新增/修改清单

| 文件 | 动作 | 说明 |
|---|---|---|
| `common/config/DirectRabbitConfig` | 修改 | 新增 `SmsNotifyQueue`、`EmailNotifyQueue`（均 durable + deadLetterExchange=`DlxExchange` / deadLetterRoutingKey=`DlxRouting`）、`SmsNotifyExchange`、`EmailNotifyExchange`（均 durable）、`SmsNotifyRouting`、`EmailNotifyRouting` 及绑定，照抄现有 DLX 模式；注册 `SimpleRabbitListenerContainerFactory` bean（名 `notifyListenerContainerFactory`）。**注意：Spring Boot 全局 listener 属性与 MessageConverter 只作用于默认工厂**，自定义工厂必须显式配置：① 注入 `Jackson2JsonMessageConverter`（否则消息退化为 Java 序列化）；② stateless 重试拦截器 `maxAttempts=5`（与全局参数一致，否则重试不生效）；③ concurrency=5、prefetchCount=5 |
| `service/dto/SmsNotifyMessage` | 新增 | 平铺五字段：`winnerName / phone / activityName / prizeTier / prizeName` |
| `service/dto/EmailNotifyMessage` | 新增 | 平铺五字段：`winnerName / email / activityName / prizeTier / prizeName` |
| `service/impl/SMSServiceImpl` | 修改 | 接口与方法签名不动（`SMSService.sendSmsAsync`）；实现改为构造消息并 `rabbitTemplate.convertAndSend(SmsNotifyExchange, SmsNotifyRouting, msg)`；保留 `@Async`；发布失败 catch + log error，fail-soft |
| `service/SMSService` | 修改 | 删除无调用方的死方法 `notifyWinnersAsync(List)`（唯一实现也在本次改动面内） |
| `service/QQEmailService` + `impl` | 修改 | 删除 `notifyWinners(List)`（调用方仅 `MqReceiver`，已核实）；新增 `@Async void sendEmailNotifyAsync(WinnerRecordDTO winner)`：构造 `EmailNotifyMessage` 发布到 `EmailNotifyExchange`，发布失败 catch + log，fail-soft；保留 `sendSimpleMail(to, subject, content)` 作为 SMTP 原语；原 `notifyWinners` 中的主题/正文拼装文案迁入 `EmailNotifyListener` |
| `service/impl/mq/SmsNotifyListener` | 新增 | `@RabbitListener(queues = SmsNotifyQueue, containerFactory = "notifyListenerContainerFactory")`，调 `SmsSender`；消费异常直接抛出交给重试/死信 |
| `service/impl/mq/EmailNotifyListener` | 新增 | `@RabbitListener(queues = EmailNotifyQueue, containerFactory = "notifyListenerContainerFactory")`，从消息拼装主题/正文后调 `sendSimpleMail`；空邮箱跳过（沿用现状语义）；消费异常直接抛出交给重试/死信 |
| `service/SmsSender` 接口 + `service/impl/MockSmsSender` | 新增 | 单方法 `send(SmsNotifyMessage)`；Mock 拼装内容并打 `【模拟短信】` 日志；原 `SmsBaoServiceImpl` 中的签名前缀拼接、奖品等级格式化（`formatPrizeTier`）逻辑迁入 |
| `service/impl/mq/MqReceiver` | 小幅修改 | 通知段：删除 `qqEmailService.notifyWinners(list)` 批量调用；改为对每个中奖者依次调用 `smsService.sendSmsAsync(winner)` 与 `qqEmailService.sendEmailNotifyAsync(winner)`；校验/状态扭转/存记录/回滚段**不动** |
| `service/impl/SmsBaoServiceImpl` | 删除 | 连同 `common/config/sms/SmsBaoConfig` 一起删除 |
| `application.properties` | 修改 | 删除 `smsbao.username / api-key / signature / api-url` 四条配置 |

**不修改**：验证码链路（`SMSUtil` / `VerificationCodeServiceImpl` / `SMSTest`）、全局 MQ 重试配置（`spring.rabbitmq.listener.simple.retry.max-attempts=5`，仍作用于 `MqReceiver` 默认容器）、`DlxReceiver`。

## 5. 数据与并发

- 消息体：两个 DTO 均 JSON 序列化进队列，RabbitMQ 管理 UI 可直接查看 body（演示点）。
- 并发来源分层：第一跳 `@Async` 线程池（core 10 / max 20 / queue 20）扇出发布；第二跳由监听容器 5 消费者 × prefetch 5 受控并发（两队列各自 5 并发）。邮件真实 SMTP 发送的并发上限由此受控，不再占用 `MqReceiver` 消费线程。
- 投递语义：第二跳 at-least-once；短信 Mock 重复仅多一条日志，邮件重复为真实副作用（见 §2 已知取舍），均如实接受。

## 6. 错误处理

| 故障点 | 行为 | 理由 |
|---|---|---|
| 二级队列发布失败（短信或邮件） | catch + log error，不抛、不触发抽奖回滚 | 通知故障不得连累主流程事务 |
| 短信通知消费失败 | 监听器抛出 → 重试 5 次（自定义工厂显式配置；耗尽后 reject 不回队 → 经队列 DLX 绑定进死信）→ 死信 `DlxQueue` → `DlxReceiver` 记录 | Mock 不会失败，此管道为未来真实驱动预留 |
| 邮件通知消费失败（SMTP 故障） | 同上：重试 5 次 → 死信存档 | 相比现状（仅 log 即丢）是升级 |
| 邮箱为空 | 监听器跳过并 log warn（沿用现状语义，不进重试） | 现状即跳过 |
| MqReceiver 自身业务失败 | 现状回滚逻辑不动 | 范围外 |

## 7. 配置与文档同步

代码外必须同步的引用（行号会漂移，按文件定位）：

| 文档 | 更新内容 |
|---|---|
| `docs/test/08-接口契约.md` | 短信/邮件通知通道描述：短信宝/直接 SMTP → "两级通知队列（短信 Mock / 邮件真实）" |
| `docs/test/05-安全测试报告.md` | smsbao api-key 明文条目：标记为"已随本次改造移除" |
| `docs/test/01-测试计划与方案.md` | 真实外发链路描述中短信宝字样更新 |

配置开关：不新增（Mock 是短信唯一驱动，YAGNI；将来接真实驱动时再引入 `sms.provider` 选择开关）。

## 8. 验收标准

1. 短信链路端到端：发起抽奖 → `SmsNotifyQueue` 出现 N 条消息 → `SmsNotifyListener` 逐条输出「【模拟短信】发往 <phone>：<拼接内容>」日志。
2. 邮件链路端到端：发起抽奖 → `EmailNotifyQueue` 出现 N 条消息 → `EmailNotifyListener` 真实发出 SMTP 邮件（真实邮箱收到中奖通知，文案与现状一致）。
3. 死信链路：人为使任一监听器抛异常（临时测试代码）→ 观察重试 5 次 → `DlxQueue` 收到消息，`DlxReceiver` 输出死信记录日志。
4. 主流程隔离：人为使发布失败 → 抽奖事务正常提交，仅 error 日志，无回滚；邮件队列堆积时 `MqReceiver` 仍快速 ack（不被 SMTP 阻塞）。
5. 回归：既有接口自动化 152 条、UI 自动化 41 条全绿；MQ 恢复后抽奖链路 e2e 不回归。
6. 清理：代码库中无 `SmsBaoServiceImpl` / `SmsBaoConfig` / `smsbao.*` / `notifyWinners(` 批量方法；§7 四份文档引用已更新。
