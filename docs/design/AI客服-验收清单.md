# 「小东」AI 客服 — 亲自验收指南

> 给项目经理的逐步验收手册。按顺序做,每一步都有「操作」和「预期结果」。
> 全部通过 = 功能合格。如某一步结果不对,对照文档末尾的「排查表」。
> 验收用时约 15~20 分钟。

---

## 一、验收前准备(3 分钟)

| 检查项 | 要求 | 怎么查 |
|---|---|---|
| MySQL 已启动 | 端口 3306 | 任务管理器看 mysql 服务 |
| Redis 已启动 | 端口 8888 | 任务管理器看 redis 服务 |
| 应用未运行 | 8080 端口空闲 | `netstat -ano` 看 8080 无监听 |
| 知识库有数据 | faq 表有 2 条记录 | 见下方 SQL 命令 |
| 工单表是空的 | human_handover 表 0 条 | 见下方 SQL 命令 |

```bash
# 查看知识库和工单(终端执行,乱码不影响判断,看 id 数量即可)
"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -h127.0.0.1 -P3306 -uroot -proot lottery_system -e "select id, question from faq; select count(*) as handover_cnt from human_handover;"
```

**预期:** faq 表显示 2 条(id=1 怎么参与抽奖、id=2 中奖后怎么领奖),handover_cnt=0。
**如果 faq 表是空的**,先执行 `docs/design/sql/ai-tables.sql` 建表,再手动插入两条:

```sql
insert into faq(question, answer, category) values
('怎么参与抽奖?', '登录后进入活动页面,点击立即抽奖即可。', '抽奖规则'),
('中奖后怎么领奖?', '中奖后系统会通过邮箱和短信通知您,请在三日内填写收货地址。', '领奖');
```

---

## 二、启动应用(2 分钟)

在项目根目录 `D:\java\lottery_system` 打开终端,执行:

```bash
mvnw.cmd spring-boot:run
```

看到如下日志说明启动成功(注意看红框部分):

```
Tomcat started on port 8080 (http) with context path ''
```

> 日志里出现 RabbitMQ 连接超时是正常的(公司 RabbitMQ 服务器不在本机),不影响验收。

**验证:** 浏览器打开 `http://localhost:8080/user-login.html`,能看到登录页面 = 启动成功。

---

## 三、注册并登录测试账号(3 分钟)

**为什么要分两个账号:** 知识库管理、管理端工单处理是**管理员专属**接口(普通用户访问返回 403);
聊天、转人工是用户功能。所以需要分别准备"普通用户"和"管理员"两个账号。

### 方式 A:用页面注册登录(推荐,顺便测前端)

**普通用户(测聊天/转人工):**
1. 浏览器打开 `http://localhost:8080/user-register.html`,注册一个账号
   (姓名、邮箱、手机号、密码随便填,身份选"普通用户")
2. 打开 `http://localhost:8080/user-login.html` 登录
3. 登录成功后按 F12 打开开发者工具 → Console 输入 `localStorage.getItem('token')`
4. 复制显示的 token 字符串(很长的一串,以 eyJ 开头),记为 `USER_TOKEN`

**管理员(测知识库/工单管理):**
1. 浏览器打开 `http://localhost:8080/register.html`,注册一个管理员账号
   (密码必填;如注册页有身份选项选"管理员")
2. 打开 `http://localhost:8080/blogin.html`(管理端登录页)登录
3. 同样在 Console 里取 `localStorage.getItem('token')`,记为 `ADMIN_TOKEN`

> ⚠️ 注意:两个 token 别搞混。用户端接口用 `USER_TOKEN`,管理端接口用 `ADMIN_TOKEN`。

### 方式 B:用命令注册登录(不想点页面就用这个)

```bash
# 注册普通用户(邮箱密码随意,别和现有账号重复)
curl -X POST http://localhost:8080/user/register -H "Content-Type: application/json" -d "{\"name\":\"验收员\",\"mail\":\"yanshou@test.com\",\"phoneNumber\":\"13900000001\",\"password\":\"123456\",\"identity\":\"NORMAL\"}"

# 普通用户登录,拿到 USER_TOKEN
curl -X POST http://localhost:8080/user/password/login -H "Content-Type: application/json" -d "{\"loginName\":\"yanshou@test.com\",\"password\":\"123456\"}"

# 注册管理员(携带 X-Admin-Secret 口令; 口令值见本机 application-local.properties 的 ADMIN_SECRET, 不入库)
curl -X POST http://localhost:8080/user/register -H "Content-Type: application/json" -H "X-Admin-Secret: $ADMIN_SECRET" -d "{\"name\":\"验收管理员\",\"mail\":\"adminys@test.com\",\"phoneNumber\":\"13900000002\",\"password\":\"123456\",\"identity\":\"ADMIN\"}"

# 管理员登录,拿到 ADMIN_TOKEN
curl -X POST http://localhost:8080/user/password/login -H "Content-Type: application/json" -d "{\"loginName\":\"adminys@test.com\",\"password\":\"123456\",\"mandatoryIdentity\":\"ADMIN\"}"
```

