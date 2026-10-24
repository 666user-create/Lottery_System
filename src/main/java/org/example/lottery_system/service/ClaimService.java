package org.example.lottery_system.service;

import org.example.lottery_system.dao.dataobject.UserDO;

/**
 * 中奖履约服务:领奖 → 发货 → 签收。
 *
 * 状态推进方(见 docs/design/中奖履约-领奖发货设计.md):
 *   客户端(用户):claim(申请领奖)、confirmReceived(确认收货)
 *   管理端(管理员):shipByTicket(登记发货)、cancelTicket(取消领奖)
 *
 * 每个迁移都用"带原状态的 UPDATE"做乐观锁,影响行数 0 即表示状态不符或并发重复提交。
 */
public interface ClaimService {

    /**
     * 客户端:申请领奖(同时可提交/更新收货信息)
     *
     * @param receiverName    收货人(可为空,为空时用用户已保存的默认地址)
     * @param receiverPhone   收货手机号(可为空)
     * @param receiverAddress 收货地址(可为空)
     * @return 生成的领奖工单 id
     */
    Long claim(Long userId, Long recordId, String receiverName, String receiverPhone, String receiverAddress);

    /** 客户端:确认收货(已发货 → 已签收) */
    boolean confirmReceived(Long userId, Long recordId);

    /** 管理端:领奖工单登记发货(同步中奖记录状态,并发邮件+短信通知) */
    boolean shipByTicket(Long ticketId, String shippingCompany, String shippingNo, String handler, String remark);

    /** 管理端:取消领奖工单(中奖记录回退为「未领取」,允许重新申请) */
    boolean cancelTicket(Long ticketId, String handler, String remark);

    /**
     * 管理端:删除工单(任何阶段都可删)
     *
     * 若该工单是领奖工单(type=CLAIM)且关联的中奖记录还处于「已申请待发货」,
     * 删除时会把记录回退为「未领取」,避免出现"没有工单却卡在已申请"的悬挂态;
     * 已发货/已签收的记录不动(删工单不该撤销已完成的履约)。
     */
    boolean deleteTicket(Long ticketId);

    /** 保存默认收货信息 */
    void saveReceiverInfo(Long userId, String receiverName, String receiverPhone, String receiverAddress);

    /** 查询默认收货信息 */
    UserDO getReceiverInfo(Long userId);

    // ==================== 管理端:中奖履约管理 ====================

    /** 管理端分页查询中奖记录(可按领奖状态筛选) */
    java.util.List<org.example.lottery_system.dao.dataobject.WinningRecordDO> listForAdmin(
            String claimStatus, int offset, int pageSize);

    /** 管理端统计总数 */
    int countForAdmin(String claimStatus);

    /**
     * 管理端直接修改领奖状态(覆盖式)
     *
     * @param claimStatus     目标状态:NOT_CLAIMED / CLAIMED / SHIPPED / COMPLETED
     * @param shippingCompany 承运公司(目标状态为 SHIPPED 时必填)
     * @param shippingNo      物流单号(目标状态为 SHIPPED 时必填)
     */
    boolean adminUpdateStatus(Long recordId, String claimStatus, String shippingCompany, String shippingNo);
}
