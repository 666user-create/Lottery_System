# Zipkin 链路追踪接入说明

> 目的:直观观察 AI 调用过程中的监控指标,实现"用户请求 → 向量检索 → 模型推理"的全链路追踪。
> 与课件 10.3 节等价,差别是本机**没有 Docker**,改用官方可执行 jar 启动 Zipkin。

## 1. 版本与位置

| 组件 | 版本 / 位置 |
|---|---|
| Zipkin Server | 3.6.1(官方 `-exec.jar`,自带 Web UI) |
| jar 路径 | `<项目根>\zipkin\zipkin-server-3.6.1-exec.jar`(**129 MB,不入库**,按下方地址下载) |
| 存储 | 内存(`STORAGE_TYPE=mem`),重启即清空 |
| Web UI | http://localhost:9411/zipkin/ |

下载地址(国内用阿里云镜像,实测 14 MB/s):
```
https://maven.aliyun.com/repository/public/io/zipkin/zipkin-server/3.6.1/zipkin-server-3.6.1-exec.jar
```

## 2. 启动 Zipkin

### 2.1 默认:应用自动拉起(无需任何额外步骤)

**现在只要正常启动应用,Zipkin 会自动跑起来**——`TracingHintRunner` 在应用启动收尾时探测 9411,
没在跑就自动执行 `java -jar zipkin-server...exec.jar`,等端口就绪后再打印启动提示。
所以日常开发**不需要手动跑脚本**。

自动启动由 `application.properties` 控制:
```properties
app.zipkin.auto-start=true                 # false = 关闭自启,改用手动启动(见 §2.2)
app.zipkin.jar=D:/java/lottery_system/zipkin/zipkin-server-3.6.1-exec.jar
app.zipkin.start-timeout-seconds=40
```
自启进程的输出落在 `D:\java\lottery_system\zipkin\zipkin-autostart.log`;它是独立进程,**不随应用退出**。

> 为什么不能"内嵌"进应用:Zipkin 3.x 服务端基于 **Armeria**,不是 Spring MVC,
> 官方已不再提供 2.x 那种"加自动配置依赖塞进自己应用"的做法,所以 Zipkin 只能是独立进程。
> 既然如此,就让应用把它拉起来,省掉手动这一步。

> ⚠️ 已知竞态:应用启动瞬间(约 6 秒内)Zipkin 还没就绪,这期间产生的第一批 span 会被
> `AsyncReporter` 丢弃(日志里 `Spans were dropped due to exceptions`)。之后的 span 正常,
> 不影响使用;如果在意,等日志出现 "已连通 ✅" 后再发请求即可。

### 2.2 手动方式(可选):直接跑官方 jar

> 作者机器上有一个便捷脚本 `start-zipkin.cmd`(带端口预检、支持 `stop`),属于个人环境工具,
> **未随仓库提供**。clone 后按下面的命令启动/停止即可,效果相同。

启动(前台运行,关窗口即停):
```cmd
java -DSTORAGE_TYPE=mem -jar zipkin\zipkin-server-3.6.1-exec.jar
```

要后台常驻可用 PowerShell:
```powershell
Start-Process java -ArgumentList '-DSTORAGE_TYPE=mem','-jar','zipkin\zipkin-server-3.6.1-exec.jar'
```

停止(按端口找进程):
```powershell
Get-NetTCPConnection -LocalPort 9411 -State Listen |
  Select-Object -ExpandProperty OwningProcess -Unique |
  ForEach-Object { Stop-Process -Id $_ }
```

> 课件用的是 `docker run -d --name zipkin-spring-ai -p 9411:9411 -e STORAGE_TYPE=mem openzipkin/zipkin:latest`;
> 本机没有 Docker,jar 方式与它完全等价(同样监听 9411、同样内存存储)。

启动成功的标志(日志):
```
Serving HTTP at /[0:0:0:0:0:0:0:0]:9411 - http://127.0.0.1:9411/
```

## 3. 项目侧配置(已就绪,无需再改)

`pom.xml` 依赖:
```xml
spring-boot-starter-actuator
io.micrometer:micrometer-tracing-bridge-brave
io.zipkin.reporter2:zipkin-reporter-brave
```

`application.properties`:
```properties
spring.application.name=lottery_system
management.tracing.sampling.probability=1.0
management.zipkin.tracing.endpoint=http://localhost:9411/api/v2/spans
management.endpoints.web.exposure.include=health,info,metrics,prometheus
```

## 4. 实测链路(2026-10-16 验证通过)

访问 `POST /api/ai/chat/sync`(默认 `simple` 模式)后,Zipkin 收到的 span 树:

```
http post /api/ai/chat/sync   2737.4 ms   ← 用户请求(服务端根 span)
├─ embedding                   443.9 ms   ← 本地向量化(bge-small-zh-v1.5 / ONNX)
├─ chat deepseek-chat         1002.6 ms   ← 第 1 轮:带工具列表的对话补全
│  └─ http post                997.8 ms   ← 调 DeepSeek 的 HTTP 客户端
└─ chat deepseek-chat          764.6 ms   ← 第 2 轮:工具结果回传后的最终回答
   └─ http post                764.0 ms
```