登录响应里的 `data.token` 就是对应 token,复制备用。

---

## 四、逐步验收(核心,10 分钟)

> 下面每个「验收点」都是独立测试,做完打勾 ✅。
> 带 token 的步骤,把 `TOKEN` 替换成你上面复制的 token。

### 验收点 1:知识库管理接口 ✅ 预期:增/查/删都能成功

> ⚠️ 本验收点必须用**管理员** token(`ADMIN_TOKEN`),普通用户 token 会返回 403。

```bash
# 1.1 新增一条知识(用管理员 token)
curl -X POST http://localhost:8080/api/faq/add -H "Content-Type: application/json" -H "token: ADMIN_TOKEN" -d "{\"question\":\"中奖要交手续费吗?\",\"answer\":\"不需要,中奖完全免费,谨防诈骗。\",\"category\":\"领奖\"}"

# 1.2 查询列表(用管理员 token)
curl "http://localhost:8080/api/faq/list?page=1&pageSize=10" -H "token: ADMIN_TOKEN"

# 1.3 删除刚才那条(记下它返回的 id,比如 3)
curl -X DELETE "http://localhost:8080/api/faq/delete?id=3" -H "token: ADMIN_TOKEN"
```

**预期:**
- 1.1 返回 `"code":200,"data":3`(id)
- 1.2 列表里能看到 3 条(原有2条+新增1条)
- 1.3 返回 `"code":200,"data":true`,再查列表变回 2 条

> ⚠️ 注意:新增接口返回的 `data` 是 FAQ 的 id,删除时要填对。如果 id 不是 3,以实际返回为准。

### 验收点 2:AI 聊天 + 知识库检索(RAG 核心)✅ 预期:回答引用知识库内容

浏览器打开 `http://localhost:8080/chat.html`,在输入框提问:

```
请问怎么参与抽奖?
```

**预期:** 小东回答中包含"登录后进入活动页面""立即抽奖"这类知识库里的内容,而不是泛泛而谈。
(现在是关键词检索兜底,因为没填向量 key,效果已够用;填了向量 key 后更准。)

### 验收点 3:多轮对话记忆 ✅ 预期:第二问能接上上下文

继续在上面的聊天框里追问:

```
那中奖之后呢?
```

**预期:** 小东能明白你在问"中奖后怎么办",回答包含"邮箱""短信""三日内填写收货地址"等领奖知识。

### 验收点 4:转人工 — 关键词触发 ✅ 预期:自动生成工单

在聊天框输入:

```
我要投诉,转人工
```

**预期:**
1. 小东回复"好的,已为您转接人工客服,请稍候…"
2. 数据库里多了一条工单。验证命令:

```bash
"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -h127.0.0.1 -P3306 -uroot -proot lottery_system -e "select id, session_id, status from human_handover;"
```

**预期:** 有一条记录,status=`PENDING`(待处理)。

### 验收点 5:转人工 — 主动按钮 ✅ 预期:登录用户可转人工

1. 确认你已经登录(chat.html 读取 localStorage 的 token)
2. 在聊天框随便输入一句话(作为问题),然后点「转人工」按钮
3. 弹出"已为您转接人工客服"

**预期:** 数据库 human_handover 又多了 1 条,且这条的 `user_id` 有值(登录用户)。

> 如果没登录就点「转人工」,会提示"转人工需要先登录哦",这也是符合预期的。

### 验收点 6:管理端工单处理 ✅ 预期:能看、能处理、状态变化

> ⚠️ 本验收点必须用**管理员**账号登录(管理端工单接口仅 ADMIN 可访问,普通用户 403)。

浏览器打开 `http://localhost:8080/blogin.html` 用管理员账号登录,再打开 `http://localhost:8080/admin-handover.html`:

