Lottery_System 抽奖系统

一个基于 Spring Boot 的抽奖系统示例项目，支持用户注册/登录、验证码登录、活动创建、奖品管理、抽奖与中奖通知等功能。

## 功能简介

- 用户注册（管理员/普通用户）
- 密码登录、短信验证码登录
- 验证码发送频控（冷却时间 + 小时上限）
- 活动创建与奖品配置
- 抽奖流程与中奖记录
- 短信/邮件通知能力（可选）

## 技术栈

- Java 17（建议）
- Spring Boot
- MyBatis
- MySQL
- Redis
- RabbitMQ
- Maven
- 静态页面（`src/main/resources/static`）

## 项目结构

```text
src/main/java/org/example/lottery_system
├─ controller        # 接口层
├─ service           # 业务层
├─ dao               # 数据访问层
├─ common            # 公共配置/工具/异常/拦截器
└─ ...               # 其他模块

src/main/resources
├─ application.properties
├─ static            # 前端静态页面
└─ ...

## 本地配置（敏感值不入库）

仓库里**不含**数据库、短信、邮箱、AI 等凭据。启动前：

1. 复制 `src/main/resources/application-local.properties.example` 为同目录的 `application-local.properties`
2. 按注释填本机真值（MySQL 密码、阿里云/短信宝/QQ 邮箱/DeepSeek 的 key 等）
3. 该文件已被 `.gitignore` 忽略，不会提交

`application.properties` 中一律使用 `${环境变量:默认值}` 占位，也可以直接用同名系统环境变量注入，不建文件。

另外，`zipkin/` 下的 Zipkin 可执行 jar（129 MB）不在仓库里，下载地址与启动命令见 `docs/design/Zipkin链路追踪.md`。
