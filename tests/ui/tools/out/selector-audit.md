# UI 用例选择器存活审计

> 由 `tests/ui/tools/selector_audit.py` 生成。
> 判定方式：把每个定位表达式拿去匹配 `src/main/resources/static` 下全部 `.html` + `.js` 的文本（含内联脚本动态拼出的 DOM）。
> **静态未命中 = 一定失效，必须重写**；静态命中 ≠ 运行时一定能找到（运行时还受接口数据、iframe、渲染时序影响）。

## 总览

| 用例文件 | 定位表达式 | 静态失效 | 引用页面 | iframe |
|---|---|---|---|---|
| `ActivityUiTest.java` | 7 | **0** | 2 | 0 |
| `AdminPageUiTest.java` | 13 | **1** | 4 | 0 |
| `AiChatUiTest.java` | 5 | **0** | 1 | 0 |
| `BaseUiTest.java` | 4 | **0** | 1 | 0 |
| `HandoverUiTest.java` | 6 | **0** | 1 | 0 |
| `LoginFlowUiTest.java` | 3 | **0** | 1 | 0 |
| `LoginPageUiTest.java` | 4 | **0** | 5 | 0 |
| `LogoutPermissionUiTest.java` | 3 | **0** | 2 | 0 |
| `PrizeUiTest.java` | 9 | **1** | 2 | 0 |
| `RegisterFlowUiTest.java` | 5 | **0** | 1 | 0 |
| `RegisterLoginFlowUiTest.java` | 1 | **0** | 2 | 0 |
| `WinningRecordsUiTest.java` | 6 | **1** | 1 | 0 |
| **合计** | **66** | **3** | | |

## 失效明细（必须重写）

### AdminPageUiTest.java — 1 个

| 方式 | 表达式 | 说明 |
|---|---|---|
| `id` | `register` | 静态语料中找不到 |

### PrizeUiTest.java — 1 个

| 方式 | 表达式 | 说明 |
|---|---|---|
| `id` | `nextPage` | 静态语料中找不到 |

### WinningRecordsUiTest.java — 1 个

| 方式 | 表达式 | 说明 |
|---|---|---|
| `xpath` | `//*[contains(text(),'复制链接')]` | 静态语料中找不到 |

## 命中明细（可参考的现有实现位置）

### ActivityUiTest.java — 7/7 命中

- 引用页面：`/activities-list.html`、`/create-activity.html`

| 方式 | 表达式 | 命中文件 |
|---|---|---|
| `id` | `activityName` | `create-activity.html` |
| `id` | `description` | `create-activity.html`, `create-prizes.html` |
| `id` | `buttonPrizes` | `create-activity.html` |
| `id` | `buttonUsers` | `create-activity.html` |
| `id` | `createActivity` | `admin.html`, `create-activity.html` |
| `cssSelector` | `#activities .activity-item` | `activities-list.html` |
| `cssSelector` | `#activities .activity-item a` | `activities-list.html`, `admin-claim.html`, `admin-handover.html`, `admin-login.html` …(+15) |

### AdminPageUiTest.java — 12/13 命中

- 引用页面：`/activities-list.html`、`/admin-register.html`、`/admin.html`、`/user-list.html`

| 方式 | 表达式 | 命中文件 |
|---|---|---|
| `id` | `activitiesList` | `admin.html`, `create-activity.html` |
| `id` | `createActivity` | `admin.html`, `create-activity.html` |
| `id` | `userList` | `admin-register.html`, `admin.html`, `user-list.html` |
| `id` | `contentFrame` | `admin.html` |
| `cssSelector` | `.sidebar` | `admin.html` |
| `cssSelector` | `#userList tr` | `admin-claim.html`, `admin-handover.html`, `admin-register.html`, `admin.html` …(+6) |
| `id` | `registerForm` | `admin-register.html`, `user-register.html` |
| `id` | `name` | `admin-register.html`, `user-register.html` |
| `id` | `mail` | `admin-register.html`, `user-register.html` |
| `id` | `phoneNumber` | `admin-login.html`, `admin-register.html`, `user-login.html`, `user-register.html` |
| `id` | `password` | `admin-login.html`, `admin-register.html`, `user-login.html`, `user-register.html` |
| `id` | `toggleAdmin` | `admin-register.html` |

### AiChatUiTest.java — 5/5 命中

- 引用页面：`/chat.html`

| 方式 | 表达式 | 命中文件 |
|---|---|---|
| `id` | `input` | `chat.html` |
| `id` | `sendBtn` | `chat.html` |
| `id` | `handoverBtn` | `chat.html` |
| `cssSelector` | `.msg.ai .bubble` | `admin-login.html`, `chat.html`, `operation-log.html`, `user-lobby.html` …(+1) |
| `cssSelector` | `.msg.ai` | `admin-login.html`, `chat.html`, `operation-log.html`, `user-lobby.html` …(+1) |

### BaseUiTest.java — 4/4 命中

- 引用页面：`/user-login.html`

| 方式 | 表达式 | 命中文件 |
|---|---|---|
| `cssSelector` | `[name='phoneNumber']` | `admin-login.html`, `admin-register.html`, `user-login.html`, `user-register.html` |
| `cssSelector` | `[name='password']` | `admin-login.html`, `admin-register.html`, `user-login.html`, `user-register.html` |
| `cssSelector` | `button[type='submit']` | `activities-list.html`, `admin-claim.html`, `admin-handover.html`, `admin-login.html` …(+15) |
| `tagName` | `body` | `activities-list.html`, `admin-claim.html`, `admin-handover.html`, `admin-login.html` …(+18) |

### HandoverUiTest.java — 6/6 命中