1. 页面应显示「待处理」工单列表(验收点 4、5 生成的)
2. 点某条工单的「处理中」按钮 → 状态变蓝色「处理中」
3. 点「已解决」按钮,弹出框填备注(如"已电话联系用户")→ 状态变绿色「已解决」
4. 点顶部「已解决」筛选按钮 → 能看到刚才处理的工单

**预期:** 状态切换正确,刷新页面数据不丢。

### 验收点 7:安全校验 ✅ 预期:未登录 401、普通用户 403

**7.1 未登录访问管理接口(清掉 token 或新开无痕窗口):**

```bash
curl "http://localhost:8080/api/handover/list"
curl "http://localhost:8080/api/faq/list"
```

**预期:** 都返回 HTTP 401(未经授权),说明管理接口有登录保护。

**7.2 普通用户访问管理接口(用 USER_TOKEN):**

```bash
curl "http://localhost:8080/api/faq/list" -H "token: USER_TOKEN"
curl "http://localhost:8080/api/handover/list" -H "token: USER_TOKEN"
```

**预期:** 都返回 HTTP 403(禁止访问),说明管理接口仅管理员可用。

**7.3 管理员访问用户端转人工接口(用 ADMIN_TOKEN):**

```bash
curl -X POST http://localhost:8080/api/handover/create -H "Content-Type: application/json" -H "token: ADMIN_TOKEN" -d '{"sessionId":"sess-safe","question":"安全测试"}'
```

**预期:** 返回成功(管理员也是登录用户,可以转人工)。

---

## 五、可选验收:向量检索(填了 embedding key 才测)

默认没填 `ai.embedding.api-key` 时走关键词兜底,功能已经可用。
如果你申请了硅基流动的 key(https://cloud.siliconflow.cn,免费),可以做进阶验收:

1. 在 `application.properties` 填上 `ai.embedding.api-key=sk-你的key`
2. 重启应用
3. 清空旧向量重新生成:`delete from faq_embedding;` 然后重新调 1.1 的接口加知识
4. 再看应用日志,应出现「向量检索命中 N 条」而不是「降级关键词」
5. 用意思相近但字面不同的问法测试(如"怎么才能抽奖"),向量检索能匹配上,关键词兜底可能匹配不上——这就是向量检索的价值

---

## 六、验收结果汇总表(打印出来打勾)

| # | 验收点 | 通过(✅/❌) | 备注 |
|---|---|---|---|
| 准备 | 数据库有 2 条知识、工单表为空 | | |
| 0 | 应用启动,登录页可访问 | | |
| 1 | 知识库增/查/删(管理员) | | |
| 2 | AI 回答引用知识库(RAG) | | |
| 3 | 多轮对话记忆 | | |
| 4 | 关键词触发转人工 → 工单 PENDING | | |
| 5 | 按钮转人工 → 工单带 user_id | | |
| 6 | 管理端查看/处理工单(管理员) | | |
| 7.1 | 未登录访问管理接口被拦(401) | | |
| 7.2 | 普通用户访问管理接口被拦(403) | | |
| 7.3 | 管理员可正常转人工 | | |
| 可选 | 向量检索(填 key 后) | | |

---

## 七、常见问题排查表

| 现象 | 原因 | 处理 |
|---|---|---|
| 聊天问知识库问题,回答很泛、没引用知识 | 知识库没数据 / 没命中 | 检查 faq 表有数据;看应用日志有没有「关键词兜底命中 N 条」 |
| 日志显示「向量检索失败,降级关键词」 | embedding key 为空(正常) | 不是 bug,没填 key 就自动走关键词 |
| 点「转人工」提示未登录 | 浏览器 localStorage 没 token | 先用登录页登录,再打开 chat.html |
| 管理端页面空白 / 列表加载失败 | token 过期、没登录或身份不是管理员 | 用管理员账号重新登录(blogin.html)后刷新页面 |
| 调 /api/faq/*、/api/handover/list 返回 403 | 用的是普通用户 token | 换 ADMIN_TOKEN(管理员身份)再试 |
| 调 /api/faq/*、/api/handover/list 返回 401 | 没带 token 或 token 失效 | 带上有效 token header 再试 |
| 应用启动报 RabbitMQ 超时 | 公司 MQ 服务器不可达 | 不影响本次验收,忽略 |
| 新增知识后提问还是答不上 | 知识没匹配上 | 换更接近知识库原话的问法,或补充更多 FAQ |
| 数据库中文显示乱码 | 终端编码问题 | 不影响,看 id 和英文部分即可 |

---

> 验收通过后,记得把「六、验收结果汇总表」的截图或照片发到群里存档。
> 祝验收顺利!💪
