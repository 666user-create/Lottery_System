# 抽奖系统接口自动化测试（Python + pytest + requests）

> 本工程为**第③④步**用例载体，替代上轮引入的 Java RestAssured（已按业主决策移除，回归纯 Python 栈）。
> 契约依据：`docs/test/contract/api-contract.md`（单一事实来源）

---

## 1. 前置条件

| 项 | 要求 | 校验方式 |
|---|---|---|
| 抽奖系统应用 | 运行于 `http://localhost:8080` | 工程内置**实例指纹校验**（防连到别的系统） |
| MySQL | `127.0.0.1:3306/lottery_system` 可连 | 应用启动正常即可 |
| Redis | `localhost:8888` | 应用启动正常即可 |
| Python | 3.11+，已装 pytest 8.3.4 / requests 2.32.3 | `python -m pytest --version` |

```bash
# 启动被测应用（在项目根目录）
mvnw.cmd spring-boot:run
```

## 2. 执行命令

```bash
cd tests/api

# 全量（关键路径 + 边界）
python -m pytest -v

# 仅冒烟（P0，核心链路）
python -m pytest -m smoke -v

# 仅关键路径（第③步）
python -m pytest -m keypath -v

# 仅边界与异常（第④步）
python -m pytest -m boundary -v

# 仅反向验证（故意破坏，断言必须失败）
python -m pytest -m reverse -v

# 指定模块
python -m pytest test_01_auth_keypath.py -v

# Allure 报告（生成物，不入库）
python -m pytest --alluredir=reports/allure-results
allure serve reports/allure-results
```

## 3. 环境变量（可选覆盖）

| 变量 | 默认 | 说明 |
|---|---|---|
| `LOTTERY_BASE_URL` | `http://localhost:8080` | 被测地址（端口冲突时改这里，而非改代码） |
| `LOTTERY_TIMEOUT` | `15` | 普通接口超时（秒） |
| `LOTTERY_AI_TIMEOUT` | `90` | AI 接口超时（依赖外部大模型） |
| `LOTTERY_MQ_TIMEOUT` | `35` | 抽奖接口超时（MQ 不可达时会阻塞） |

```bash
# 例：应用跑在 8081 时
LOTTERY_BASE_URL=http://localhost:8081 python -m pytest -m smoke -v
```

## 4. 测试数据与清理（绝不污染）

| 数据类型 | 策略 |
|---|---|
| 测试账号 | **固定复用** `qa_auto_user@test.com` / `qa_auto_admin@test.com`（首次自动注册，后续登录复用）→ 不累积 |
| FAQ | 用例内新增，`faq_cleanup` fixture 结束时调用 API 删除（含向量连带删除） |
| 工单 | 用例内创建，`handover_cleanup` fixture 结束时置为 `CLOSED`（无删除接口） |
| 基础真实数据 | **只读**；列表类用例断言 `total >= 基线`（防误删检测），绝不修改 |
| 无删除 API 的写操作（活动/奖品创建） | 第④步执行，配套 DB 白名单清理（见下） |

**历史清理记录**：2026-09-13 按白名单清理 12 个测试账号 + 1 条测试 FAQ，
备份见 `docs/test/backup/cleanup-20260913-*.sql`，清理后 `user=14`（基础）、`faq=2`（基线）。

## 5. 生成物说明（不入库，已写入 .gitignore）

| 生成物 | 路径 | 重新生成命令 |
|---|---|---|
| pytest 缓存 | `.pytest_cache/` | 自动 |
| Python 字节码 | `__pycache__/` | 自动 |
| Allure 结果/报告 | `reports/` | `python -m pytest --alluredir=reports/allure-results` |

## 6. 断言强度约定（对应业主错误 #3）

1. **禁止只断 HTTP 200**：业务失败时 HTTP 仍为 200（`GlobalExceptionHandler`），必须断 `body.code`；
2. **禁止只断元素存在**：接口用例须断数据正确性（回显一致性、字段非空、计数变化、跨接口一致性）；
3. **新断言必须能失败**：第⑤步对每条关键断言做反向验证（故意破坏 → 断言必须失败）；
4. 工具函数见 `common/client.py`：`assert_success(expect_data=True)` 可抓"code=200 但 data=null"的假成功。

## 7. 实例指纹校验（对应业主错误 #1）

`conftest.py::app_fingerprint` 会自动：
1. 校验 `/user-login.html` 可达（404 → 明确失败，提示可能连到了别的系统）；
2. 校验 `/api/faq/list` 无 token 返回 401（抽奖系统特征）；
3. 打印被测实例信息到执行日志。

> 背景：2026-09-13 实测发现 8080 被另一项目（`BlogSystemApplication`）占用，
> 若不校验会"测到别人的系统"并得出完全错误的结论。
