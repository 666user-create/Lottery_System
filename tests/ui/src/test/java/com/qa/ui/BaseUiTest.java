package com.qa.ui;

import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestWatcher;

import java.io.ByteArrayInputStream;
import java.time.Duration;

/**
 * Selenium UI 自动化基类
 * <p>
 * 前置条件：
 * 1. 应用已启动于 http://localhost:8080
 * 2. Chrome 浏览器已安装（自动匹配本机 ChromeDriver；如版本不匹配，请用
 *    WebDriverManager 或手动放置 chromedriver.exe 到系统 PATH）
 * <p>
 * 使用说明：
 * - 默认无头模式（-Dselenium.headless=false 可切换有头便于调试）
 * - 超时：元素等待 10s
 * <p>
 * Allure 集成（2026-09-16 接入）：
 * - 类上统一挂 {@code @Epic("抽奖系统 Web UI")}，各测试类用 {@code @Feature} 标注模块；
 * - 失败时由 {@link #ALLURE_ON_FAILURE} 自动把**当前 URL / 页面标题 / 截图 / 页面源码**
 *   作为附件挂到 Allure 报告，避免"只看到断言失败、看不到现场"。
 */
@Epic("抽奖系统 Web UI")
public abstract class BaseUiTest {

    protected static final String BASE_URL = "http://localhost:8080";
    protected static WebDriver driver;

    /**
     * 最近一次被捕获（并 accept 掉）的弹窗文案；没有弹窗时为 null。
     *
     * <p>为什么要有这个字段（2026-10-18 空断言审计的根因之一）：
     * 原来的 {@link #dismissAlertIfPresent()} / {@link #waitForAlertAndAccept(int)} 只把弹窗文案
     * {@code System.out.println} 出来，**不返回也不保存**。于是用例想断言"弹窗说了什么"时无路可走，
     * 只能退而断言"整页文本包含 xx"这种弱断言 —— 例如 SA-10 本该断言
     * 「注册成功！请登录」（user-register.html:80-81），实际却断言
     * {@code bodyText().contains("成功") || 跳转}，而登录页上"注册"二字本来就存在，断言因此恒真。
     *
     * <p>现在把文案留痕在字段里，用例可直接断言精确文案；弹窗被 accept 掉后 DOM 上什么都没留下，
     * 这是唯一能拿到它的时机。
     */
    protected static String lastAlertText;

    /**
     * 失败现场取证：JUnit5 扩展，用例失败时把现场信息挂到 Allure 附件。
     * 用 TestWatcher 而不是 AspectJ 织入 —— 后者要改 surefire agent，风险大且收益相同。
     */
    @RegisterExtension
    static final TestWatcher ALLURE_ON_FAILURE = new TestWatcher() {
        @Override
        public void testFailed(ExtensionContext context, Throwable cause) {
            if (driver == null) {
                return;
            }
            try {
                String url = driver.getCurrentUrl();
                Allure.addAttachment("失败现场-URL", "text/plain", url, ".txt");
                Allure.addAttachment("失败现场-页面标题", "text/plain", driver.getTitle(), ".txt");
                byte[] png = ((org.openqa.selenium.TakesScreenshot) driver)
                        .getScreenshotAs(org.openqa.selenium.OutputType.BYTES);
                Allure.addAttachment("失败现场-截图", "image/png",
                        new ByteArrayInputStream(png), ".png");
                String src = driver.getPageSource();
                Allure.addAttachment("失败现场-页面源码", "text/html",
                        src.length() > 200_000 ? src.substring(0, 200_000) : src, ".html");
            } catch (Exception e) {
                // 取证失败不得掩盖原始断言失败
                Allure.addAttachment("失败现场-取证失败", "text/plain", String.valueOf(e), ".txt");
            }
        }
    };

    @BeforeAll
    static void initDriver() {
        // 先确认被测应用已就绪：规避启动初期的不稳定窗口
        // （实测：应用刚启动时同一账号注册可能返回 500，稍后恢复正常）
        ApiSupport.waitForAppReady();
        configureChromeDriverFromCache();

        ChromeOptions options = new ChromeOptions();
        boolean headless = Boolean.parseBoolean(System.getProperty("selenium.headless", "true"));
        if (headless) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--window-size=1440,900");
        driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        driver.manage().window().maximize();
    }

