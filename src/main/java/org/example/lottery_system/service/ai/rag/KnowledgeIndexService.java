package org.example.lottery_system.service.ai.rag;

import jakarta.annotation.PostConstruct;
import org.example.lottery_system.dao.dataobject.FaqDO;
import org.example.lottery_system.dao.mapper.FaqMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 知识库索引管道(离线阶段)
 *
 * ETL 流程:MySQL → FaqDocumentReader(读) → TokenTextSplitter(切分) → SimpleVectorStore(向量化+持久化)
 *
 * 开关策略(app.rag.reindex):
 * - true : 重新读库→切分→向量化,并覆盖落盘文件(改了 FAQ 后手动触发)
 * - false: 直接加载已落盘的向量库(默认,省算力/避免重复向量化)
 * - 落盘文件不存在时:自动降级为一次全量重建
 *
 * 并行加载:用线程池 + CountDownLatch 并行"读取 + 切分",再统一向量化写入
 * (向量化串行执行,避免本地 ONNX 模型并发推理的不确定性)
 */
@Service
public class KnowledgeIndexService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexService.class);

    /** 与 FaqDocumentReader 写入的 metadata 键保持一致 */
    private static final String FAQ_ID_KEY = "faq_id";

    /**
     * 单条 FAQ 最多可能被切成多少个文档块(用于"按 id 删除"时枚举序号)。
     * 当前 chunk-size=800、FAQ 文本都很短,实际每条约 1 块;给足余量即可,
     * 删除不存在的 id 是安全空操作。
     */
    private static final int MAX_CHUNKS_PER_FAQ = 16;

    @Autowired private FaqMapper faqMapper;
    @Autowired private SimpleVectorStore vectorStore;
    @Autowired private RagProperties props;

    /** 当前索引中的文档块数量(供健康检查/管理端查看) */
    private volatile int indexedChunkCount = 0;

    /**
     * 索引在 Bean 初始化阶段完成,而不是用 ApplicationRunner。
     *
     * 原因(实测踩坑):ApplicationRunner 在 "Started" 之后才执行,而本项目启动时
     * RabbitMQ 不可达会阻塞约 60 秒,导致索引迟迟未就绪——此时用户提问会静默降级成
     * 关键词检索(日志表现为"向量召回为空")。改为 @PostConstruct 后,索引在启动
     * 关键路径上完成,检索链路一上线就是可用的向量检索。
     */
    @PostConstruct
    public void init() {
        try {
            initIndex();
        } catch (Exception e) {
            // 索引失败不应阻止应用启动:检索侧会自动降级为关键词检索
            log.error("知识库索引初始化失败(检索将降级为关键词模式): {}", e.getMessage(), e);
        }
    }

    /**
     * 索引初始化:按配置决定「重建」还是「加载」
     */
    public synchronized int initIndex() {
        File storeFile = new File(props.getStoreFile());
        if (!props.isReindex() && storeFile.exists()) {
            log.info("加载已持久化的向量库: {}", storeFile.getAbsolutePath());
            vectorStore.load(storeFile);
            int loaded = simpleCount(storeFile);
            if (loaded <= 0) {
                // 文件存在但解析为 0 条,说明内容异常,直接重建,避免"静默降级为关键词检索"
                log.warn("向量库文件解析出的文档块为 0,改为执行全量重建");
                return rebuild();
            }
            indexedChunkCount = loaded;
            log.info("向量库加载完成,文档块数(估算): {}", indexedChunkCount);
            return indexedChunkCount;
        }
        if (!props.isReindex()) {
            log.info("未找到向量库文件,首次自动执行全量重建");
        }
        return rebuild();
    }

    /**
     * 全量重建索引:Reader → 切分 → 向量化 → 落盘
     *
     * 说明:文档块 id 已规范成 faq-{faqId}-{序号}(见 buildChunks),
     * 所以重复重建会覆盖同 id,不会累积重复文档,无需"清空索引"这种
     * SimpleVectorStore 不支持的按过滤删除操作。
     */
    public synchronized int rebuild() {
        long start = System.currentTimeMillis();
        List<FaqDO> faqs = faqMapper.selectAll();
        if (faqs == null || faqs.isEmpty()) {
            log.warn("FAQ 表为空,跳过索引构建");
            indexedChunkCount = 0;
            return 0;
        }

        // 1. 并行「读取 + 切分」(IO/CPU 并行)
        List<Document> allChunks = readAndSplitInParallel(faqs);

        // 2. 统一向量化 + 写入向量库(串行,避免本地模型并发推理问题)
        log.info("开始向量化写入,文档块数: {}", allChunks.size());
        vectorStore.add(allChunks);

        // 3. 持久化落盘
        try {
            File storeFile = new File(props.getStoreFile());
            File parent = storeFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                log.warn("向量库目录创建失败: {}", parent.getAbsolutePath());
            }
            vectorStore.save(storeFile);
            log.info("向量库已持久化: {}", storeFile.getAbsolutePath());
        } catch (Exception e) {
            log.error("向量库落盘失败(内存索引仍可用): {}", e.getMessage());
        }

        indexedChunkCount = allChunks.size();
        log.info("知识库索引重建完成: FAQ {} 条 → 文档块 {} 个, 耗时 {} ms",
                faqs.size(), indexedChunkCount, System.currentTimeMillis() - start);
        return indexedChunkCount;
    }

    // ==================== 增量索引(FAQ 增删后调用) ====================

    /**
     * 新增/更新单条 FAQ 到向量索引(增量,不重建全量)
     *
     * 背景(修复):管理端通过 /api/faq/add 新增 FAQ 后,原先只写 MySQL 与老向量表,
     * 不更新 SimpleVectorStore,导致"客服检索不到新知识",且重启也不会生效
     * (reindex=false 且有落盘文件时直接 load 旧索引)。
     */
    public synchronized void addOrUpdateFaq(FaqDO faq) {
        if (faq == null || faq.getId() == null) {
            return;
        }
        try {
            List<Document> chunks = buildChunks(List.of(faq));
            // 先删同 id 的旧块(覆盖"更新"场景,并保证块数变少时不残留)
            deleteFaqChunks(faq.getId());
            vectorStore.add(chunks);
            persistStore();
            log.info("FAQ[{}] 已增量写入向量索引: 块数={}", faq.getId(), chunks.size());
        } catch (Exception e) {
            log.error("FAQ[{}] 增量索引失败,回退全量重建", faq.getId(), e);
            rebuild();
        }
    }

    /**
     * 从向量索引移除某条 FAQ(删除 FAQ 后调用)
     */
    public synchronized void removeFaqFromIndex(Long faqId) {
        if (faqId == null) {
            return;
        }
        try {
            deleteFaqChunks(faqId);
            persistStore();
            log.info("FAQ[{}] 已从向量索引移除", faqId);
        } catch (Exception e) {
            log.error("FAQ[{}] 索引删除失败,回退全量重建", faqId, e);
            rebuild();
        }
    }

    /** Reader → Transformer:把 FAQ 转成文档块,并统一改成"可预测的文档 id" */
    private List<Document> buildChunks(List<FaqDO> faqs) {
        // TokenTextSplitter 构造参数:(chunkSize, minChunkSizeChars, minChunkLengthToEmbed, maxNumChunks, keepSeparator)
        TokenTextSplitter splitter = new TokenTextSplitter(props.getChunkSize(), 350, 5, 10000, true);
        List<Document> raw = splitter.apply(new FaqDocumentReader(faqs).get());

        // 关键:SimpleVectorStore 只支持「按 id 删除」,不支持按 metadata 过滤删除
        // (父类 doDelete(Filter.Expression) 直接抛 UnsupportedOperationException,实测确认)。
        // 因此把块 id 规范成 faq-{faqId}-{序号}:重复写入会覆盖同 id,删除时可精确命中,
        // 既避免重复文档累积,也不需要"清空整个索引"这种 SimpleVectorStore 不支持的删除方式。
        Map<Long, Integer> counter = new HashMap<>();
        List<Document> normalized = new ArrayList<>(raw.size());
        for (Document chunk : raw) {
            Long faqId = toLong(chunk.getMetadata().get(FAQ_ID_KEY));
            if (faqId == null) {
                normalized.add(chunk);
                continue;
            }
            int seq = counter.merge(faqId, 1, Integer::sum) - 1;
            normalized.add(new Document(chunkId(faqId, seq), chunk.getText(), chunk.getMetadata()));
        }
        return normalized;
    }

    /** 文档块 id:faq-{faqId}-{序号} */
    private String chunkId(Long faqId, int seq) {
        return "faq-" + faqId + "-" + seq;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 删除指定 FAQ 的全部文档块。
     *
     * 因为 id 是"faq-{id}-{序号}",这里按序号区间逐个尝试删除;
     * SimpleVectorStore 删除不存在的 id 是安全的空操作,所以不需要额外记录块数。
     */
    private void deleteFaqChunks(Long faqId) {
        List<String> ids = new ArrayList<>(MAX_CHUNKS_PER_FAQ);
        for (int i = 0; i < MAX_CHUNKS_PER_FAQ; i++) {
            ids.add(chunkId(faqId, i));
        }
        vectorStore.delete(ids);
    }

    /** 落盘(内存索引始终有效,落盘失败只记日志) */
    private void persistStore() {
        try {
            File storeFile = new File(props.getStoreFile());
            File parent = storeFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                log.warn("向量库目录创建失败: {}", parent.getAbsolutePath());
            }
            vectorStore.save(storeFile);
        } catch (Exception e) {
            log.error("向量库落盘失败(内存索引仍可用): {}", e.getMessage());
        }
    }

    /**
     * 并行执行 Reader + Transformer(多线程分批处理),用 CountDownLatch 等待全部完成
     */
    private List<Document> readAndSplitInParallel(List<FaqDO> faqs) {
        int threads = Math.max(1, Math.min(props.getLoadThreads(), faqs.size()));
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        List<Document> allChunks = Collections.synchronizedList(new ArrayList<>());

        int batchSize = (faqs.size() + threads - 1) / threads;
        for (int i = 0; i < threads; i++) {
            int from = i * batchSize;
            int to = Math.min(faqs.size(), from + batchSize);
            if (from >= to) {
                latch.countDown();
                continue;
            }
            List<FaqDO> subList = new ArrayList<>(faqs.subList(from, to));
            pool.submit(() -> {
                try {
                    // 复用 buildChunks:保证"并行重建"和"增量写入"产出完全一致的文档 id,
                    // 否则重建写入的是随机 id,与增量路径的 id 不一致,又会造成重复文档。
                    allChunks.addAll(buildChunks(subList));
                } catch (Exception e) {
                    log.error("并行切分失败", e);
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            if (!latch.await(5, TimeUnit.MINUTES)) {
                log.warn("并行切分超时,使用已完成部分");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("并行切分被中断");
        } finally {
            pool.shutdown();
        }
        return allChunks;
    }

    /** 粗略统计向量库文件中的文档块数(避免重复解析 JSON 的复杂度) */
    private int simpleCount(File file) {
        try {
            String content = new String(java.nio.file.Files.readAllBytes(file.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8);
            // SimpleVectorStore 落盘 JSON 中每个文档含 "id":"..." 字段
            int count = 0;
            int idx = 0;
            while ((idx = content.indexOf("\"text\"", idx)) >= 0) {
                count++;
                idx++;
            }
            return count;
        } catch (Exception e) {
            log.warn("统计向量库文档数失败: {}", e.getMessage());
            return 0;
        }
    }

    public int getIndexedChunkCount() {
        return indexedChunkCount;
    }
}
