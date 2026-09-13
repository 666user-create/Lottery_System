package com.qa.ui;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

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
 */
public abstract class BaseUiTest {

    protected static final String BASE_URL = "http://localhost:8080";
    protected static WebDriver driver;

    @BeforeAll
    static void initDriver() {
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

    @AfterAll
    static void quitDriver() {
        if (driver != null) {
            driver.quit();
        }
    }

    protected void open(String path) {
        // 导航前先清掉遗留 alert（前一个页面 JS 弹出的），否则 driver.get 本身会抛 UnhandledAlertException
        dismissAlertIfPresent();
        driver.get(BASE_URL + path);
        dismissAlertIfPresent();
    }

    /** 轮询等待并关闭页面 alert（页面 JS 可能异步弹出，最多等 3 秒），避免阻塞后续操作 */
    protected void dismissAlertIfPresent() {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            try {
                org.openqa.selenium.Alert alert = driver.switchTo().alert();
                System.out.println("[捕获弹窗] " + alert.getText());
                alert.accept();
                // 关闭后可能又弹新的，继续轮询
                deadline = System.currentTimeMillis() + 3000;
            } catch (org.openqa.selenium.NoAlertPresentException e) {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    /** 截屏存档到项目根 docs/test/artifacts/screenshots/ */
    protected void screenshot(String name) {
        try {
            var src = ((org.openqa.selenium.TakesScreenshot) driver).getScreenshotAs(
                    org.openqa.selenium.OutputType.BYTES);
            // 固定使用项目根目录：selenium 工程位于 <root>/docs/test/artifacts/selenium，
            // userDir 为 selenium 目录时向上 4 级到项目根；否则视为已在根目录运行
            java.nio.file.Path userDir = java.nio.file.Paths.get(System.getProperty("user.dir"));
            java.nio.file.Path root = userDir.getFileName().toString().equals("selenium")
                    ? userDir.getParent().getParent().getParent().getParent()
                    : userDir;
            java.nio.file.Path dir = root.resolve("docs/test/artifacts/screenshots");
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Files.write(dir.resolve(name + ".png"), src);
            System.out.println("[截图] " + dir.resolve(name + ".png"));
        } catch (Exception e) {
            System.err.println("截图失败: " + e.getMessage());
        }
    }
}