    /**
     * 直接使用本机缓存里的 chromedriver，绕开 Selenium Manager 的联网检查。
     *
     * <h3>为什么必须这么做（2026-10-18 实测）</h3>
     * Selenium 4.6+ 默认由 Selenium Manager 解析驱动：它每次都会去访问
     * {@code https://googlechromelabs.github.io/chrome-for-testing/known-good-versions-with-downloads.json}。
     * 本机需要代理才能出网，直连该域名会一直卡到超时，日志里表现为：
     * <pre>
     * 警告: There was an error managing chromedriver (error decoding response body for url
     *      (https://googlechromelabs.github.io/chrome-for-testing/known-good-versions-...json));
     *      using driver found in the cache
     * </pre>
     * 最坑的是：它<b>并不失败</b>，只是很慢 —— 单个测试类的 wall time 会被这 5 分钟拖满
     * （实测 WinningRecordsUiTest 用例本身合计仅 21 秒，但套件耗时 323.9 秒），
     * 全量套件因此白烧大量时间，看上去像"用例很慢"，实际跟用例毫无关系。
     *
     * <p>显式设置 {@code webdriver.chrome.driver} 后 Selenium 会跳过 Manager 的联网解析。
     * 版本选择上按 Chrome 主版本号匹配（本机 Chrome 与 chromedriver 均为 155），
     * 避免缓存里同时存在多个版本时挑错。找不到就静默回退给 Selenium Manager，
     * 这样在别的机器上仍然能正常跑。
     */
    private static void configureChromeDriverFromCache() {
        // 已经显式指定过就不再覆盖（便于命令行临时指定别的驱动）
        String explicit = System.getProperty("webdriver.chrome.driver");
        if (explicit != null && !explicit.isBlank()) {
            System.out.println("[驱动] 使用显式指定的 chromedriver: " + explicit);
            return;
        }
        try {
            java.nio.file.Path cache = java.nio.file.Paths.get(
                    System.getProperty("user.home"), ".cache", "selenium", "chromedriver");
            if (!java.nio.file.Files.isDirectory(cache)) {
                System.out.println("[驱动] 未找到缓存目录 " + cache + "，回退 Selenium Manager");
                return;
            }
            String chromeMajor = detectChromeMajor();
            java.util.List<java.nio.file.Path> all = new java.util.ArrayList<>();
            try (var stream = java.nio.file.Files.walk(cache, 4)) {
                for (java.nio.file.Path p : stream
                        .filter(java.nio.file.Files::isRegularFile)
                        .filter(p -> "chromedriver.exe".equals(p.getFileName().toString()))
                        .sorted()
                        .collect(java.util.stream.Collectors.toList())) {
                    all.add(p);
                }
            }
            java.nio.file.Path best = null;
            if (chromeMajor != null) {
                // 只认主版本号与 Chrome 一致的那个。
                // 不做"没有就随便挑一个"的兜底（第一版就是这么写的，结果选中 151 版驱动，
                // 报 This version of ChromeDriver only supports Chrome version 151）。
                for (java.nio.file.Path p : all) {
                    if (p.toString().contains(chromeMajor)) {
                        best = p;
                    }
                }
            }
            if (best != null) {
                System.setProperty("webdriver.chrome.driver", best.toString());
                System.out.println("[驱动] Chrome 主版本=" + chromeMajor + "，使用缓存 chromedriver: " + best);
            } else {
                System.out.println("[驱动] 缓存中无与 Chrome " + chromeMajor + " 匹配的 chromedriver（候选="
                        + all + "），回退 Selenium Manager");
            }
        } catch (Exception e) {
            System.out.println("[驱动] 解析缓存驱动失败，回退 Selenium Manager: " + e);
        }
    }

