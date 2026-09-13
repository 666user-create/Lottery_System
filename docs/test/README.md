# 抽奖系统（lottery_system）测试文档套件

> 企业级全套测试文档 | 版本 **V2.0（重建版）** | 编写：QA Leader | 日期：2026-09-13
> 测试对象：lottery_system V0.0.1-SNAPSHOT（Spring Boot 3.2.6 / Java 17）
> 测试栈：**Python + pytest + requests**（接口自动化）· Selenium WebDriver 4 + Java + Headless Chrome（UI）· **JMeter 5.6.3 命令行**（性能）· 浏览器 DevTools + Fiddler（手工探索/抓包）

---

## 一、文档索引

| 编号 | 文档 | 路径 |
|---|---|---|
| 00 | **接口契约拆解（单一事实来源：25 接口 + 10 项风险）** | [contract/api-contract.md](contract/api-contract.md) |
| 01 | 测试计划 | [01-测试计划-TestPlan.md](01-测试计划-TestPlan.md) |
| 02 | 测试方案（策略） | [02-测试方案-TestStrategy.md](02-测试方案-TestStrategy.md) |
| 03 | 测试用例集（全模块） | [03-测试用例集/00-用例总览与用户模块.md](03-测试用例集/00-用例总览与用户模块.md) |
| 04 | 需求追踪矩阵 RTM | [04-需求追踪矩阵-RTM.md](04-需求追踪矩阵-RTM.md) |
| 05 | 测试执行记录 | [05-测试执行记录/](05-测试执行记录/) |
| 06 | 缺陷报告 | [06-缺陷报告-BugReport.md](06-缺陷报告-BugReport.md) |
| 07 | 测试总结报告 | [07-测试总结报告-TestSummary.md](07-测试总结报告-TestSummary.md) |
| 08 | 专项报告 | [08-专项报告/](08-专项报告/) |

## 二、可运行交付物（现状）

| 交付物 | 路径 | 状态 |
|---|---|---|
| **接口自动化（Python + pytest + requests）** | `tests/api/`（14 文件 / **147 条用例**） | ✅ **146 passed / 1 xfailed**（25.28s） |
| UI 自动化（Selenium 4 + Java + Headless Chrome） | `docs/test/artifacts/selenium/` | ✅ 6/6 通过 |
| Postman Collection（调试） | `docs/test/artifacts/postman/lottery_system.postman_collection.json` | ✅ 27 请求 / 7 分组 |
| JMeter 5.6.3 脚本 + 结果 + HTML Dashboard | `docs/test/artifacts/jmeter/` · `tests/jmeter/` | ✅ 已真实执行（3500 请求 / 0 错误） |
| 测试数据白名单清理工具 | `tests/api/tools/db_cleanup.py` | ✅ 干跑 / 自动备份 / 关联安全检查 |
| ~~Java RestAssured 接口测试~~ | ~~`src/test/**/api/`~~ | ❌ **已移除**（业主决策：统一 Python 栈，消除双栈数字漂移） |

> **生成物不入库**（已写入 `.gitignore`）：`tests/jmeter/*.jtl`、`tests/jmeter/report/`、`tests/api/reports/`、`__pycache__/`、`.pytest_cache/`、`docs/test/artifacts/screenshots/`、`hs_err_pid*.log`。重新生成命令见第四节。

## 三、测试结论速览（2026-09-13 实测）

| 指标 | 结果 |
|---|---|
| **接口自动化（pytest）** | **147 条：146 passed / 1 xfailed** |
| UI 自动化（Selenium） | 6/6 通过 |
| 安全测试 | 通过：SQL 注入 4 载荷全阻断、身份提权全降级、401/403 权限矩阵有效 |
| 并发测试 | 通过：并发注册无竞态、并发工单无丢失/无重复 id |
| **反向验证** | **通过：故意破坏 3 处 → 6 条断言如实失败**（证明断言非摆设） |
| 性能测试 | 3500 请求 / **0 错误** / 175.6/s / p95 ≤ 6ms |
| 缺陷 | 累计 10 个：**P2×4 已修复、P3×3 已修复**；1 个 xfail（BUG-002，依赖 MQ 环境）；1 个风险登记（BUG-004 密钥） |
| **发布建议** | **通过（Pass）**——P2/P3 缺陷已修复并回归验证；BUG-002 待 MQ 环境就绪后复测 |

