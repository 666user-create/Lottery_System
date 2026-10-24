# 抽奖系统（lottery_system）测试文档套件

> 企业级全套测试文档 | 版本 **V4.0（6 段骨架 + 明细层）** | 编写：QA Leader | 日期：2026-10-20
> 测试对象：lottery_system V0.0.1-SNAPSHOT（Spring Boot 3.2.6 / Java 17）
> 测试栈：**Python + pytest + requests**（接口自动化）· Selenium WebDriver 4.51.0 + Java + Headless Chrome（UI）· **JMeter 5.6.3 命令行**（性能）· Fiddler（手工探索/抓包）

> **归档约定（V4.0 重构后）**：`docs/test/` 根目录只放 **6 个阶段文件 + 本 README**；面向自己阅读的明细层（接口测试 18 册、执行报告、手工验证总流程）统一放 `docs/test/明细/` 下。可运行代码与脚本一律在 `tests/` 下。

---

## 一、文档索引

### 1.1 主干（6 段骨架，评审视角）

| 编号 | 阶段 | 涵盖内容 | 文件 |
|---|---|---|---|
| 01 | 需求分析与测试计划 | 需求拆解、测试范围与策略（四层测试金字塔）、进度与资源、退出准则、交付物清单 | [01-需求分析与测试计划.md](01-需求分析与测试计划.md) |
| 02 | 需求追溯矩阵（RTM） | **44 个需求点**四维追踪：✅43 / ⚠️1（CMP-004）/ ⏸0 | [02-需求追溯矩阵(RTM).md](02-需求追溯矩阵(RTM).md) |
| 03 | 测试用例与测试数据设计 | 全模块用例设计 + **接口契约拆解（唯一事实来源）** + 测试数据与账号设计 | [03-测试用例与测试数据设计.md](03-测试用例与测试数据设计.md) |
| 04 | 测试环境与数据配置说明 | 软硬件环境、依赖服务（MySQL/Redis/RabbitMQ）、**清理工具与 2026-10-20 实测基线**、运行时账号 | [04-测试环境与数据配置说明.md](04-测试环境与数据配置说明.md) |
| 05 | 测试执行与缺陷跟踪清单 | 执行结果总表、**缺陷台账 5 条**（BUG-001/006/007/008/009，均已修复/消除并回归）、趋势 | [05-测试执行与缺陷跟踪清单.md](05-测试执行与缺陷跟踪清单.md) |
| 06 | 测试总结与质量评估报告 | 质量评估、缺陷统计、退出准则核对、**发布建议（Pass）**、交付物验收 | [06-测试总结与质量评估报告.md](06-测试总结与质量评估报告.md) |

### 1.2 明细层（`明细/`，面向自己看的细节）

| 明细 | 内容 | 文件 |
|---|---|---|
| **接口测试（1 册总览 + 18 册模块）** | 接口自动化 **242 条用例全部通过**，每册自包含：覆盖接口 / 用例清单 / 实测证据 / 关联缺陷 / **完整测试源码 + 逐段走读** | [明细/接口测试/00-总览.md](明细/接口测试/00-总览.md) |
| **手工验证总流程** | 端到端人工走查指南（页面 / 接口 / 步骤 / 误判速查 / 验证记录表），已同步 2026-10-10 前端改版 | [明细/手工验证总流程.md](明细/手工验证总流程.md) |
| UI 测试报告 | 手工旅程记录 + Selenium 自动化报告（**64 条 / 18 测试类**） | [明细/执行报告/03-UI测试报告.md](明细/执行报告/03-UI测试报告.md) |
| 性能测试报告 | JMeter 5.6.3 压测场景、实测数据、瓶颈分析 | [明细/执行报告/04-性能测试报告.md](明细/执行报告/04-性能测试报告.md) |
| 安全测试报告 | SQL 注入、越权/提权、401/403 权限矩阵、敏感信息 | [明细/执行报告/05-安全测试报告.md](明细/执行报告/05-安全测试报告.md) |

> 溯源：V3.0「8 份合并报告」（01/06/07/08 四份根文件）已按阶段重组为 6 段骨架；内容未丢弃（重组前经 `tmp/check/loss_check.py` 对快照逐行核销）；接口测试明细 17 册保持原样，不并入主干。

## 二、可运行交付物（代码与脚本统一在 `tests/`）