    /**
     * 取本机 Chrome 的主版本号（如 155）；取不到返回 null。
     *
     * <p>踩坑记录（2026-10-18，两版都错在这一个方法上）：
     * <ol>
     *   <li>第一版取「chrome.exe 所在目录名」，但 chrome.exe 就在 {@code Application\} 下，
     *       目录名恒为 "Application"，于是永远返回 null；</li>
     *   <li>第二版改为扫 {@code Application\} 下的版本目录、要求目录里有 {@code chrome.exe}。
     *       Chrome 155 改了安装布局：{@code Application\155.0.8059.84\} 里<b>没有</b> chrome.exe
     *       （只有 chrome.dll / chrome.exe.sig / chrome_pwa_launcher.exe），
     *       chrome.exe 仍留在上一级。判断再次落空，又返回 null。</li>
     * </ol>
     * 两次落空的后果是一样的：驱动选择退化成"按字母序取第一个"→ 选中 151 版驱动
     * （报 {@code This version of ChromeDriver only supports Chrome version 151}），
     * 或者退回 Selenium Manager 联网解析、白等 4 分钟。
     *
     * <p>现在改用 {@code chrome.dll} 作为"这确实是个 Chrome 版本目录"的标记：
     * 它和 chrome.exe 同源同版本，且在这种新版布局下仍然存在。
     * 再辅以同级的 {@code <版本号>.manifest} 作为旁证，两者取其一即可。
     */
    private static String detectChromeMajor() {
        String[] appDirs = {
                System.getenv("ProgramFiles") + "\\Google\\Chrome\\Application",
                System.getenv("ProgramFiles(x86)") + "\\Google\\Chrome\\Application",
                System.getenv("LOCALAPPDATA") + "\\Google\\Chrome\\Application",
        };
        String best = null;
        for (String dir : appDirs) {
            if (dir == null || dir.startsWith("null")) {
                continue;
            }
            java.nio.file.Path appDir = java.nio.file.Paths.get(dir);
            if (!java.nio.file.Files.isDirectory(appDir)) {
                continue;
            }
            try (var stream = java.nio.file.Files.list(appDir)) {
                for (java.nio.file.Path p : stream
                        .filter(java.nio.file.Files::isDirectory)
                        .collect(java.util.stream.Collectors.toList())) {
                    String name = p.getFileName().toString();
                    if (!name.matches("\\d+\\..+")) {
                        continue;
                    }
                    // 版本目录的标记物：chrome.dll（新版布局）或 chrome.exe（旧版布局）
                    boolean real = java.nio.file.Files.isRegularFile(p.resolve("chrome.dll"))
                            || java.nio.file.Files.isRegularFile(p.resolve("chrome.exe"));
                    if (!real) {
                        continue;
                    }
                    String major = name.split("\\.")[0];
                    if (best == null || Integer.parseInt(major) > Integer.parseInt(best)) {
                        best = major;
                    }
                }
            } catch (Exception ignored) {
                // 读不到就继续试下一个候选目录
            }
        }
        return best;
    }

    @AfterAll
    static void quitDriver() {
        if (driver != null) {
            driver.quit();
        }
    }

    protected void open(String path) {
        // 导航前清掉遗留 alert（前一个页面 JS 弹出的），否则 driver.get 本身会抛 UnhandledAlertException
        dismissAlertIfPresent();
        driver.get(BASE_URL + path);
        dismissAlertIfPresent();
    }

    /**
     * 快速探测并关闭页面 alert。
     *
     * 性能说明（2026-09-14 优化）：原实现把轮询上限设为 3 秒，且**无 alert 时同样空转满 3 秒**。
     * 单轮用例中本方法被调用 101 次（每个 {@code open()} 前后各 1 次 × 35 次 + 用例内显式 31 次），
     * 每次白等最多 2.6 秒，是 UI 全量耗时的主要来源（实测全量耗时由 744s 降至 324s，见
     * {@code docs/test/03-UI测试报告.md}）。现改为 400ms 快速探测；
     * 对「明确预期会弹窗」的场景请使用 {@link #waitForAlertAndAccept(int)}。
     */
    protected void dismissAlertIfPresent() {
        long deadline = System.currentTimeMillis() + 400;
        while (System.currentTimeMillis() < deadline) {
            try {
                org.openqa.selenium.Alert alert = driver.switchTo().alert();
                lastAlertText = alert.getText();
                System.out.println("[捕获弹窗] " + lastAlertText);
                alert.accept();
                deadline = System.currentTimeMillis() + 400; // 处理后可能再弹，再给 400ms
            } catch (org.openqa.selenium.NoAlertPresentException e) {
                sleep(50);
            } catch (org.openqa.selenium.WebDriverException e) {
                // 2026-10-18 实测（Chrome 155.0.8059.84 + Selenium 4.21.0）：
                // 页面正处于导航中时，switchTo().alert() 会先返回句柄，随后 accept() 抛
                //   WebDriverException: aborted by navigation: Not attached to an active page
                // 该异常是「此刻没有可稳定处理的弹窗」，不是用例失败，短暂等待后重试即可。
                //
                // 只吞这一种导航竞态：其余 WebDriverException（会话丢失、浏览器崩溃等）
                // 必须原样抛出，否则会被伪装成"没有弹窗"，让用例在后面更难排查的位置失败。
                String msg = String.valueOf(e.getMessage());
                if (msg.contains("aborted by navigation") || msg.contains("Not attached to an active page")) {
                    sleep(100);
                } else {
                    throw e;
                }
            }
        }
    }

