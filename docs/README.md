# 抽奖系统（lottery_system）文档中心

> 本目录集中管理项目的**功能设计与实现文档**（`design/`）与**测试文档**（`test/`）。
> 最近整理：2026-09-13

---

## 一、目录结构

```
docs/
├── README.md                        ← 本文件（总索引）
├── design/                          ← 功能设计与实现文档
│   ├── AI客服RAG实现指南.md
│   ├── AI客服-验收清单.md
│   ├── MCP接入方案.md
│   └── sql/
│       └── ai-tables.sql
└── test/                            ← 测试文档（入口：test/README.md）
    ├── README.md                    （索引 + 执行命令 + 生成物清单 + 数据清理）
    ├── contract/api-contract.md     （接口契约拆解：25 接口 + 10 项风险｜单一事实来源）
    ├── 01-测试计划-TestPlan.md
    ├── 02-测试方案-TestStrategy.md
    ├── 03-测试用例集/
    ├── 04-需求追踪矩阵-RTM.md
    ├── 05-测试执行记录/
    ├── 06-缺陷报告-BugReport.md
    ├── 07-测试总结报告-TestSummary.md
    ├── 08-专项报告/                 （接口自动化 / 性能 / UI / 安全）
    ├── artifacts/                   （Postman Collection · JMeter 脚本 · Selenium 工程）
    └── backup/                      （数据清理备份，不入库）
```

## 二、design/ — 功能设计与实现文档

| 文档 | 用途 | 适用读者 |
|---|---|---|
| [AI客服RAG实现指南.md](design/AI客服RAG实现指南.md) | 「小东」AI 客服的完整实现指南：RAG 知识库问答、转人工工单、管理端处理；含概念课、分步实现、验证方法、安全加固 | 后端开发 |
| [AI客服-验收清单.md](design/AI客服-验收清单.md) | AI 客服功能的逐步验收手册（含管理员/普通用户权限区分、验收点、排查表） | QA / 项目经理 |
| [MCP接入方案.md](design/MCP接入方案.md) | MCP（Model Context Protocol）接入方案 | 后端开发 |
| [sql/ai-tables.sql](design/sql/ai-tables.sql) | AI 客服建表脚本：`faq` / `faq_embedding` / `human_handover` | 后端 / DBA |

## 三、test/ — 测试文档

| 入口 | 说明 |
|---|---|
| [test/README.md](test/README.md) | **测试文档总入口**：文档索引、可运行交付物、结论速览、执行命令、环境依赖 |

**核心数字（唯一来源，勿在多处重复维护）**

| 指标 | 值 |
|---|---|
| 接口自动化 | Python + pytest：**152 条**（151 passed / 1 xfailed） |
| UI 自动化 | Selenium 4 + Java：6/6 |
| 性能（JMeter 5.6.3） | 3500 请求 / 0 错误 / 175.6 次每秒 / p95 ≤ 6ms |
| 缺陷 | 12 项：9 已修复并回归 · 1 待修（依赖 MQ 环境）· 2 登记 |

## 四、维护约定

1. **分类**：功能/设计文档放 `design/`，测试文档放 `test/`；新增功能文档请同步更新本索引
2. **生成物不入库**：截图 / `result.jtl` / `report/` / `backup/` / `__pycache__` / `*.log` 均由根目录 `.gitignore` 排除，重新生成命令见 `test/README.md`
3. **数字单一来源**：用例数 / 缺陷数 / 性能数据以 `test/README.md` 与 `test/07-测试总结报告-TestSummary.md` 为准；修改后需 grep 全量对齐
4. **契约优先**：接口相关结论以 `test/contract/api-contract.md` 为依据（每条附代码行号，可核对）
5. **数据安全**：测试数据统一 `qa_auto_` 前缀，清理工具 `tests/api/tools/db_cleanup.py`（默认干跑，`--apply` 前自动备份）