> 两轮 `chat` 就是手写实现里的 **MCP 工具调用循环**(问"帮我查一下我的中奖记录"会触发工具)。
> 不带工具的问题只有一轮 `chat`。
> `chat` span 上带的标签:`gen_ai.system=deepseek`、`gen_ai.operation.name=chat`、
> `gen_ai.request.model=deepseek-chat`、`app.rag.mode=simple`。

`framework` 模式下同样是这棵树(`embedding` + `chat deepseek-chat` + `http post`),
区别只在 span 由 Spring AI 组件自动产生,而非手写埋点。

命令行核对:
```powershell
Invoke-WebRequest 'http://127.0.0.1:9411/api/v2/traces?serviceName=lottery_system&limit=3' -UseBasicParsing
```

## 5. 两种模式都能看到 AI span(已补齐)

| `app.rag.mode` | 追踪到的内容 | 埋点来源 |
|---|---|---|
| `simple`(默认) | `embedding` + `chat deepseek-chat` + `http post` | 向量化由 Spring AI `EmbeddingModel` 自动埋点;对话调用在 `AiCustomerService.callDeepSeek()` 里**手动包了 `Observation`**;HTTP 客户端 span 由 `RestTemplateBuilder` 的观测拦截器产生 |
| `framework` | 同上,另有向量检索等 Spring AI 组件的 span | Spring AI `ChatClient` / `EmbeddingModel` 内置的 Micrometer Observation |

因此**不需要为了看追踪而切换检索模式**。相关代码:

- `AiCustomerService.callDeepSeek()`:`Observation.createNotStarted("chat " + model, observationRegistry)`
- `RestTemplateConfig`:默认 `RestTemplate` 改用 `RestTemplateBuilder` 构造(超时策略不变,仍是"不设置")


## 6. 观察指标的操作步骤

1. 启动 Zipkin → 启动应用(要 AI span 就用 `app.rag.mode=framework`)
2. 打开 http://localhost:9411/zipkin/ ,点 **Run Query**(Service Name 选 `lottery_system`)
3. 左侧列表是调用链,点任一 trace → 右侧显示每个 span 的详情(耗时、起止时间、标签)
4. 想看某次具体请求:控制台会打印 traceId,直接访问
   `http://localhost:9411/zipkin/traces/<traceId>`

### 启动时会自动提醒(不用再记地址)

应用启动的**最后一段日志**会打印 Zipkin 配置与连通状态(由 `TracingHintRunner` 实现,
放在 `ApplicationRunner` 里,正好在 `Started ... in Xs` 之后输出):

```
──────────────────────────────────────────────────────────
  Zipkin 链路追踪已配置
    可视化界面 : http://localhost:9411/zipkin/
    上报地址   : http://localhost:9411/api/v2/spans
    服务名     : lottery_system    采样率: 1.0
    运行状态   : 已连通 ✅
──────────────────────────────────────────────────────────
  看不到数据?先启动 Zipkin(命令见 §2.2),再刷新界面。
```

Zipkin 没启动时,"运行状态"会显示 `未连通 ⚠️(Zipkin 没启动?)`——
看到这行就知道该去启动 Zipkin 了(命令见 §2.2)。探测超时 1.5 秒,连接被拒会立刻返回,不会拖慢启动。


## 7. 踩坑记录

| 现象 | 原因 / 处理 |
|---|---|
| 日志刷 `Spans were dropped due to exceptions ... ConnectException` | Zipkin 没启动;先起 Zipkin 再起应用 |
| `docker: command not found` | 本机无 Docker,用 jar 方式 |
| **Zipkin 突然消失、日志无报错** | **Zipkin 也是 java.exe(路径 D:\java\bin),批量杀 java 进程(任务管理器/taskkill/按路径过滤)会连它一起杀**。特征:它的日志最后一行停在 "Serving HTTP",没有任何异常。批量操作时先按端口停掉 9411 的进程(命令见 §2.2),再重启应用 |
| 手动再起一个实例报 9411 端口占用 | 已有 Zipkin 在跑;先按端口停掉旧进程(§2.2),或直接打开界面 http://localhost:9411/zipkin/ |
| 从后台任务/会话里启动的 Zipkin 随任务一起死 | 控制台会话结束会带走子进程树;用户自己双击 .cmd 没这个问题(脚本内部用 `start` 脱离当前控制台) |
| 改了 `app.rag.mode` 却不生效 | properties 是 **CRLF 行尾**,`-replace '...=simple$'` 匹配不上,别用 `$` 锚点 |
| UI 里查不到数据 | 存储是内存,Zipkin 重启后历史清空;另外确认采样率 `probability=1.0` |

### 手动启动/停止速查(Windows)

```powershell
# 启动(后台;首次启动约 8~12 秒)
Start-Process java -ArgumentList '-DSTORAGE_TYPE=mem','-jar','zipkin\zipkin-server-3.6.1-exec.jar'
# 停止
Get-NetTCPConnection -LocalPort 9411 -State Listen |
  Select-Object -ExpandProperty OwningProcess -Unique |
  ForEach-Object { Stop-Process -Id $_ }
```

- 应用自启时，Zipkin 日志落在 `zipkin\zipkin-autostart.log`（每次自启覆盖）。
- 手动在控制台启动时，输出直接打在控制台；关掉窗口进程即结束。