| 交付物 | 路径 | 状态 |
|---|---|---|
| **接口自动化（Python + pytest + requests）** | `tests/api/`（18 文件 / **242 条用例**，文档拆为 1 册总览 + 18 册模块） | ✅ **242 passed / 0 xfailed**（21.62s，2026-10-20） |
| UI 自动化（Selenium 4.51 + Java + Headless Chrome） | `tests/ui/`（**18 个测试类 / 64 条用例**） | ✅ 64/64 通过（整轮 619.47s，覆盖全部 21 个静态页面，2026-10-20） |
| Postman Collection（调试） | `tests/postman-collection.json` | ✅ 27 请求 / 7 分组 |
| JMeter 5.6.3 脚本 + 结果 + HTML Dashboard | `tests/jmeter/` | ✅ 已真实执行（3500 请求 / 0 错误） |
| UI 自动化截图存档 | `tests/ui/screenshots/` | 生成物，不入库 |
| **Allure 测试报告（HTML，双栈）** | `tests/api/reports/allure-results/`、`tests/ui/reports/allure-results/` | ✅ 接口 242 条 / UI 64 条，生成物不入库 |
| 测试数据白名单清理工具 | `tests/api/tools/db_cleanup.py` | ✅ 干跑 / 自动备份（只留最新一份，命名=日期时间+.sql）/ 关联安全检查 |
| ~~Java RestAssured 接口测试~~ | ~~`src/test/**/api/`~~ | ❌ **已移除**（业主决策：统一 Python 栈，消除双栈数字漂移） |

> **生成物不入库**（已写入 `.gitignore`）：`tests/jmeter/*.jtl`、`tests/jmeter/report/`、`tests/api/reports/`、`__pycache__/`、`.pytest_cache/`、`tests/ui/screenshots/`、`*.log`。重新生成命令见第四节。

## 三、测试结论速览（2026-10-20 实测）

| 指标 | 结果 |
|---|---|
| **接口自动化（pytest）** | **242 条：242 passed / 0 xfailed**（21.62s；smoke/keypath/boundary/reverse/perf 5 类 marker） |
| UI 自动化（Selenium） | **64 条全部通过**（18 个测试类，619.47s），覆盖 **21 个静态页面** |
| 安全测试 | 通过：SQL 注入 4 载荷全阻断、身份提权全降级、401/403 权限矩阵有效 |
| 并发测试 | 通过：并发注册无竞态、并发工单无丢失/无重复 id |
| **反向验证** | **通过：故意破坏 → 断言如实失败**（证明断言非摆设） |
| 性能测试 | 3500 请求 / **0 错误** / 175.6/s / p95 ≤ 6ms |
| 需求追溯 | RTM **44 行**：✅43 / ⚠️1（CMP-004）/ ⏸0 |
| 缺陷 | 累计 **5 个**：**P2×4、P3×1，全部已修复/已消除并回归**；0 个 xfail（BUG-001 已随 MQ 恢复消除）；0 个风险登记 |
| **发布建议** | **通过（Pass）**——P2/P3 缺陷已修复并回归验证；BUG-001 已消除（本机 MQ，抽奖链路端到端可用） |

## 四、快速开始

### 4.1 启动被测应用

```powershell
cd D:\java\lottery_system
# 推荐：脱离会话启动，避免应用进程被作业对象回收（2026-09-15 实测踩坑）
$env:MAVEN_OPTS="-Xmx320m -XX:MaxMetaspaceSize=128m"
Start-Process -FilePath "cmd.exe" -WindowStyle Hidden -ArgumentList "/c",
  ".\mvnw.cmd spring-boot:run `"-Dspring-boot.run.jvmArguments=-Xmx1024m -XX:MaxMetaspaceSize=224m -XX:TieredStopAtLevel=1`" > .current-run.log 2>&1"
```

> ⚠️ **启动后必须核验实例**（避免测到别的系统）：`/user-login.html` 应 200；未登录访问 `/user/base-user/find-list` 应 **401**。
> 工程内置 `app_fingerprint` fixture 会自动完成校验 —— 背景：2026-09-13 实测发现 8080 曾被另一项目（BlogSystemApplication）占用。

### 4.2 接口自动化（Python 栈）

```bash
cd tests\api
python -m pip install -r requirements.txt      # pytest 8.3.4 + requests 2.32.3（含 allure-pytest）

python -m pytest -v                             # 全量 242 条
python -m pytest -m smoke -v                    # 冒烟
python -m pytest -m keypath -v                  # 关键路径
python -m pytest -m boundary -v                 # 边界与异常

