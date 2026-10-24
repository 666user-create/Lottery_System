CREATE TABLE IF NOT EXISTS faq (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    question    VARCHAR(500) NOT NULL COMMENT '问题',
    answer      TEXT         NOT NULL COMMENT '答案',
    category    VARCHAR(100) DEFAULT '通用' COMMENT '分类,如:抽奖规则/领奖/账号',
    gmt_create  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    gmt_modified DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间'
) COMMENT='AI客服知识库';

CREATE TABLE IF NOT EXISTS faq_embedding (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    faq_id      BIGINT NOT NULL COMMENT '对应faq表的id',
    embedding   TEXT   NOT NULL COMMENT '向量,JSON数组字符串,如 [0.12,-0.35,...]',
    gmt_create  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    gmt_modified DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    UNIQUE KEY uk_faq_id (faq_id)
) COMMENT='FAQ向量表';

CREATE TABLE IF NOT EXISTS human_handover (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id      BIGINT COMMENT '用户ID(从token解析,可能为空)',
    session_id   VARCHAR(64) NOT NULL COMMENT '会话标识',
    question     TEXT COMMENT '用户转人工时最后说的话',
    status       VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态:PENDING待处理/PROCESSING处理中/RESOLVED已解决/CLOSED已关闭',
    handler      VARCHAR(100) COMMENT '处理人',
    remark       TEXT COMMENT '处理备注',
    gmt_create   DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    gmt_modified DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    KEY idx_status (status)
) COMMENT='转人工工单表';

-- ===== 框架化 RAG 重构新增:Jdbc 会话记忆表 =====
-- 由 Spring AI 的 JdbcChatMemoryRepository 使用(表结构取自其官方 schema-mysql.sql)
-- 用途:把多轮会话持久化到 MySQL,替代/补充原 Redis 手写会话记忆
CREATE TABLE IF NOT EXISTS SPRING_AI_CHAT_MEMORY (
    conversation_id VARCHAR(36) NOT NULL COMMENT '会话ID(对应前端 sessionId)',
    content         TEXT        NOT NULL COMMENT '消息内容',
    type            VARCHAR(10) NOT NULL COMMENT '消息类型:USER/ASSISTANT/SYSTEM/TOOL',
    timestamp       TIMESTAMP   NOT NULL COMMENT '消息时间',
    CONSTRAINT TYPE_CHECK CHECK (type IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL'))
) COMMENT='Spring AI Jdbc 会话记忆';

-- 索引:MySQL 不支持 CREATE INDEX IF NOT EXISTS,重复执行前请先确认是否已存在
CREATE INDEX SPRING_AI_CHAT_MEMORY_IDX
    ON SPRING_AI_CHAT_MEMORY (conversation_id, timestamp);

-- 清理任务(建议:定时删除 7 天前的会话,避免表无限增长;Jdbc 记忆没有自动过期)
-- DELETE FROM SPRING_AI_CHAT_MEMORY WHERE timestamp < DATE_SUB(NOW(), INTERVAL 7 DAY);