    /**
     * 等待 alert 出现并关闭，返回是否捕获到。
     * 用于「明确预期会弹窗」的场景（如注册成功提示、工单处理成功提示），
     * 避免用固定 sleep 空等。
     */
    protected boolean waitForAlertAndAccept(int seconds) {
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            try {
                org.openqa.selenium.Alert alert = driver.switchTo().alert();
                lastAlertText = alert.getText();
                System.out.println("[捕获弹窗] " + lastAlertText);
                alert.accept();
                return true;
            } catch (org.openqa.selenium.NoAlertPresentException e) {
                sleep(100);
            } catch (org.openqa.selenium.WebDriverException e) {
                // 与 dismissAlertIfPresent 同因：导航竞态下 accept() 抛
                // WebDriverException: aborted by navigation。这里**不能**当成"捕获成功"，
                // 因为弹窗并未被处理；继续轮询，等页面稳定后真正的 alert 才会出现。
                String msg = String.valueOf(e.getMessage());
                if (!msg.contains("aborted by navigation") && !msg.contains("Not attached to an active page")) {
                    throw e;
                }
                sleep(100);
            }
        }
        return false;
    }

    /**
     * 从当前工作目录向上找到被测工程根目录。
     *
     * <p>为什么不能只按目录名判断（2026-09-16 实测踩坑）：
     * 用 `mvnw.cmd -f tests/ui/pom.xml test` 从仓库根执行时，Maven 会把
     * {@code user.dir} 设成**调用目录**（仓库根），而不是模块目录；
     * 但直接 `cd tests/ui && mvnw test` 时又是模块目录。故改用「工程根标记」判定。
     */
    protected static java.nio.file.Path projectRoot() {
        java.nio.file.Path dir = java.nio.file.Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null) {
            if (java.nio.file.Files.exists(dir.resolve("mvnw.cmd"))
                    || java.nio.file.Files.exists(dir.resolve(".mvn"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        return java.nio.file.Paths.get(System.getProperty("user.dir")).toAbsolutePath();
    }

    /** 截屏存档到项目根 tests/ui/screenshots/（可用 -Dselenium.screenshot.dir=... 覆盖） */
    protected void screenshot(String name) {
        try {
            var src = ((org.openqa.selenium.TakesScreenshot) driver).getScreenshotAs(
                    org.openqa.selenium.OutputType.BYTES);
            String override = System.getProperty("selenium.screenshot.dir");
            java.nio.file.Path dir = (override != null && !override.isBlank())
                    ? java.nio.file.Paths.get(override)
                    : projectRoot().resolve("tests/ui/screenshots");
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Files.write(dir.resolve(name + ".png"), src);
            System.out.println("[截图] " + dir.resolve(name + ".png"));
        } catch (Exception e) {
            System.err.println("截图失败: " + e.getMessage());
        }
    }

    // ==================== 登录态辅助 ====================

    /**
     * 注入登录态：先落到同源页面（否则 localStorage 写入会被浏览器拒绝），再写 token。
     * 用于「已登录场景」的用例，避免每个用例都走一遍 UI 登录（提升稳定性与速度）。
     */
    protected void injectToken(String token) {
        open("/user-login.html");
        ((org.openqa.selenium.JavascriptExecutor) driver)
                .executeScript("localStorage.setItem('token', arguments[0]);", token);
    }

    /** 注入登录态并标记身份（部分页面按 user_identity 控制入口显隐） */
    protected void injectToken(String token, String identity) {
        injectToken(token);
        ((org.openqa.selenium.JavascriptExecutor) driver)
                .executeScript("localStorage.setItem('user_identity', arguments[0]);", identity);
    }

    /** 清空登录态（模拟未登录） */
    protected void clearAuth() {
        open("/user-login.html");
        ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("localStorage.clear();");
    }

    /** 通过 UI 完成登录（用于「登录流程」本身的功能验证，非仅注入 token）
     *  登录成功后等待跳转（最多 12s），避免固定 sleep 造成的时序误判。 */
    protected void loginByUi(String loginName, String password) {
        open("/user-login.html");
        type(By.cssSelector("[name='phoneNumber']"), loginName);
        type(By.cssSelector("[name='password']"), password);
        clickIfPresent(By.cssSelector("button[type='submit']"));
        sleep(2000);
        dismissAlertIfPresent();
        waitForUrlContains("lobby", 12);
    }

    // ==================== 元素与等待辅助 ====================

    protected void type(org.openqa.selenium.By by, String value) {
        org.openqa.selenium.WebElement el = driver.findElement(by);
        el.clear();
        el.sendKeys(value);
    }

    protected boolean clickIfPresent(org.openqa.selenium.By by) {
        var list = driver.findElements(by);
        if (list.isEmpty()) {
            return false;
        }
        list.get(0).click();
        return true;
    }

    protected void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    /** 轮询等待元素出现，超时返回 null（不抛异常，便于断言时给出可读信息） */
    protected org.openqa.selenium.WebElement waitForElement(org.openqa.selenium.By by, int seconds) {
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            var list = driver.findElements(by);
            if (!list.isEmpty()) {
                return list.get(0);
            }
            sleep(300);
        }
        return null;
    }

    /**
     * 轮询等待某元素文本包含指定内容（用于验证异步渲染结果，如流式回复/表格数据）。
     *
     * <p><b>为什么对空串直接抛异常（2026-10-18 空断言审计的根治项，P-8）</b>：
     * 任何字符串都 {@code contains("")}，所以期望文本传空串时本方法恒真 —— 它不是"等待成功"，
     * 而是退化成"元素存在"，这正是 SA-26 曾经的空断言成因：
     * {@code waitForTextContains(By.cssSelector(".msg.ai .bubble"), "", 3)}。
     * 这类写法永远不会失败，却看起来在验证"AI 回复非空"。
     * 与其让调用方静默拿到恒真结果，不如在写错的那一刻就炸出来。
     *
     * @param by      元素定位
     * @param text    期望包含的文本，**不允许为 null 或空串**
     * @param seconds 最长等待秒数
     * @return 超时前命中返回 true；否则 false（调用方必须断言这个返回值，不能忽略）
     */
    protected boolean waitForTextContains(org.openqa.selenium.By by, String text, int seconds) {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException(
                    "waitForTextContains 的期望文本不能为 null/空串：任何文本都包含空串，"
                            + "该断言会恒真（历史教训 SA-26）。请断言具体的业务文案。by=" + by);
        }
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            var list = driver.findElements(by);
            if (!list.isEmpty()) {
                String t = list.get(0).getText();
                if (t != null && t.contains(text)) {
                    return true;
                }
            }
            sleep(300);
        }
        return false;
    }

    /**
     * 轮询等待元素数量达到期望值。
     *
     * <p><b>陷阱警告（2026-10-18 空断言审计，P-7）</b>：本方法在**超时时不失败**，
     * 而是返回"最后一次观测到的数量"。因此
     * <pre>{@code
     * int n = waitForElementCount(by, 5, 10);
     * // 错误写法：什么都没断言，等于白等 10 秒
     * // 正确写法：assertTrue(n >= 5, "...实际=" + n);
     * }</pre>
     * 必须由调用方对返回值做断言。保留"超时返回观测值 + 不抛异常"的语义是为了向后兼容
     * （既有用例依赖它返回 0 来判断空态），调用方自行断言即可获得可读的失败信息。
     */
    protected int waitForElementCount(org.openqa.selenium.By by, int minCount, int seconds) {
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        int count = 0;
        while (System.currentTimeMillis() < deadline) {
            count = driver.findElements(by).size();
            if (count >= minCount) {
                return count;
            }
            sleep(300);
        }
        return count;
    }

    /** 读取当前页面可见文本（用于内容级断言） */
    protected String bodyText() {
        var list = driver.findElements(org.openqa.selenium.By.tagName("body"));
        return list.isEmpty() ? "" : list.get(0).getText();
    }

    /**
     * 轮询等待 URL 包含关键字（验证页面跳转）。
     * 场景：前端先弹 alert 再跳转，dismiss alert 后跳转仍有延迟，
     *       直接断言 URL 会误判失败，故改为等待式断言。
     */
    protected boolean waitForUrlContains(String keyword, int seconds) {
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (driver.getCurrentUrl().contains(keyword)) {
                return true;
            }
            sleep(300);
        }
        return false;
    }

    // ==================== 授权守卫（auth-guard.js）痕迹读取 ====================

    /**
     * 读取 {@code <body>} 上的任意 data-* 属性（没有则返回 null）。
     *
     * <p>用途：{@code src/main/resources/static/js/auth-guard.js} 在处理 401/403 时会把结果
     * 写在 body 属性上（:96-98 的 {@code mark()}）：
     * <ul>
     *   <li>{@code data-auth-guard} = "401" / "403"</li>
     *   <li>{@code data-auth-guard-action} = 触发动作，如「加载工单列表」</li>
     *   <li>{@code data-auth-guard-message} = 后端 message</li>
     * </ul>
     * 弹窗会被 accept 掉、跳转后 DOM 也会换掉，只有 403（不跳转）场景能稳定读到这些痕迹。
     * 这是断言"越权确实被拦截"的唯一可靠信号 —— 断言"表格里没数据"会把白屏/慢加载也当成拦截成功。
     *
     * @param name 属性名，如 {@code "data-auth-guard"}
     * @return 属性值；元素不存在或属性缺失返回 null
     */
    protected String bodyAttribute(String name) {
        Object v = ((org.openqa.selenium.JavascriptExecutor) driver)
                .executeScript("return document.body ? document.body.getAttribute(arguments[0]) : null;", name);
        return v == null ? null : String.valueOf(v);
    }

    /**
     * 轮询等待 body 的某个 data-* 属性等于期望值（auth-guard 的痕迹是异步写的，不能立刻读）。
     *
     * <p>为什么每次轮询前都要先关弹窗：auth-guard.js 在写属性之后还会 {@code alert(...)}
     * （:125 / :166），而原生弹窗会**阻塞页面 JS**，此时 ChromeDriver 执行脚本会抛
     * {@code UnexpectedAlertOpenException}。不先 accept 掉，读属性这一步就直接报错了。
     *
     * @return 超时前命中返回 true
     */
    protected boolean waitForBodyAttribute(String name, String expected, int seconds) {
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            dismissAlertIfPresent();
            try {
                String v = bodyAttribute(name);
                if (expected.equals(v)) {
                    return true;
                }
            } catch (org.openqa.selenium.WebDriverException e) {
                // 探测与关弹窗之间又冒出新弹窗（原生弹窗阻塞 JS 时会抛 UnhandledAlertException），
                // 下一轮先关弹窗再读；这里的重试不影响失败判定，超时仍返回 false。
                dismissAlertIfPresent();
            }
            sleep(200);
        }
        return false;
    }

    /** 清空上一条弹窗文案留痕，避免上一个用例的弹窗污染本用例的断言 */
    protected void clearLastAlertText() {
        lastAlertText = null;
    }

    /**
     * 取"最后一个匹配元素"的可见文本；没有匹配返回 null。
     * 用于"最后一条聊天气泡 / 最后一行表格"这类断言，比 {@code get(0)} 更贴近业务语义。
     */
    protected String lastTextOf(org.openqa.selenium.By by) {
        var list = driver.findElements(by);
        if (list.isEmpty()) {
            return null;
        }
        return list.get(list.size() - 1).getText();
    }

    /**
     * 轮询等待某元素文本匹配正则（用于「共 12 条」「3 个」这类含动态数字的文案）。
     *
     * <p>为什么不直接 {@code assertEquals("共 12 条", …)}：条数随数据库变化，
     * 写死数字会让用例变成"改数据就红"的假失败。这里断言的是**格式与渲染是否发生**，
     * 数字具体是多少交给页面自己决定；需要校验数字一致性的地方，调用方再单独交叉验证。
     *
     * @return 命中返回 true；超时返回 false
     */
    protected boolean waitForTextMatches(org.openqa.selenium.By by, String regex, int seconds) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(regex);
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            var list = driver.findElements(by);
            if (!list.isEmpty()) {
                String t = list.get(0).getText();
                if (t != null && p.matcher(t.trim()).find()) {
                    return true;
                }
            }
            sleep(200);
        }
        return false;
    }
}
