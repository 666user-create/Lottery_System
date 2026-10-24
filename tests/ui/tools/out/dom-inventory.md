# UI 页面 DOM 清单（改版后重新提取）

- 提取时间基准：本次重构勘察
- 页面数：**21**（`src/main/resources/static/*.html`）
- 选择器优先级建议：`id` > `name` > `data-*` > 稳定 class；下表 class 已过滤纯样式类

| 页面 | title | 行数 | id | name | data-* | class(过滤后) |
|---|---|---|---:|---:|---:|---:|
| `activities-list.html` | 活动列表 | 349 | 2 | 0 | 0 | 7 |
| `admin-claim.html` | 奖品发放 | 451 | 11 | 0 | 3 | 20 |
| `admin-handover.html` | 转人工工单管理 | 373 | 4 | 0 | 2 | 21 |
| `admin-login.html` | 管理员登录 | 273 | 9 | 5 | 1 | 16 |
| `admin-register.html` | 注册页面 | 223 | 8 | 5 | 0 | 2 |
| `admin.html` | 后台管理 | 347 | 11 | 0 | 2 | 18 |
| `chat.html` | 小东智能客服 | 179 | 4 | 0 | 0 | 7 |
| `create-activity.html` | 创建抽奖活动 | 494 | 10 | 2 | 0 | 12 |
| `create-prizes.html` | 创建奖品 | 264 | 6 | 3 | 0 | 12 |
| `draw.html` | 抽奖页面 | 764 | 5 | 0 | 0 | 4 |
| `faq-management.html` | FAQ 知识库管理 | 366 | 6 | 0 | 0 | 8 |
| `nav.html` | 抽奖系统 · 页面导航 | 206 | 3 | 1 | 0 | 15 |
| `operation-log.html` | 操作日志 | 427 | 7 | 0 | 0 | 13 |
| `prizes-list.html` | 奖品列表 | 315 | 2 | 0 | 0 | 12 |
| `public-winners.html` | 中奖名单 | 469 | 8 | 1 | 1 | 19 |
| `statistics.html` | 数据统计 | 422 | 5 | 0 | 0 | 13 |
| `user-list.html` | 账号列表 | 293 | 1 | 0 | 0 | 9 |
| `user-lobby.html` | 活动大厅 | 853 | 17 | 1 | 1 | 57 |
| `user-login.html` | 用户登录 | 216 | 9 | 5 | 1 | 16 |
| `user-profile.html` | 个人中心 | 636 | 38 | 3 | 1 | 26 |
| `user-register.html` | 用户注册 | 95 | 5 | 5 | 0 | 3 |

## activities-list.html
- title: `活动列表`
- 行数: 349
- **id**(2): `activities`, `pageInput`
- **脚本**(3): `./js/auth-guard.js?v=20260916`, `https://cdn.bootcdn.net/ajax/libs/jquery/3.6.0/jquery.min.js`, `./js/toastr.min.js?v=20260913`
- **可见文本**(5): `&#8592;`, `首页`, `上一页`, `下一页`, `尾页`
- class(7): `activity-list`, `page-back`, `pagination`, `op-holder`, `danger`, `activity-item`, `activity-info`
- 调用接口(3): `/activity/find-list`, `/activity/end`, `/activity/delete`

## admin-claim.html
- title: `奖品发放`
- 行数: 451
- **id**(11): `tbody`, `totalTip`, `pageInput`, `totalPages`, `' + id + '`, `expErr`, `expCompany`, `expNo`, `undoToast`, `undoText`, `undoLink`
- **data-***(3): `data-status`, `data-row`, `data-id`
- **脚本**(1): `./js/auth-guard.js?v=20260916`
- **可见文本**(14): `&#8592;`, `全部`, `未领取`, `已申请待发货`, `已发货`, `已签收`, `首页`, `上一页`, `下一页`, `尾页`, `改物流`, `保存发货`, `取消`, `撤销`
- class(20): `page-back`, `card`, `filter-bar`, `btn`, `status-btn`, `table`, `pagination-bar`, `ship`, `cell-ellipsis`, `status-tag`, `status-COMPLETED`, `status-'`, `cur`, `status-select`, `expand-box`, `expand-err`, `expand-field`, `req`, `expand-actions`, `undo-toast`
- 调用接口(1): `/api/winning/manage/status`

