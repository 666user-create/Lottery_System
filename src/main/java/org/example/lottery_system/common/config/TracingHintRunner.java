package org.example.lottery_system.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.HttpURLConnection;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * 启动收尾:确保 Zipkin 可用,并把入口打在启动日志末尾。
 *
 * 为什么不让应用直接"内嵌"Zipkin?
 *   Zipkin 3.x 服务端基于 Armeria,不是 Spring MVC,官方已不再提供
 *   "加依赖塞进自己 Spring Boot 应用"的自动配置(那是 2.x 的做法),
 *   所以 Zipkin 只能是独立进程。既然如此,就由本应用在启动时把它拉起来,
 *   省掉手动启动 Zipkin 这一步。
 *
 * 为什么用 ApplicationRunner:Spring Boot 顺序是
 *   "context refresh → 打印 Started ... in Xs → 执行 ApplicationRunner",
 *   所以这段日志会落在整段启动输出的最后,不会被冲掉。
 *   (注意:ApplicationRunner 晚于 8080 可用,索引预热那类"上线即可用"的
 *    逻辑仍要放在 @PostConstruct,不要挪到这里。)
 */
@Slf4j
@Component
public class TracingHintRunner implements ApplicationRunner, Ordered {

    /** 上报地址,默认与 application.properties 一致;界面地址由它推导 */
    @Value("${management.zipkin.tracing.endpoint:http://localhost:9411/api/v2/spans}")
    private String zipkinEndpoint;

    /** 是否在应用启动时自动把没在跑的 Zipkin 拉起来 */
    @Value("${app.zipkin.auto-start:true}")
    private boolean autoStart;

    /** Zipkin 可执行 jar 的绝对路径 */
    @Value("${app.zipkin.jar:D:/java/lottery_system/zipkin/zipkin-server-3.6.1-exec.jar}")
    private String zipkinJar;

    /** 等待 Zipkin 起来的最长时间(秒);jar 冷启动实测约 8~12 秒 */
    @Value("${app.zipkin.start-timeout-seconds:40}")
    private int startTimeoutSeconds;

    @Override
    public int getOrder() {
        // 尽量靠后:让其它 ApplicationRunner 的日志先出,这段保持在最末尾
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void run(ApplicationArguments args) {
        String ui = zipkinEndpoint.replace("/api/v2/spans", "/zipkin/");
        boolean alreadyUp = isReachable(ui);

        // 没在跑且允许自启 → 拉起 jar,然后等端口就绪
        boolean up = alreadyUp || (autoStart && tryStartZipkin(ui));

        // 日志分级:已在跑就一行带过;自启成功/失败才多说两句。
        if (up && alreadyUp) {
            log.info("Zipkin 已就绪: {}", ui);
        } else if (up) {
            log.info("Zipkin 未运行,已自动启动: {}", ui);
        } else {
            log.warn("Zipkin 未运行且自动启动失败,本次链路追踪数据会丢失。界面: {}(可手动启动 Zipkin,命令见 docs/design/Zipkin链路追踪.md §2.2)", ui);
        }
    }

    /**
     * 尝试启动 Zipkin jar 并等待其就绪。
     *
     * 进程用 ProcessBuilder 直接起 java,输出重定向到日志文件。Windows 下
     * 父进程(本应用)结束不会带走它,所以关掉应用后 Zipkin 仍在跑 —— 符合
     * "追踪服务独立于业务应用"的预期,也避免每次重启应用都要等 Zipkin 冷启动。
     */
    private boolean tryStartZipkin(String ui) {
        File jar = new File(zipkinJar);
        if (!jar.isFile()) {
            log.warn("[Zipkin自启] jar 不存在,跳过自动启动: {}", zipkinJar);
            return false;
        }

        // 已在跑但还没就绪(刚启动几秒内)就不重复起,避免 bind 冲突
        // (isReachable 失败只说明界面未就绪,不代表端口空闲,这里只按"绝对没跑过"来起)
        String javaExe = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java.exe";
        // 输出统一落到 zipkin-autostart.log(每次自启覆盖,内容就是 Zipkin 自己的日志)
        Path logFile = jar.toPath().resolveSibling("zipkin-autostart.log");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    javaExe, "-DSTORAGE_TYPE=mem", "-jar", jar.getAbsolutePath());
            pb.redirectErrorStream(true);
            pb.redirectOutput(logFile.toFile());

            Process p = pb.start();
            log.debug("[Zipkin自启] 已拉起进程 pid={},等待端口就绪(最多 {}s)...", p.pid(), startTimeoutSeconds);

            long deadline = System.currentTimeMillis() + startTimeoutSeconds * 1000L;
            while (System.currentTimeMillis() < deadline) {
                if (isReachable(ui)) {
                    return true;
                }
                // 进程提前挂了就别干等
                if (!p.isAlive()) {
                    log.warn("[Zipkin自启] 进程已退出(exit={}),日志: {}", p.exitValue(), logFile);
                    return false;
                }
                TimeUnit.MILLISECONDS.sleep(500);
            }
            log.warn("[Zipkin自启] {}s 内未就绪,日志: {}", startTimeoutSeconds, logFile);
            return false;
        } catch (Exception e) {
            log.warn("[Zipkin自启] 启动失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 探测 Zipkin 界面是否可达。
     *
     * localhost 没监听时会立刻 "Connection refused",不会拖慢启动;
     * 仍然给 1.5 秒超时兜底,避免极端网络环境下卡住启动日志。
     */
    private boolean isReachable(String ui) {
        HttpURLConnection conn = null;
        try {
            // 用 URI.create().toURL():JDK 20 起 new URL(String) 已标记过时
            conn = (HttpURLConnection) java.net.URI.create(ui).toURL().openConnection();
            conn.setConnectTimeout(1500);
            conn.setReadTimeout(1500);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            return code >= 200 && code < 500;
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