## 四、快速开始

### 4.1 启动被测应用

```bash
cd D:\java\lottery_system
$env:MAVEN_OPTS="-Xmx320m -XX:MaxMetaspaceSize=128m"     # 低内存环境建议
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.jvmArguments=-Xmx1024m -XX:MaxMetaspaceSize=224m"
```

> ⚠️ **启动后必须核验实例**（避免测到别的系统）：`/user-login.html` 应 200；未登录访问 `/api/faq/list` 应 **401**。
> 工程内置 `app_fingerprint` fixture 会自动完成校验 —— 背景：2026-09-13 实测发现 8080 曾被另一项目（BlogSystemApplication）占用。

### 4.2 接口自动化（Python 栈）

```bash
cd tests\api
python -m pip install -r requirements.txt      # pytest 8.3.4 + requests 2.32.3

python -m pytest -v                             # 全量 147 条
python -m pytest -m smoke -v                    # 冒烟
python -m pytest -m keypath -v                  # 关键路径（第③步 79 条）
python -m pytest -m boundary -v                 # 边界与异常（第④步 68 条）
```

**环境变量覆盖**（端口冲突无需改代码）：`LOTTERY_BASE_URL`、`LOTTERY_TIMEOUT`、`LOTTERY_AI_TIMEOUT`、`LOTTERY_MQ_TIMEOUT`

### 4.3 UI 自动化（Selenium 4 + Java）

```bash
mvnw.cmd -f docs/test/artifacts/selenium/pom.xml test "-Dselenium.headless=true"
```

### 4.4 性能测试（JMeter 5.6.3 命令行）

```bash
# 1) 登录获取管理员 token，替换 .jmx 中 adminToken 占位符
# 2) 非 GUI 执行（-l 结果文件；-e -o 生成 HTML Dashboard）
& "D:\apache-jmeter-5.6.3\bin\jmeter.bat" -n -t docs\test\artifacts\jmeter\lottery_system.jmx -l tests\jmeter\result.jtl -e -o tests\jmeter\report
```

### 4.5 测试数据清理（跑完就清，绝不碰真实数据）

```bash
cd tests\api
python tools\db_cleanup.py            # 干跑：待删清单 + 关联安全检查
python tools\db_cleanup.py --apply    # 执行：自动先备份到 docs\test\backup\
```

**白名单前缀**：`qa_auto_`（账号/工单/FAQ）· `qa_auto_`/`QA_AUTO_`（奖品/活动）
**基线核对**：清理后应为 `user=14`、`faq=2`、`human_handover=0`

## 五、环境依赖

| 组件 | 要求 | 备注 |
|---|---|---|
| MySQL | `127.0.0.1:3306/lottery_system` | 必备，8.0.42 |
| Redis | `localhost:8888` | 必备（验证码 / 缓存 / 对话记忆） |
| RabbitMQ | `8.136.222.102:5672` | **不可达** → 抽奖接口阻塞（BUG-002，1 条 xfail 覆盖） |
| Python | 3.11+（pytest 8.3.4 / requests 2.32.3） | 接口自动化 |
| Chrome | 最新版 | Selenium 自动匹配驱动 |
| JMeter | 5.6.3（`D:\apache-jmeter-5.6.3`） | 性能测试 |
| DeepSeek Key | 已配置 | AI 客服真实调用 |
| Embedding Key | **为空** | 走关键词兜底（正常降级，已有用例覆盖） |