## admin-handover.html
- title: `转人工工单管理`
- 行数: 373
- **id**(4): `tbody`, `delErr`, `procErr`, `procRemark`
- **data-***(2): `data-status`, `data-row`
- **脚本**(1): `./js/auth-guard.js?v=20260916`
- **可见文本**(12): `&#8592;`, `全部`, `待处理`, `处理中`, `已解决`, `更多 &#8744;`, `标记为处理中`, `标记为已解决`, `删除工单`, `确定删除`, `取消`, `保存`
- class(21): `page-back`, `card`, `filter-bar`, `btn`, `status-btn`, `table`, `cell-ellipsis`, `status-tag`, `status-${sc}`, `op-cell`, `more-wrap`, `more-link`, `more-menu`, `menu-danger`, `expand-box`, `danger-box`, `expand-err`, `delete-tip`, `expand-actions`, `expand-field`, `expand-status`
- 调用接口(1): `/api/handover/process`

## admin-login.html
- title: `管理员登录`
- 行数: 273
- **id**(9): `loginForm`, `phoneNumber`, `password`, `pwdServerMsg`, `codeForm`, `loginMobile`, `verificationCode`, `getVerificationCode`, `codeServerMsg`
- **name**(5): `viewport`, `phoneNumber`, `password`, `loginMobile`, `verificationCode`
- **data-***(1): `data-form`
- **脚本**(4): `https://cdn.bootcdn.net/ajax/libs/jquery/3.6.0/jquery.min.js`, `https://cdn.bootcdn.net/ajax/libs/jquery-validate/1.19.3/jquery.validate.min.js`, `https://cdn.staticfile.org/twitter-bootstrap/4.5.2/js/bootstrap.min.js`, `./js/toastr.min.js?v=20260913`
- **可见文本**(2): `登录`, `注册管理员`
- class(16): `login-page`, `login-box`, `img-box`, `ll-slogan`, `ll-list`, `ll-item`, `dot`, `sub`, `login-container`, `tab-box`, `tab-span`, `btn`, `login-btn`, `server-msg`, `code-box`, `register-link`
- 调用接口(3): `/user/password/login`, `/user/verification-code/send`, `/user/message/login`

## admin-register.html
- title: `注册页面`
- 行数: 223
- **id**(8): `registerForm`, `name`, `mail`, `phoneNumber`, `password`, `secretGroup`, `adminSecret`, `toggleAdmin`
- **name**(5): `name`, `mail`, `phoneNumber`, `password`, `adminSecret`
- **脚本**(3): `https://cdn.bootcdn.net/ajax/libs/jquery/3.6.0/jquery.min.js`, `https://cdn.bootcdn.net/ajax/libs/jquery-validate/1.19.3/jquery.validate.min.js`, `https://cdn.staticfile.org/twitter-bootstrap/4.5.2/js/bootstrap.min.js`
- **可见文本**(2): `切换为管理员注册`, `注册`
- class(2): `register-container`, `btn`
- 调用接口(1): `/user/register`

## admin.html
- title: `后台管理`
- 行数: 347
- **id**(11): `activitiesList`, `createActivity`, `userList`, `accountCenter`, `faqManage`, `handoverManage`, `claimManage`, `aiChatEntry`, `statisticsPage`, `operationLogPage`, `contentFrame`
- **data-***(2): `data-desc`, `data-url`
- **脚本**(1): `https://cdn.bootcdn.net/ajax/libs/jquery/3.6.0/jquery.min.js`
- **可见文本**(12): `活动列表`, `新建抽奖活动`, `奖品列表`, `创建奖品`, `账号列表`, `个人中心`, `FAQ 知识库`, `转人工工单`, `奖品发放`, `AI 客服对话`, `活动数据统计`, `操作日志`
- class(18): `admin-page`, `header-box`, `logo-box`, `user-box`, `gray`, `cont-box`, `sidebar`, `nav`, `huodong`, `white-img`, `gray-img`, `sub-category`, `jiangpin`, `renyuan`, `kefu`, `tongji`, `main-content`, `iframe-styles`

## chat.html
- title: `小东智能客服`
- 行数: 179
- **id**(4): `chatBody`, `input`, `sendBtn`, `handoverBtn`
- **可见文本**(3): `&#8592;`, `发送`, `转人工`
- class(7): `chat-box`, `chat-header`, `page-back`, `chat-body`, `msg`, `bubble`, `chat-footer`
- 调用接口(2): `/api/ai/chat`, `/api/handover/create`

