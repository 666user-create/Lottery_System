# JMeter 性能测试脚本与插件说明

> 脚本：`tests/jmeter/lottery_system.jmx`（抽奖系统性能测试计划）
> 环境：Apache JMeter **5.6.3**、JDK 17（`JAVA_HOME=D:\Android\jdk-17`）

## 一、已安装插件（JMeterPlugins Manager + 依赖）

| 插件 | 版本 | 放置位置 | 用途 |
|---|---|---|---|
| jmeter-plugins-manager | 1.12 | `lib/ext/` | 插件管理入口（GUI → Options → Plugins Manager） |
| jmeter-plugins-cmn-jmeter | 0.9 | `lib/` | 插件公共库（manager/casutg 依赖） |
| jmeter-plugins-casutg | 3.1.1 | `lib/ext/` | **jp@gc - Stepping Thread Group**（阶梯加压） |
| jmeter-plugins-standard | 1.4.0 | `lib/ext/` | `CorrectedResultCollector`（Page Data Extractor 依赖的监听器基类） |
| jmeter-plugins-pde | 0.1 | `lib/ext/` | **jp@gc - Page Data Extractor**（页面数据抽取+曲线） |

启动 JMeter 后日志出现 `ClassFinder: Will scan jar ... jmeter-plugins-*.jar` 即表示插件已加载。

## 二、测试设计（在原有 3 个场景基础上新增）

| 场景 | 元件 | 设计 |
|---|---|---|
| setUp-管理员登录取Token | Setup Thread Group + JSON 后置处理器 + JSR223 | 先登录一次，把 `data.token` 写入 JMeter 属性 `${__P(adminToken)}`，供场景2/3 使用（避免硬编码 JWT 过期） |
| 场景4-阶梯加压登录 | **jp@gc - Stepping Thread Group** | 10 线程起步，每 10s 增加 10 线程，上限 50；每级 ramp 5s、保持 15s；线程内 500ms 思考时间 |
| 场景2-活动列表 | **jp@gc - Page Data Extractor**（挂在场景2 下） | 从 `/activity/find-list` 响应中按正则抽取 `"total":N` 与 `"runningCount":N`，绘制业务指标随时间曲线 |

PDE 的正则对配置（jmx 内）：
- key：`"total":` → value：`"total":(\d+)`
- key：`"runningCount":` → value：`"runningCount":(\d+)`

## 三、运行方式

Page Data Extractor 在非 GUI 模式需要响应体，必须用属性文件打开 XML 结果 + 保存响应数据：

```powershell
$env:JAVA_HOME="D:\Android\jdk-17"
D:\apache-jmeter-5.6.3\bin\jmeter.bat -n `
  -q tests\jmeter\pde.properties `
  -t tests\jmeter\lottery_system.jmx `
  -l result.jtl -j jmeter-run.log
```

`tests/jmeter/pde.properties` 内容：
```
jmeter.save.saveservice.output_format=xml
jmeter.save.saveservice.response_data=true
jmeter.save.saveservice.print_field_names=true
```

> 注意：`-Jjmeter.save.saveservice.output_format=xml` 在本机不生效，需用 `-q` 指定属性文件。

## 四、前置条件

- 应用已启动：`http://localhost:8080`（MySQL 3306 / Redis 8888 / RabbitMQ 5672 就绪）
- 存在性能测试管理员账号：`qa_perf_admin@test.com` / `123456`（ADMIN、ACTIVE）
  - 创建方式：先 `POST /user/register` 注册，再 SQL 提权
    `UPDATE user SET identity='ADMIN', status='ACTIVE' WHERE email='qa_perf_admin@test.com';`

## 五、最近一次实测（2026-11-12，本机）

| 标签 | 样本数 | 平均响应 | 最大响应 | 错误 |
|---|---|---|---|---|
| 登录接口-密码登录(阶梯) | 5565 | 6.2 ms | 228 ms | 0 |
| 活动列表查询 | 2000 | 6.6 ms | 232 ms | 0 |
| 中奖记录查询 | 1000 | 3.3 ms | 14 ms | 0 |
| 登录接口-密码登录 | 500 | 4.7 ms | 134 ms | 0 |
| 管理员登录-取Token | 1 | 67 ms | 67 ms | 0 |
| **合计** | **9066** | **~6 ms** | — | **0（0.00%）** |

- 请求成功标准：HTTP 200 且响应体包含 `"code":200`；
- PDE 抽取验证：2000/2000 条活动列表响应均命中 `"total":N` 与 `"runningCount":N`；
- 说明：非 GUI 模式 PDE 图形不落地（需 GUI 查看，或用 CMDRunner 生成 PNG；本机 CMDRunner 的 JMeter home 探测存在兼容问题，未出图）。