# 带 Allure 结果输出（报告见 4.6）
python -m pytest -q --alluredir=reports/allure-results --clean-alluredir
```

**环境变量覆盖**（端口冲突无需改代码）：`LOTTERY_BASE_URL`、`LOTTERY_TIMEOUT`、`LOTTERY_AI_TIMEOUT`、`LOTTERY_MQ_TIMEOUT`

### 4.3 UI 自动化（Selenium 4.51 + Java）

```powershell
# 全量（18 类 / 64 条，约 10 分钟）
& .\mvnw.cmd -f tests\ui\pom.xml test *> tmp\logs\ui-full.log
# 指定类
& .\mvnw.cmd -f tests\ui\pom.xml test "-Dtest=LoginPageUiTest"   # 引号包整体，勿用 + 连接
# 有头模式调试
& .\mvnw.cmd -f tests\ui\pom.xml test "-Dselenium.headless=false"
# 结果以 tests\ui\target\surefire-reports\*.xml 为准（Maven 控制台数字可能因截断不全）
```

### 4.4 性能测试（JMeter 5.6.3 命令行）

```bash
# 1) 登录获取管理员 token，替换 .jmx 中 adminToken 占位符
# 2) 非 GUI 执行（-l 结果文件；-e -o 生成 HTML Dashboard）
& "D:\apache-jmeter-5.6.3\bin\jmeter.bat" -n -t tests\jmeter\lottery_system.jmx -l tests\jmeter\result.jtl -e -o tests\jmeter\report
```

### 4.5 测试数据清理（跑完就清，绝不碰真实数据）

```bash
cd tests\api
python tools\db_cleanup.py            # 干跑：待删清单 + 关联安全检查
python tools\db_cleanup.py --apply    # 执行：自动先备份到 tests/api/db-backup\（只留最新一份）
```

**白名单前缀**：`qa_auto_`（账号/工单/FAQ）· `qa_auto_`/`QA_AUTO_`（奖品/活动）
**基线核对**：以 [04-测试环境与数据配置说明.md](04-测试环境与数据配置说明.md) §4.2 的 **2026-10-20 实测基线**为准（`user=15`、`activity=18`、`prize=6`、`faq=24`、`human_handover=5`；判读方式是「跑完自动化后回升到清理前水位」，不是死记数字）。

### 4.6 生成 Allure 报告（接口 + UI 双栈）

```bash
# Allure 命令行：本机已装于 D:\allure-2.29.0（2.29.0）
set JAVA_HOME=D:\java

# 接口报告（先跑 4.2 里带 --alluredir 的那条命令产出结果）
D:\allure-2.29.0\bin\allure.bat generate tests\api\reports\allure-results ^
    -o tests\api\reports\allure-html --clean --name "抽奖系统-接口自动化测试报告"

# UI 报告（先跑 4.3，结果自动产出）
D:\allure-2.29.0\bin\allure.bat generate tests\ui\reports\allure-results ^
    -o tests\ui\reports\allure-html --clean --name "抽奖系统-UI自动化测试报告"

# 在线预览（自动开浏览器，带热重载）
D:\allure-2.29.0\bin\allure.bat serve tests\api\reports\allure-results
```

**报告内容**：接口侧按 `Epic → Feature → Story` 三层分组（7 个特性）、环境信息、失败分类；UI 侧同样三层分组，且**用例失败时自动附「URL / 页面标题 / 截图 / 页面源码」4 个附件**（由 `BaseUiTest.ALLURE_ON_FAILURE` 实现）。

## 五、环境依赖

| 组件 | 要求 | 备注 |
|---|---|---|
| MySQL | `127.0.0.1:3306/lottery_system` | 必备，8.0.42 |
| Redis | `localhost:8888` | 必备（验证码 / 缓存 / 对话记忆） |
| RabbitMQ | **`localhost:5672`（本机实例）** | 可用（Windows 服务自启，账号 `admin/admin`）；配置项 `${RABBITMQ_HOST:localhost}` 支持环境变量覆盖。原 BUG-001 已消除 |
| Python | 3.11+（pytest 8.3.4 / requests 2.32.3） | 接口自动化 |
| Chrome | 155.x | Selenium Manager 需离线缓存驱动（`SE_OFFLINE=true`） |
| JMeter | 5.6.3（`D:\apache-jmeter-5.6.3`） | 性能测试 |
| DeepSeek Key | 已配置 | AI 客服真实调用 |
| Embedding Key | **为空** | 走关键词兜底（正常降级，已有用例覆盖） |