## create-activity.html
- title: `创建抽奖活动`
- 行数: 494
- **id**(10): `activityForm`, `activityName`, `description`, `buttonPrizes`, `buttonUsers`, `createActivity`, `prizesModal`, `prizesContainer`, `usersModal`, `usersContainer`
- **name**(2): `activityName`, `description`
- **脚本**(4): `./js/auth-guard.js?v=20260916`, `https://cdn.bootcdn.net/ajax/libs/jquery/3.6.0/jquery.min.js`, `https://cdn.bootcdn.net/ajax/libs/jquery-validate/1.19.3/jquery.validate.min.js`, `./js/toastr.min.js?v=20260913`
- **可见文本**(6): `&#8592;`, `圈选奖品`, `圈选人员`, `创建活动`, `取消`, `确定`
- class(12): `page-back`, `desc-row`, `btn`, `pre-btn`, `toast`, `modal`, `close`, `prize-item`, `custom-p`, `user-tag-admin`, `user-item`, `user-empty`
- 调用接口(3): `/prize/find-list`, `/user/base-user/find-list`, `/activity/create`

## create-prizes.html
- title: `创建奖品`
- 行数: 264
- **id**(6): `prizeRows`, `prizeName`, `previewImg`, `prizeImageUrl`, `price`, `description`
- **name**(3): `prizeName`, `price`, `description`
- **脚本**(2): `./js/auth-guard.js?v=20260916`, `https://cdn.bootcdn.net/ajax/libs/jquery/3.6.0/jquery.min.js`
- **可见文本**(2): `&#8592;`, `创建奖品`
- class(12): `title-row`, `page-back`, `prize-row`, `prize-name`, `img-upload`, `upload-box`, `plus`, `up-txt`, `preview-img`, `prize-price`, `prize-description`, `btn`
- 调用接口(1): `/prize/create`

## draw.html
- title: `抽奖页面`
- 行数: 764
- **id**(5): `container`, `title`, `disc`, `image`, `list`
- **脚本**(2): `./js/auth-guard.js?v=20260916`, `https://cdn.bootcdn.net/ajax/libs/jquery/3.6.0/jquery.min.js`
- class(4): `opt-box`, `btn`, `pre-btn`, `next-btn`
- 调用接口(3): `/draw/winning-records/show`, `/draw/prize`, `/activity/detail/find`

## faq-management.html
- title: `FAQ 知识库管理`
- 行数: 366
- **id**(6): `question`, `category`, `answer`, `faqTableBody`, `totalTip`, `pageInput`
- **脚本**(2): `./js/auth-guard.js?v=20260916`, `https://cdn.bootcdn.net/ajax/libs/jquery/3.6.0/jquery.min.js`
- **可见文本**(8): `&#8592;`, `新增`, `清空`, `首页`, `上一页`, `下一页`, `尾页`, `删除`
- class(8): `page-back`, `panel`, `field`, `grow`, `fixed`, `empty-tip`, `pagination-bar`, `left`
- 调用接口(3): `/api/faq/list`, `/api/faq/add`, `/api/faq/delete`

## nav.html
- title: `抽奖系统 · 页面导航`
- 行数: 206
- **id**(3): `loginState`, `now`, `q`
- **name**(1): `viewport`
- **可见文本**(1): `清除登录态`
- class(15): `meta`, `chip`, `off`, `clear`, `filter`, `hint`, `b-pub`, `b-login`, `b-admin`, `grid`, `card`, `name`, `path`, `note`, `api`

## operation-log.html
- title: `操作日志`
- 行数: 427
- **id**(7): `moduleSelect`, `identitySelect`, `startTime`, `endTime`, `logTableBody`, `totalTip`, `pageInput`
- **脚本**(2): `./js/auth-guard.js?v=20260916`, `https://cdn.bootcdn.net/ajax/libs/jquery/3.6.0/jquery.min.js`
- **可见文本**(7): `&#8592;`, `查询`, `重置`, `首页`, `上一页`, `下一页`, `尾页`
- class(13): `page-back`, `panel`, `filter-bar`, `field`, `empty-tip`, `pagination-bar`, `tag`, `tag-ok`, `tag-fail`, `msg`, `tag-warn`, `left`, `path`
- 调用接口(1): `/api/operation-log/list`

