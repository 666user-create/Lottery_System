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