- 引用页面：`/admin-handover.html`

| 方式 | 表达式 | 命中文件 |
|---|---|---|
| `cssSelector` | `#tbody tr` | `admin-claim.html`, `admin-handover.html`, `faq-management.html`, `nav.html` …(+4) |
| `cssSelector` | `.status-btn[data-status='PENDING']` | `admin-claim.html`, `admin-handover.html` |
| `xpath` | `//button[contains(text(),'待处理')]` | `activities-list.html`, `admin-claim.html`, `admin-handover.html`, `admin-login.html` …(+15) |
| `xpath` | `//tbody//button[contains(text(),'处理中')]` | `activities-list.html`, `admin-claim.html`, `admin-handover.html`, `admin-login.html` …(+16) |
| `xpath` | `//tbody//button[contains(text(),'已解决')]` | `activities-list.html`, `admin-claim.html`, `admin-handover.html`, `admin-login.html` …(+16) |
| `cssSelector` | `.status-btn[data-status='RESOLVED']` | `admin-claim.html`, `admin-handover.html` |

### LoginFlowUiTest.java — 3/3 命中

- 引用页面：`/admin-login.html`

| 方式 | 表达式 | 命中文件 |
|---|---|---|
| `cssSelector` | `[name='phoneNumber']` | `admin-login.html`, `admin-register.html`, `user-login.html`, `user-register.html` |
| `cssSelector` | `[name='password']` | `admin-login.html`, `admin-register.html`, `user-login.html`, `user-register.html` |
| `cssSelector` | `button[type='submit']` | `activities-list.html`, `admin-claim.html`, `admin-handover.html`, `admin-login.html` …(+15) |

### LoginPageUiTest.java — 4/4 命中

- 引用页面：`/admin-handover.html`、`/chat.html`、`/user-lobby.html`、`/user-login.html`、`/user-register.html`

| 方式 | 表达式 | 命中文件 |
|---|---|---|
| `tagName` | `body` | `activities-list.html`, `admin-claim.html`, `admin-handover.html`, `admin-login.html` …(+18) |
| `id` | `input` | `chat.html` |
| `id` | `sendBtn` | `chat.html` |
| `id` | `handoverBtn` | `chat.html` |

### LogoutPermissionUiTest.java — 3/3 命中

- 引用页面：`/admin-handover.html`、`/user-lobby.html`

| 方式 | 表达式 | 命中文件 |
|---|---|---|
| `xpath` | `//button[contains(text(),'退出')]` | `activities-list.html`, `admin-claim.html`, `admin-handover.html`, `admin-login.html` …(+16) |
| `cssSelector` | `.btn-outline-danger` | `user-profile.html` |
| `cssSelector` | `#tbody tr` | `admin-claim.html`, `admin-handover.html`, `faq-management.html`, `nav.html` …(+4) |

### PrizeUiTest.java — 8/9 命中

- 引用页面：`/create-prizes.html`、`/prizes-list.html`

| 方式 | 表达式 | 命中文件 |
|---|---|---|
| `cssSelector` | `tbody tr` | `admin-claim.html`, `admin-handover.html`, `faq-management.html`, `nav.html` …(+4) |
| `cssSelector` | `[name='prizeName']` | `create-prizes.html` |
| `id` | `prizeName` | `create-prizes.html` |
| `id` | `price` | `create-prizes.html` |
| `cssSelector` | `[name='price']` | `create-prizes.html` |
| `cssSelector` | `input[type='file']` | `activities-list.html`, `admin-claim.html`, `admin-handover.html`, `admin-login.html` …(+12) |
| `cssSelector` | `.next` | `draw.html` |
| `xpath` | `//button[contains(text(),'下一页')]` | `activities-list.html`, `admin-claim.html`, `admin-handover.html`, `admin-login.html` …(+15) |

### RegisterFlowUiTest.java — 5/5 命中

- 引用页面：`/user-register.html`

| 方式 | 表达式 | 命中文件 |
|---|---|---|
| `cssSelector` | `[name='name']` | `admin-register.html`, `user-register.html` |
| `cssSelector` | `[name='mail']` | `admin-register.html`, `user-register.html` |
| `cssSelector` | `[name='phoneNumber']` | `admin-login.html`, `admin-register.html`, `user-login.html`, `user-register.html` |
| `cssSelector` | `[name='password']` | `admin-login.html`, `admin-register.html`, `user-login.html`, `user-register.html` |
| `cssSelector` | `button[type='submit']` | `activities-list.html`, `admin-claim.html`, `admin-handover.html`, `admin-login.html` …(+15) |

### RegisterLoginFlowUiTest.java — 1/1 命中

- 引用页面：`/user-login.html`、`/user-register.html`

| 方式 | 表达式 | 命中文件 |
|---|---|---|
| `cssSelector` | `button[type='submit']` | `activities-list.html`, `admin-claim.html`, `admin-handover.html`, `admin-login.html` …(+15) |

### WinningRecordsUiTest.java — 5/6 命中

- 引用页面：`/draw.html`

| 方式 | 表达式 | 命中文件 |
|---|---|---|
| `id` | `container` | `draw.html` |
| `id` | `list` | `draw.html` |
| `xpath` | `//button[contains(text(),'中奖')]` | `activities-list.html`, `admin-claim.html`, `admin-handover.html`, `admin-login.html` …(+17) |
| `xpath` | `//*[contains(text(),'中奖名单')]` | `activities-list.html`, `draw.html`, `nav.html`, `public-winners.html` …(+1) |
| `xpath` | `//button[contains(text(),'分享')]` | `activities-list.html`, `admin-claim.html`, `admin-handover.html`, `admin-login.html` …(+16) |