## prizes-list.html
- title: `奖品列表`
- 行数: 315
- **id**(2): `prizes`, `pageInput`
- **脚本**(3): `./js/auth-guard.js?v=20260916`, `https://cdn.bootcdn.net/ajax/libs/jquery/3.6.0/jquery.min.js`, `./js/toastr.min.js?v=20260913`
- **可见文本**(5): `&#8592;`, `首页`, `上一页`, `下一页`, `尾页`
- class(12): `prize-list`, `page-back`, `pagination`, `prize-empty`, `prize-info`, `prize-thumb`, `prize-text`, `prize-meta`, `prize-ops`, `prize-price`, `danger`, `prize-item`
- 调用接口(2): `/prize/find-list`, `/prize/delete`

## public-winners.html
- title: `中奖名单`
- 行数: 469
- **id**(8): `split`, `actCount`, `actBtns`, `sideHint`, `listTitle`, `listCount`, `listBody`, `drawLink`
- **name**(1): `viewport`
- **data-***(1): `data-id`
- **脚本**(2): `./js/auth-guard.js?v=20260916`, `https://cdn.bootcdn.net/ajax/libs/jquery/3.6.0/jquery.min.js`
- **可见文本**(2): `返回活动大厅`, `查看结果海报 →`
- class(19): `header`, `btn`, `page-intro`, `split`, `picker`, `picker-title`, `picker-count`, `act-list`, `picker-tip`, `side-hint`, `empty`, `draw-link`, `act-btn`, `winner-table`, `time-cell`, `name-cell`, `rank-cell`, `rankClass(r.prizeTier)`, `prize-cell`
- 调用接口(2): `/activity/find-list`, `/draw/winning-records/show`

## statistics.html
- title: `数据统计`
- 行数: 422
- **id**(5): `overviewCards`, `tierTableBody`, `activitySelect`, `activitySummary`, `prizeStatTableBody`
- **脚本**(2): `./js/auth-guard.js?v=20260916`, `https://cdn.bootcdn.net/ajax/libs/jquery/3.6.0/jquery.min.js`
- **可见文本**(1): `&#8592;`
- class(13): `page-back`, `stat-card`, `label`, `value`, `panel`, `empty-tip`, `activity-bar`, `progress`, `progress-bar`, `pct`, `unit`, `summary-metrics`, `item`
- 调用接口(3): `/api/stat/overview`, `/activity/find-list`, `/api/stat/activity`

## user-list.html
- title: `账号列表`
- 行数: 293
- **id**(1): `userList`
- **脚本**(2): `./js/auth-guard.js?v=20260916`, `https://cdn.bootcdn.net/ajax/libs/jquery/3.6.0/jquery.min.js`
- **可见文本**(2): `&#8592;`, `+ 注册用户`
- class(9): `user-table`, `page-back`, `table`, `status-badge`, `(disabled`, `'disabled'`, `'active')`, `danger')`, `self-tip`
- 调用接口(3): `/user/me`, `/user/status/update`, `/user/base-user/find-list`

## user-lobby.html
- title: `活动大厅`
- 行数: 853
- **id**(17): `userName`, `statRunning`, `statEnded`, `statMyWins`, `actSection`, `actTitle`, `actFilterNote`, `activityList`, `actPager`, `qeProfileDesc`, `xiaoDongFab`, `xiaoDongChat`, `chatMessages`, `chatInput`, `activityDetailModal`, `modalTitle`, `modalBody`
- **name**(1): `viewport`
- **data-***(1): `data-dismiss`
- **脚本**(3): `./js/auth-guard.js?v=20260916`, `https://cdn.bootcdn.net/ajax/libs/jquery/3.6.0/jquery.min.js`, `https://cdn.staticfile.org/twitter-bootstrap/4.5.2/js/bootstrap.min.js`
- **可见文本**(4): `退出登录`, `完整对话页 →`, `&times;`, `去个人中心补全 →`
- class(57): `hero`, `hero-left`, `hero-title`, `hero-sub`, `hero-user`, `who`, `btn`, `stats`, `stat-card`, `stat-1`, `clickable`, `bar`, `stat-2`, `stat-3`, `sec`, `sec-head`, `sec-title`, `sec-note`, `act-grid`, `load-tip`, `act-pager`, `quick-entry`, `qe-card`, `qe-winners`, `qe-body`, `qe-t`, `qe-d`, `qe-arrow`, `qe-profile`, `qe-chat`, `fab-btn`, `fab-icon`, `chat-header`, `close-btn`, `chat-messages`, `msg`, `bot`, `chat-input`, `modal`, `close` …
- 调用接口(8): `/user/me`, `/activity/find-list`, `/user/receiver-info`, `/api/winning/claim`, `/api/winning/confirm`, `/activity/detail/find`, `/draw/my-records`, `/api/ai/chat`

