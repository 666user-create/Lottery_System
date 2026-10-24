package org.example.lottery_system.dao.dataobject;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 转人工工单实体
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class HumanHandoverDO extends BaseDO {

    /** 工单类型:转人工 */
    public static final String TYPE_HANDOVER = "HANDOVER";
    /** 工单类型:领奖发货 */
    public static final String TYPE_CLAIM = "CLAIM";
    /** 用户ID(从token解析,可能为空) */
    private Long userId;
    /** 会话标识 */
    private String sessionId;
    /** 工单类型:HANDOVER转人工(默认) / CLAIM领奖发货 */
    private String type = "HANDOVER";
    /** 关联业务ID:领奖工单指向 winning_record.id */
    private Long refId;
    /** 用户转人工时最后说的话 */
    private String question;
    /** 状态:PENDING待处理/PROCESSING处理中/RESOLVED已解决/CLOSED已关闭 */
    private String status;
    /** 处理人 */
    private String handler;
    /** 处理备注 */
    private String remark;
    // ===== 以下两个字段仅用于「领奖发货」接口传参,表中没有对应列,不参与持久化 =====
    /** 承运公司(仅接口传参) */
    private String shippingCompany;
    /** 物流单号(仅接口传参) */
    private String shippingNo;
}
