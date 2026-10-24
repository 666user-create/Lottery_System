package com.qa.ui;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UI 用例辅助：通过接口准备数据与登录态，避免 UI 用例之间互相污染。
 *
 * 设计取舍：
 *   - 登录态：走接口登录取 token → 注入 localStorage（快且稳定）；"登录流程"本身仍用 UI 走完整流程验证。
 *   - 数据准备：工单等前置数据用接口创建，保证 UI 用例可重复执行。
 *   - 不引入额外依赖：使用 JDK 11+ 的 java.net.http.HttpClient，JSON 用轻量正则提取。
 */
public final class ApiSupport {

    public static final String BASE = "http://localhost:8080";
    /**
     * 管理员注册口令：<b>不写入仓库</b>。真值在项目根
     * {@code src/main/resources/application-local.properties}（该文件 .gitignore 已忽略）。
     *
     * <p>解析顺序：环境变量 {@code ADMIN_SECRET} → 系统属性 {@code -Dadmin.secret}
     * → 本机私有配置文件。都没配时为空，管理员注册用例会失败——这是预期行为：
     * 与其把口令提交进 git，不如让缺配置显式暴露。
     */
    private static final String ADMIN_SECRET = resolveAdminSecret();
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private ApiSupport() {
    }

    private static String resolveAdminSecret() {
        String env = System.getenv("ADMIN_SECRET");
        if (env != null && !env.isBlank()) {
            return env;
        }
        String prop = System.getProperty("admin.secret");
        if (prop != null && !prop.isBlank()) {
            return prop;
        }
        // mvn 在 tests/ui 下执行时 cwd=tests/ui；IDEA 里通常 cwd=项目根，故两个相对路径都试
        Path local = Paths.get("src", "main", "resources", "application-local.properties");
        if (!Files.isRegularFile(local)) {
            local = Paths.get("..", "..", "src", "main", "resources", "application-local.properties");
        }
        try {
            if (Files.isRegularFile(local)) {
                for (String line : Files.readAllLines(local, StandardCharsets.UTF_8)) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("ADMIN_SECRET=")) {
                        return trimmed.substring("ADMIN_SECRET=".length()).trim();
                    }
                }
            }
        } catch (Exception ignored) {
            // 读不到就当未配置，由用例失败显式暴露
        }
        return "";
    }

    // ==================== HTTP ====================

    public static String post(String path, String json, String authHeaderName, String authHeaderValue) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(BASE + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json");
            if (authHeaderName != null) {
                b.header(authHeaderName, authHeaderValue);
            }
            b.POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
            return CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
        } catch (Exception e) {
            throw new RuntimeException("POST " + path + " 失败: " + e.getMessage(), e);
        }
    }

    public static String get(String path, String token) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(BASE + path))
                    .timeout(Duration.ofSeconds(20));
            if (token != null) {
                b.header("token", token);
            }
            b.GET();
            return CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
        } catch (Exception e) {
            throw new RuntimeException("GET " + path + " 失败: " + e.getMessage(), e);
        }
    }

    /** 轻量 JSON 提取：取 "key":"value" 或 "key":123 */
    public static String extract(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"?([^\",}]+)\"?").matcher(json);
        return m.find() ? m.group(1).trim() : null;
    }

    // ==================== 账号与登录态 ====================

    /** 幂等确保账号存在（已存在则忽略 106/107）
     *
     *  带重试：2026-09-13 实测发现应用启动初期存在短暂不稳定窗口
     *  （同一账号首次注册返回 500「系统异常」，稍后重试即 200），
     *  故此处对首次失败做最多 4 次重试，避免把环境抖动误判为用例失败。
     */
    public static void ensureAccount(String mail, String phone, String identity, String password) {
        String body = String.format(
                "{\"name\":\"QA-UI\",\"mail\":\"%s\",\"phoneNumber\":\"%s\",\"password\":\"%s\",\"identity\":\"%s\"}",
                mail, phone, password, identity);
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 4; attempt++) {
            try {
                String resp = "ADMIN".equals(identity)
                        ? post("/user/register", body, "X-Admin-Secret", ADMIN_SECRET)
                        : post("/user/register", body, null, null);
                String code = extract(resp, "code");
                if ("200".equals(code) || "106".equals(code) || "107".equals(code)) {
                    return;
                }
                last = new RuntimeException("准备账号失败: " + resp);
            } catch (RuntimeException e) {
                last = e;
            }
            sleep(2000);
        }
        throw last;
    }

    /**
     * 应用就绪探测：不仅等 HTTP 端口可用，还等**数据源可用**。
     *
     * <p>背景（2026-09-15 实测）：仅探测静态页会在启动后约 0~15 秒内"假就绪"——
     * 此时 Tomcat 已监听 8080，但 MyBatis 首次取连接失败：
     * <pre>
     * org.mybatis.spring.MyBatisSystemException: null
     *     at ...UserServiceImpl.checkMailUsed(UserServiceImpl.java:229)
     * → CommonResult{code=500, message="系统异常"}
     * </pre>
     * 表现是用例报「准备账号失败: code=500」的伪失败。
     * 现改为轮询一个**必过数据库**的只读接口（{@code /user/base-user/find-list}），
     * 直到它返回业务码 401（未登录）——说明「路由 → MyBatis → DB → 全局异常处理」整链路已就绪。
     */
    public static void waitForAppReady() {
        long deadline = System.currentTimeMillis() + 90_000;
        String last = "(未探测)";
        while (System.currentTimeMillis() < deadline) {
            try {
                String html = get("/user-login.html", null);
                if (html != null && html.length() > 200) {
                    // HTTP 已就绪，进一步确认数据源可用
                    String resp = get("/user/base-user/find-list", "qa_auto_readiness_probe");
                    String code = extract(resp, "code");
                    if ("401".equals(code) || "200".equals(code) || "403".equals(code)) {
                        return; // 数据源可用
                    }
                    last = resp == null ? "null" : resp.substring(0, Math.min(120, resp.length()));
                }
            } catch (Exception e) {
                last = e.getMessage();
            }
            sleep(1000);
        }
        throw new RuntimeException("应用/数据源未在 90 秒内就绪: " + BASE + " 最近响应=" + last);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    /** 接口密码登录取 token */
    public static String login(String mail, String password, String identity) {
        String resp = post("/user/password/login", String.format(
                "{\"loginName\":\"%s\",\"password\":\"%s\",\"mandatoryIdentity\":\"%s\"}",
                mail, password, identity), null, null);
        String token = extract(resp, "token");
        if (token == null || token.length() < 50) {
            throw new RuntimeException("登录取 token 失败: " + resp);
        }
        return token;
    }

    public static String normalUserToken() {
        ensureAccount("qa_auto_uiuser@test.com", "13800338001", "NORMAL", "123456");
        return login("qa_auto_uiuser@test.com", "123456", "NORMAL");
    }

    public static String adminToken() {
        ensureAccount("qa_auto_uiadmin@test.com", "13900339001", "ADMIN", "123456");
        return login("qa_auto_uiadmin@test.com", "123456", "ADMIN");
    }

    // ==================== 数据准备 ====================

    /** 创建一个转人工工单，返回工单 id（用于工单管理页 UI 用例） */
    public static String createHandover(String userToken) {
        String sid = "qa_auto_ui_" + UUID.randomUUID().toString().substring(0, 8);
        String resp = post("/api/handover/create",
                String.format("{\"sessionId\":\"%s\",\"question\":\"QA-UI 自动化用例前置工单\"}", sid),
                "token", userToken);
        String id = extract(resp, "data");
        if (id == null) {
            throw new RuntimeException("创建工单失败: " + resp);
        }
        return id;
    }

    // ==================== 中奖记录相关数据探测 ====================

    /**
     * 找出库中「确实含有中奖记录」的一个活动 id；一个都没有则返回 {@code null}。
     *
     * <p>为什么不让用例写死 activityId=37（2026-10-18 重构）：
     * 37 是历史遗留活动，随时可能被清理；一旦消失，SA-23/SA-24 会以
     * 「分享按钮不存在 / 名单为空」这种看不出根因的方式失败。
     * 改为运行时反查：活动列表 → 逐个探测中奖记录 → 返回第一个有数据的。
     *
     * <p>为什么不用 JSON 库：本类刻意只依赖 JDK（见类注释），
     * 且这里只需要区分 {@code "data":[]} 与 {@code "data":[{...}]} 两种形状，
     * 用字符串判定足够且不会有解析失败的额外失败面。
     */
    public static String firstActivityIdWithRecords(String token) {
        String listResp = get("/activity/find-list?currentPage=1&pageSize=100", token);
        if (listResp == null || !listResp.contains("\"records\"")) {
            return null;
        }
        // 活动 id 在 JSON 里形如 "activityId":198，按出现顺序取即可
        Matcher m = Pattern.compile("\"activityId\"\\s*:\\s*(\\d+)").matcher(listResp);
        while (m.find()) {
            String activityId = m.group(1);
            // 该接口在公开白名单内（AppConfig.java:31），但仍带上 token，与页面行为一致
            String recResp = post("/draw/winning-records/show",
                    "{\"activityId\":" + activityId + "}", "token", token);
            if (recResp != null && recResp.contains("\"code\":200")
                    && !recResp.contains("\"data\":[]") && !recResp.contains("\"data\":null")) {
                return activityId;
            }
        }
        return null;
    }

    /** 创建一个 FAQ，返回 id */
    public static String createFaq(String adminToken) {
        String resp = post("/api/faq/add", String.format(
                "{\"question\":\"QA-UI 用例知识_%s\",\"answer\":\"用于 UI 用例验证\",\"category\":\"测试\"}",
                UUID.randomUUID().toString().substring(0, 6)), "token", adminToken);
        return extract(resp, "data");
    }

    /**
     * 找出库中第一个「已结束（COMPLETED）」的活动 id；一个都没有则返回 {@code null}。
     *
     * <p>为什么需要它（2026-10-18 新增）：{@code public-winners.html} 的活动选择器
     * 只列 COMPLETED 活动（见该页 :272 的 {@code data:{status:'COMPLETED'}}），
     * 进行中的活动还没开奖、没有名单可看。若库里一个已结束活动都没有，
     * 该页会显示「暂无已结束的活动」，此时不该让用例以
     * "按钮找不到"的形式失败——那是数据前置问题，要在断言消息里说清楚。
     *
     * <p>与 {@link #firstActivityIdWithRecords(String)} 的区别：
     * 后者按"确实有中奖记录"筛选（可能不是 COMPLETED），本方法按状态筛选（可能没有记录）。
     * 测试公开名单页时用前者更稳（保证有数据可断言），测试活动选择器时用本方法。
     */
    public static String firstCompletedActivityId(String token) {
        String resp = get("/activity/find-list?currentPage=1&pageSize=100&status=COMPLETED", token);
        if (resp == null) {
            return null;
        }
        Matcher m = Pattern.compile("\"activityId\"\\s*:\\s*(\\d+)").matcher(resp);
        return m.find() ? m.group(1) : null;
    }
}
