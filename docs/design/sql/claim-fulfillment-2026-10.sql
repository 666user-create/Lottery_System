-- ============================================================
-- 中奖履约(领奖 → 发货 → 签收)数据库迁移
-- 版本:2026-10
-- 设计文档:docs/design/中奖履约-领奖发货设计.md
--
-- 变更内容:
--   1. user            + 默认收货地址 3 个字段
--   2. winning_record  + 领奖状态与物流信息 6 个字段 + 1 个索引
--   3. human_handover  + 工单类型 1 个字段 + 1 个索引
--
-- 回滚语句(如需):
--   ALTER TABLE `user` DROP COLUMN receiver_name, DROP COLUMN receiver_phone, DROP COLUMN receiver_address;
--   ALTER TABLE winning_record DROP INDEX idx_winner_claim,
--     DROP COLUMN claim_status, DROP COLUMN claim_time, DROP COLUMN shipping_company,
--     DROP COLUMN shipping_no, DROP COLUMN shipped_time, DROP COLUMN completed_time;
--   ALTER TABLE human_handover DROP INDEX idx_type_status, DROP COLUMN type;
--
-- 历史数据说明:winning_record 存量记录一律视为「未领取」(claim_status 默认 NOT_CLAIMED),
--             不做批量回填;如需将线下已发放的历史记录标记为已签收,由管理员逐条订正。
-- ============================================================

-- 1. user:默认收货地址
ALTER TABLE `user`
    ADD COLUMN receiver_name    VARCHAR(50)  NULL COMMENT '收货人姓名' AFTER status,
    ADD COLUMN receiver_phone   VARCHAR(255) NULL COMMENT '收货人手机号(EncryptTypeHandler 加密存储)' AFTER receiver_name,
    ADD COLUMN receiver_address VARCHAR(500) NULL COMMENT '收货地址' AFTER receiver_phone;

-- 2. winning_record:领奖状态 + 物流信息
ALTER TABLE winning_record
    ADD COLUMN claim_status     VARCHAR(20)  NOT NULL DEFAULT 'NOT_CLAIMED'
        COMMENT '领奖状态:NOT_CLAIMED未领取/CLAIMED已申请待发货/SHIPPED已发货/COMPLETED已签收' AFTER winning_time,
    ADD COLUMN claim_time       DATETIME     NULL COMMENT '申请领奖时间' AFTER claim_status,
    ADD COLUMN shipping_company VARCHAR(50)  NULL COMMENT '承运公司' AFTER claim_time,
    ADD COLUMN shipping_no      VARCHAR(100) NULL COMMENT '物流单号' AFTER shipping_company,
    ADD COLUMN shipped_time     DATETIME     NULL COMMENT '发货时间' AFTER shipping_no,
    ADD COLUMN completed_time   DATETIME     NULL COMMENT '确认收货时间' AFTER shipped_time;

ALTER TABLE winning_record ADD INDEX idx_winner_claim (winner_id, claim_status);

-- 3. human_handover:工单类型(默认 HANDOVER,不影响既有转人工工单)
ALTER TABLE human_handover
    ADD COLUMN type VARCHAR(20) NOT NULL DEFAULT 'HANDOVER'
        COMMENT '工单类型:HANDOVER转人工/CLAIM领奖发货' AFTER session_id;
-- 关联业务ID:领奖工单指向 winning_record.id,便于处理工单时同步领奖状态
ALTER TABLE human_handover
    ADD COLUMN ref_id BIGINT NULL COMMENT '关联业务ID(领奖工单=winning_record.id)' AFTER type;

ALTER TABLE human_handover ADD INDEX idx_type_status (type, status);

-- 4. 知识库:领奖流程相关 FAQ(与界面流程保持一致)
UPDATE faq
   SET answer = '中奖后请在「我的中奖」里点「我要领奖」,填写收货地址提交申请;管理员备货发货后,系统会通过邮件和短信通知您,并可在「我的中奖」中查看物流单号;收到奖品后点「确认收货」即可完成。'
 WHERE question LIKE '%联系管理员领奖%' OR question LIKE '%怎么领奖%' OR question LIKE '%领奖流程%';
