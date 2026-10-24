package org.example.lottery_system.service.ai.rag;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 管道配置(app.rag.*)
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.rag")
public class RagProperties {

    /** 是否重建知识库索引:true=重新读库→切分→向量化;false=加载已落盘的向量库 */
    private boolean reindex = false;

    /** 向量库落盘文件 */
    private String storeFile = "./data/vector-store.json";

    /** 向量召回条数 */
    private int topK = 5;

    /** 重排后注入提示词的条数 */
    private int rerankTopN = 3;

    /** 本地重排开关 */
    private boolean rerankEnabled = true;

    /** 文档切分大小(字符) */
    private int chunkSize = 800;

    /** 切分重叠(说明:TokenTextSplitter 不支持 overlap,保留配置位供自定义 splitter 使用) */
    private int chunkOverlap = 100;

    /** 相似度阈值,低于该值丢弃 */
    private double minScore = 0.3;

    /** 索引并行加载线程数 */
    private int loadThreads = 4;

    /**
     * 检索模式:
     * - framework:框架化(SimpleVectorStore + Advisor + Jdbc 记忆 + 本地重排)
     * - simple   :原手写实现(MySQL 存向量 + 内存余弦相似度 + Redis 记忆)
     */
    private String mode = "framework";

    /** 会话窗口最大消息条数(MessageWindowChatMemory) */
    private int memoryMaxMessages = 20;
}
