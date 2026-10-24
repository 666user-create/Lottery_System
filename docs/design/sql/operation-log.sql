-- 操作日志表
-- 说明：本表只由 OperationLogInterceptor 异步写入，业务代码不直接操作它。
-- 只记录「写操作」（POST/PUT/DELETE）与登录，GET 查询一律不记，用于控制数据量。

CREATE TABLE IF NOT EXISTS operation_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id     BIGINT       COMMENT '操作人ID，未登录为空',
    identity    VARCHAR(20)  COMMENT '身份：ADMIN/NORMAL/ANONYMOUS',
    module      VARCHAR(50)  COMMENT '业务模块，如 活动模块',
    operation   VARCHAR(100) COMMENT '操作描述，如 创建活动',
    http_method VARCHAR(10)  COMMENT '请求方法',
    path        VARCHAR(200) COMMENT '请求路径',
    result_code INT          COMMENT '业务返回码，200 表示成功',
    result_msg  VARCHAR(500) COMMENT '结果描述/失败原因',
    http_status INT          COMMENT 'HTTP 状态码',
    cost_ms     BIGINT       COMMENT '处理耗时（毫秒）',
    ip          VARCHAR(64)  COMMENT '来源IP',
    gmt_create  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
    KEY idx_create (gmt_create),
    KEY idx_user (user_id),
    KEY idx_module (module)
) COMMENT='操作日志表';
