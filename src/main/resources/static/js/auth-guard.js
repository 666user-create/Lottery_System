/*
 * auth-guard.js —— 统一处理「授权失效（401/403）」，两种返回形态都要判
 *
 * 背景（BUG-012，2026-09-16 修复）
 *   本项目后端有两种失败返回形态，必须**同时**覆盖，否则必然漏判：
 *
 *   形态 A：业务失败 → **HTTP 200 + body.code**（如 106 邮箱已注册、403 需要管理员权限…）
 *           —— 只会进 jQuery 的 success 回调
 *   形态 B：拦截器拒绝（LoginInterceptor 直接 writeJson）→ **真正的 HTTP 401 / 403**
 *           —— 只会进 jQuery 的 error 回调
 *
 *   而各页面原先把「登录失效跳登录页」写在 error 回调里判 `err.status==401`：
 *   对形态 A 永不触发（死代码）；同时 success 分支只弹了一句「XX失败：undefined」。
 *   结果：Token 过期后页面不跳登录页；非管理员打开管理端只看到莫名报错。
 *
 * 用法：
 *     success: function (result) {
 *         if (window.AuthGuard && AuthGuard.handle(result, '查询用户列表')) return;
 *         ...
 *     },
 *     error: function (xhr) {
 *         if (window.AuthGuard && AuthGuard.handle(xhr, '查询用户列表')) return;
 *         ...
 *     }
 *   handle() 返回 true 表示「已处理授权失效」，调用方直接 return。
 *   传入 result（形态 A）或 xhr（形态 B）都可以，函数自己识别。
 *
 * 登录页配置（2026-10-10 修复）：
 *   原先 LOGIN_PAGE 硬编码为管理端登录页 /admin-login.html，导致用户端页面
 *   （user-lobby.html 等）token 失效后被跳到管理端登录页。
 *   现在按以下顺序取值，页面可在自己脚本里随时设置：
 *     window.AUTH_GUARD_LOGIN_PAGE  >  <body data-login-page="...">  >  '/admin-login.html'
 *   管理端页面不设置，行为与原来完全一致。
 */
(function (global) {
    'use strict';

    var DEFAULT_LOGIN_PAGE = '/admin-login.html';   // 管理端登录页，作为缺省值

    /** 解析该跳转到哪个登录页（延迟求值，页面可在任意时机覆盖） */
    function loginPage() {
        try {
            if (global.AUTH_GUARD_LOGIN_PAGE) {
                return global.AUTH_GUARD_LOGIN_PAGE;
            }
            var body = global.document && global.document.body;
            if (body) {
                var attr = body.getAttribute('data-login-page');
                if (attr) {
                    return attr;
                }
            }
        } catch (e) {
            // 忽略，回退到缺省值
        }
        return DEFAULT_LOGIN_PAGE;
    }

    /** 从 result（响应体）或 xhr（jQuery jqXHR）里取出业务码 */
    function extractCode(arg) {
        if (!arg) {
            return null;
        }
        // 形态 B：jQuery 的 xhr 对象
        if (typeof arg.status === 'number') {
            var body = arg.responseJSON;
            if (body && typeof body.code !== 'undefined') {
                return Number(body.code);
            }
            return arg.status;              // 真正的 HTTP 状态码
        }
        // 形态 A：后端响应体
        if (typeof arg.code !== 'undefined') {
            return Number(arg.code);
        }
        return null;
    }

    function extractMessage(arg) {
        if (arg && typeof arg === 'object') {
            if (arg.message) {
                return arg.message;
            }
            if (arg.responseJSON && arg.responseJSON.message) {
                return arg.responseJSON.message;
            }
        }
        return '';
    }

    /** 在页面上留下持久痕迹，便于自动化测试与线上排查（弹窗会被 accept 掉，DOM 痕迹不会） */
    function mark(code, what, message) {
        try {
            var b = global.document && global.document.body;
            if (b) {
                b.setAttribute('data-auth-guard', String(code));
                b.setAttribute('data-auth-guard-action', what || '');
                b.setAttribute('data-auth-guard-message', message || '');
            }
            global.__AUTH_GUARD__ = { code: code, action: what || '', message: message || '' };
        } catch (e) {
            // 忽略
        }
    }

    /*
     * 「跳登录页」只允许触发一次。
     *
     * 页面初始化常并发多个请求（列表 + 用户信息 + 统计…），token 失效时它们会
     * **同时**返回 401。若不加这层幂等，每个请求都会弹一次 alert、
     * 都跳一次登录页 —— 用户看到一连串"登录状态已失效"弹窗，还要挨个点掉，
     * 才终于回到登录页。实测越权/过期进入页面正是这个现象。
     *
     * 首个触发者负责提示与跳转，后续调用直接返回，什么都不做。
     * flag 只存在于本次页面生命周期内，登录页是新文档，无需重置。
     */
    var navigating = false;

    function toLogin(what, message) {
        mark(401, what, message);
        if (navigating) {
            return;
        }
        navigating = true;
        alert('登录状态已失效' + (what ? ('，' + what + '已中止') : '') + '，即将跳转到登录页！');
        // 只有一个动作：让「当前窗口」跳走。
        // 被嵌在 admin.html 的 iframe 里时，必须跳父窗口 —— 否则外壳还留在原地，
        // 内容区却已跳走，留下"外壳还在、内容已跳"的错乱状态。
        var target = (global.parent && global.parent !== global) ? global.parent : global;
        target.location.href = loginPage();
        // 不用再单独给 iframe 自己赋 location.href：
        // 父窗口跳转后 iframe 随之卸载，这里再跳一次会与父窗口抢路由，
        // 在多层嵌套或带 iframe 的登录页上偶发跳错目标。
    }

    /*
     * 403 同样只提示一次。
     * 越权进入管理端页面时，页内多个并发请求会同时拿到 403，
     * 不做幂等就是一串重复的"权限不足"弹窗，需要挨个点掉。
     * 第一次的提示已经说清了，后续直接静默返回。
     * 403 不跳转（用户身份有效，只是权限不够），因此不占用 navigating 标志 ——
     * 那会连带把后续真正的 401 登录跳转也吞掉。
     */
    var permissionDeniedNotified = false;

    function handle(arg, what) {
        var code = extractCode(arg);
        if (code === null) {
            return false;                  // 非标准响应，交回调用方
        }
        var action = what ? ('「' + what + '」') : '';
        var message = extractMessage(arg);

        if (code === 401) {
            toLogin(action, message);
            return true;
        }

        if (code === 403) {
            // 403 不跳转（用户已登录，只是权限不够），给一句能看懂的提示
            mark(403, action, message);
            if (permissionDeniedNotified) {
                return true;
            }
            permissionDeniedNotified = true;
            alert('权限不足' + action + '：' + (message || '需要管理员权限'));
            return true;
        }

        return false;
    }

    global.AuthGuard = { handle: handle, codeOf: extractCode };
})(window);
