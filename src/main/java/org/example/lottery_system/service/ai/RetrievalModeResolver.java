package org.example.lottery_system.service.ai;

import jakarta.annotation.PostConstruct;
import org.example.lottery_system.service.ai.rag.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 检索模式解析器:决定这次对话走哪套实现。
 *
 * 需求(用户确认):
 *   1. simple(原手写实现:远程向量 + MySQL faq_embedding)—— 能用时优先用
 *   2. 连不上外网(远程向量服务不可用)—— 自动回落到 framework(Spring AI + 本地向量模型)
 *
 * 配置 app.rag.mode:
 *   - auto      :自动探测(推荐)。远程 Embedding 可用 → simple;不可用 → framework
 *   - simple    :强制走手写实现
 *   - framework :强制走框架实现
 *
 * 注意:探测结果会缓存,避免每次对话都发一次网络请求;需要重新探测可调 refresh()。
 */
@Service
public class RetrievalModeResolver {

    private static final Logger log = LoggerFactory.getLogger(RetrievalModeResolver.class);

    public static final String MODE_AUTO = "auto";
    public static final String MODE_SIMPLE = "simple";
    public static final String MODE_FRAMEWORK = "framework";

    @Autowired private RagProperties ragProperties;
    @Autowired private EmbeddingClient embeddingClient;

    /** 解析结果缓存(null 表示还没解析过) */
    private volatile String resolvedMode;
    /** 解析依据(打日志/排查用) */
    private volatile String resolvedReason = "尚未解析";

    public String getConfiguredMode() {
        String mode = ragProperties.getMode();
        return (mode == null || mode.isBlank()) ? MODE_AUTO : mode.trim().toLowerCase();
    }

    /**
     * 启动时就解析一次:让模式判定结果在启动日志里可见,
     * 也避免"第一个请求"承担探测延迟(探测有 3 秒连接 / 6 秒读取超时)。
     */
    @PostConstruct
    public void warmUp() {
        try {
            getMode();
        } catch (Exception e) {
            log.warn("启动解析检索模式失败,将在首次请求时重试: {}", e.getMessage());
        }
    }

    /** 当前生效模式 */
    public String getMode() {
        String mode = resolvedMode;
        if (mode != null) {
            return mode;
        }
        return detect();
    }

    public boolean isSimple() {
        return MODE_SIMPLE.equals(getMode());
    }

    public boolean isFramework() {
        return MODE_FRAMEWORK.equals(getMode());
    }

    public String getReason() {
        getMode();
        return resolvedReason;
    }

    /** 手动重新探测(auto 模式下才有意义) */
    public synchronized String refresh() {
        resolvedMode = null;
        return detect();
    }

    /**
     * 解析模式(只在第一次或 refresh 时真正探测)
     */
    public synchronized String detect() {
        String configured = getConfiguredMode();

        if (MODE_SIMPLE.equals(configured)) {
            resolvedMode = MODE_SIMPLE;
            resolvedReason = "配置强制 simple";
        } else if (MODE_FRAMEWORK.equals(configured)) {
            resolvedMode = MODE_FRAMEWORK;
            resolvedReason = "配置强制 framework";
        } else {
            resolvedMode = probeRemoteEmbedding();
        }

        log.info("检索模式 = {} (配置={}, 依据: {})", resolvedMode, configured, resolvedReason);
        if (MODE_FRAMEWORK.equals(resolvedMode)) {
            log.info("说明:framework 模式使用本地向量模型,检索不依赖外网;"
                    + "但生成回答仍会调用 DeepSeek 接口。");
        }
        return resolvedMode;
    }

    /**
     * auto 模式的核心判定:
     *   1. provider=local(默认)→ 向量能力是本地模型,不依赖外网 → simple 可用
     *   2. provider=remote       → 真探一次远程 /embeddings:成功 simple,失败回落 framework
     */
    private String probeRemoteEmbedding() {
        if (embeddingClient.isLocalProvider()) {
            resolvedReason = "向量来源=本地模型(" + embeddingClient.describe() + "),simple 模式可用";
            return MODE_SIMPLE;
        }
        if (!embeddingClient.isApiKeyConfigured()) {
            resolvedReason = "未配置 ai.embedding.api-key,远程向量服务不可用";
            return MODE_FRAMEWORK;
        }
        try {
            double[] vector = embeddingClient.embed("lottery-embedding-probe");
            if (vector != null && vector.length > 0) {
                resolvedReason = "远程向量服务可用(维度=" + vector.length + "),优先使用 simple";
                return MODE_SIMPLE;
            }
            resolvedReason = "远程向量服务返回空向量";
            return MODE_FRAMEWORK;
        } catch (Exception e) {
            // 连不上外网 / Key 失效 / 超时 —— 全部回落 framework(本地向量模型)
            resolvedReason = "远程向量服务不可用(" + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage()) + "),回落 framework";
            return MODE_FRAMEWORK;
        }
    }
}