## user-login.html
- title: `用户登录`
- 行数: 216
- **id**(9): `loginForm`, `phoneNumber`, `password`, `pwdServerMsg`, `codeForm`, `loginMobile`, `verificationCode`, `getVerificationCode`, `codeServerMsg`
- **name**(5): `viewport`, `phoneNumber`, `password`, `loginMobile`, `verificationCode`
- **data-***(1): `data-form`
- **脚本**(4): `https://cdn.bootcdn.net/ajax/libs/jquery/3.6.0/jquery.min.js`, `https://cdn.bootcdn.net/ajax/libs/jquery-validate/1.19.3/jquery.validate.min.js`, `https://cdn.staticfile.org/twitter-bootstrap/4.5.2/js/bootstrap.min.js`, `./js/toastr.min.js?v=20260913`
- **可见文本**(2): `登录`, `注册用户`
- class(16): `login-page`, `login-box`, `img-box`, `ll-slogan`, `ll-list`, `ll-item`, `dot`, `sub`, `login-container`, `tab-box`, `tab-span`, `btn`, `login-btn`, `server-msg`, `code-box`, `register-link`
- 调用接口(3): `/user/password/login`, `/user/verification-code/send`, `/user/message/login`

## user-profile.html
- title: `个人中心`
- 行数: 636
- **id**(38): `userName`, `avatar`, `nameText`, `vUserName`, `vUserId`, `vIdentity`, `receiverName`, `receiverPhone`, `receiverAddress`, `receiverErr`, `receiverSubmit`, `emailModal`, `curEmail`, `newEmail`, `emailErr`, `emailPwdPane`, `emailOldPwd`, `emailSecretPane`, `emailSecret`, `emailCodePane`, `emailCode`, `emailSendCode`, `emailCodeHint`, `emailFormErr`, `emailSubmit`, `pwdModal`, `pwdOldPane`, `pwdOldPwd`, `pwdSecretPane`, `pwdSecret`, `pwdCodePane`, `pwdCode`, `pwdSendCode`, `pwdCodeHint`, `newPwd`, `newPwd2`, `pwdFormErr`, `pwdSubmit`
- **name**(3): `viewport`, `emailVerify`, `pwdVerify`
- **data-***(1): `data-dismiss`
- **脚本**(4): `./js/auth-guard.js?v=20260916`, `https://cdn.bootcdn.net/ajax/libs/jquery/3.6.0/jquery.min.js`, `https://cdn.staticfile.org/twitter-bootstrap/4.5.2/js/bootstrap.min.js`, `https://cdn.staticfile.org/toastr.js/2.1.4/toastr.min.js`
- **可见文本**(7): `&#8592;`, `退出登录`, `修改`, `保存收货地址`, `获取验证码`, `取消`, `确认修改`
- class(26): `header`, `back-arrow`, `user-flow-only`, `user-info`, `btn`, `profile-card`, `profile-top`, `avatar`, `profile-meta`, `name`, `sub`, `info-row`, `label`, `value`, `sec-title`, `action-item`, `req`, `hint`, `modal`, `close`, `field-error`, `verify-box`, `verify-opt`, `admin-only`, `verify-pane`, `code-row`
- 调用接口(6): `/user/me`, `/user/account/security`, `/user/security/verification-code/send`, `/user/email/update`, `/user/password/update`, `/user/receiver-info`

## user-register.html
- title: `用户注册`
- 行数: 95
- **id**(5): `registerForm`, `name`, `mail`, `phoneNumber`, `password`
- **name**(5): `viewport`, `name`, `mail`, `phoneNumber`, `password`
- **脚本**(2): `https://cdn.bootcdn.net/ajax/libs/jquery/3.6.0/jquery.min.js`, `https://cdn.bootcdn.net/ajax/libs/jquery-validate/1.19.3/jquery.validate.min.js`
- **可见文本**(2): `注册`, `去登录`
- class(3): `register-container`, `btn`, `back-link`
- 调用接口(1): `/user/register`
