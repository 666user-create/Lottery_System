package org.example.lottery_system.service.impl;

import org.example.lottery_system.common.errorcode.ServiceErrorCodeConstants;
import org.example.lottery_system.common.exception.ServiceException;
import org.example.lottery_system.dao.dataobject.Encrypt;
import org.example.lottery_system.dao.dataobject.HumanHandoverDO;
import org.example.lottery_system.dao.dataobject.UserDO;
import org.example.lottery_system.dao.dataobject.WinningRecordDO;
import org.example.lottery_system.dao.mapper.HumanHandoverMapper;
import org.example.lottery_system.dao.mapper.UserMapper;
import org.example.lottery_system.dao.mapper.WinningRecordMapper;
import org.example.lottery_system.service.ClaimService;
import org.example.lottery_system.service.QQEmailService;
import org.example.lottery_system.service.enums.WinningClaimStatusEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;

/**
 * 中奖履约实现:领奖 → 发货 → 签收
 *
 * 一致性设计:
 *   1. 状态迁移全部走"带原状态条件的 UPDATE"(claimIfNotClaimed / shipIfClaimed / completeIfShipped /
 *      revertClaimIfClaimed),靠影响行数判断成功与否 —— 天然防重复提交与并发。
 *   2. 建工单失败时回滚状态,避免"状态已改成已申请但没有工单"的悬挂态。
 *   3. 通知失败只记日志,绝不回滚发货状态(通知是附带动作,不是业务前置条件)。
 */
@Service
public class ClaimServiceImpl implements ClaimService {

    private static final Logger log = LoggerFactory.getLogger(ClaimServiceImpl.class);

    @Autowired private WinningRecordMapper winningRecordMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private HumanHandoverMapper handoverMapper;
    @Autowired private QQEmailService qqEmailService;
    @Autowired private SmsBaoServiceImpl smsBaoService;

    @Override
    public Long claim(Long userId, Long recordId, String receiverName,
                      String receiverPhone, String receiverAddress) {
        WinningRecordDO record = requireOwnedRecord(userId, recordId);

        // 1. 收货信息:本次提交优先,未提交则取用户已保存的默认地址
        String name = trimToNull(receiverName);
        String phone = trimToNull(receiverPhone);
        String address = trimToNull(receiverAddress);
        if (name != null && phone != null && address != null) {
            // 本次填全了,顺手存成默认地址,下次不用重复填
            saveReceiverInfo(userId, name, phone, address);
        } else {
            UserDO user = userMapper.selectById(userId);
            if (user != null) {
                if (name == null) {
                    name = trimToNull(user.getReceiverName());
                }
                if (phone == null && user.getReceiverPhone() != null) {
                    phone = trimToNull(user.getReceiverPhone().getValue());
                }
                if (address == null) {
                    address = trimToNull(user.getReceiverAddress());
                }
            }
        }
        if (name == null || phone == null || address == null) {
            throw new ServiceException(ServiceErrorCodeConstants.RECEIVER_INFO_INCOMPLETE);
        }

        // 2. 乐观锁推进状态:未领取 → 已申请
        int updated = winningRecordMapper.claimIfNotClaimed(recordId);
        if (updated == 0) {
            throw new ServiceException(ServiceErrorCodeConstants.CLAIM_DUPLICATE);
        }

        // 3. 生成领奖工单(复用转人工工单体系)
        HumanHandoverDO ticket = new HumanHandoverDO();
        ticket.setUserId(userId);
        ticket.setSessionId("claim-" + recordId);
        ticket.setType(HumanHandoverDO.TYPE_CLAIM);
        ticket.setRefId(recordId);
        ticket.setStatus("PENDING");
        ticket.setQuestion(String.format("领奖申请 | 活动:%s | 奖品:%s(%s) | 收货信息:%s %s %s",
                record.getActivityName(), record.getPrizeName(), record.getPrizeTier(),
                name, phone, address));
        try {
            handoverMapper.insert(ticket);
        } catch (Exception e) {
            // 工单没建成功就把状态回退,避免出现"已申请但无工单"的悬挂态
            winningRecordMapper.revertClaimIfClaimed(recordId);
            log.error("领奖工单创建失败,已回退中奖记录[{}]状态", recordId, e);
            throw new ServiceException(ServiceErrorCodeConstants.CLAIM_STATUS_INVALID);
        }
        log.info("用户[{}]申请领奖成功: 中奖记录[{}] 工单[{}]", userId, recordId, ticket.getId());
        return ticket.getId();
    }

    @Override
    public boolean confirmReceived(Long userId, Long recordId) {
        requireOwnedRecord(userId, recordId);
        int updated = winningRecordMapper.completeIfShipped(recordId);
        if (updated == 0) {
            throw new ServiceException(ServiceErrorCodeConstants.CLAIM_NOT_SHIPPED);
        }
        log.info("用户[{}]确认收货: 中奖记录[{}]", userId, recordId);
        return true;
    }

    @Override
    public boolean shipByTicket(Long ticketId, String shippingCompany, String shippingNo,
                                String handler, String remark) {
        HumanHandoverDO ticket = handoverMapper.selectById(ticketId);
        if (ticket == null || !HumanHandoverDO.TYPE_CLAIM.equals(ticket.getType()) || ticket.getRefId() == null) {
            throw new ServiceException(ServiceErrorCodeConstants.CLAIM_TICKET_NOT_EXIST);
        }
        if (!StringUtils.hasText(shippingCompany) || !StringUtils.hasText(shippingNo)) {
            throw new ServiceException(ServiceErrorCodeConstants.SHIPPING_INFO_EMPTY);
        }

        // 已申请待发货 → 已发货
        int updated = winningRecordMapper.shipIfClaimed(ticket.getRefId(),
                shippingCompany.trim(), shippingNo.trim());
        if (updated == 0) {
            throw new ServiceException(ServiceErrorCodeConstants.CLAIM_STATUS_INVALID);
        }

        // 工单标为已解决,备注里带上物流信息留痕
        HumanHandoverDO update = new HumanHandoverDO();
        update.setId(ticketId);
        update.setStatus("RESOLVED");
        update.setHandler(handler);
        String shippingText = "承运:" + shippingCompany.trim() + " 单号:" + shippingNo.trim();
        update.setRemark(StringUtils.hasText(remark) ? remark.trim() + " | " + shippingText : shippingText);
        handoverMapper.updateStatus(update);

        notifyShipped(ticket.getRefId(), shippingCompany.trim(), shippingNo.trim());
        log.info("工单[{}]登记发货: 中奖记录[{}] {} 单号 {}", ticketId, ticket.getRefId(), shippingCompany, shippingNo);
        return true;
    }

    @Override
    public boolean cancelTicket(Long ticketId, String handler, String remark) {
        HumanHandoverDO ticket = handoverMapper.selectById(ticketId);
        if (ticket == null || !HumanHandoverDO.TYPE_CLAIM.equals(ticket.getType())) {
            throw new ServiceException(ServiceErrorCodeConstants.CLAIM_TICKET_NOT_EXIST);
        }
        if (ticket.getRefId() != null) {
            // 只有"已申请待发货"能回退;已发货的记录不会被改回(条件不满足,影响 0 行)
            winningRecordMapper.revertClaimIfClaimed(ticket.getRefId());
        }
        HumanHandoverDO update = new HumanHandoverDO();
        update.setId(ticketId);
        update.setStatus("CLOSED");
        update.setHandler(handler);
        update.setRemark(StringUtils.hasText(remark) ? remark.trim() : "取消领奖申请");
        handoverMapper.updateStatus(update);
        log.info("工单[{}]已取消领奖,中奖记录[{}]状态回退为未领取", ticketId, ticket.getRefId());
        return true;
    }

    @Override
    public boolean deleteTicket(Long ticketId) {
        HumanHandoverDO ticket = handoverMapper.selectById(ticketId);
        if (ticket == null) {
            throw new ServiceException(ServiceErrorCodeConstants.CLAIM_TICKET_NOT_EXIST);
        }
        // 领奖工单:若奖品还没发货,删除工单时把中奖记录回退为「未领取」,避免悬挂态;
        // 已发货/已签收的不动 —— 删工单不应撤销已完成的履约。
        if (HumanHandoverDO.TYPE_CLAIM.equals(ticket.getType()) && ticket.getRefId() != null) {
            winningRecordMapper.revertClaimIfClaimed(ticket.getRefId());
        }
        handoverMapper.deleteById(ticketId);
        log.info("工单[{}](类型 {})已删除", ticketId, ticket.getType());
        return true;
    }

    @Override
    public void saveReceiverInfo(Long userId, String receiverName, String receiverPhone, String receiverAddress) {
        UserDO user = new UserDO();
        user.setId(userId);
        user.setReceiverName(trimToNull(receiverName));
        user.setReceiverPhone(new Encrypt(trimToNull(receiverPhone)));
        user.setReceiverAddress(trimToNull(receiverAddress));
        userMapper.updateReceiverInfo(user);
    }

    @Override
    public UserDO getReceiverInfo(Long userId) {
        return userMapper.selectById(userId);
    }

    // ==================== 管理端:中奖履约管理 ====================

    @Override
    public java.util.List<WinningRecordDO> listForAdmin(String claimStatus, int offset, int pageSize) {
        // 状态非法时按"全部"处理,避免前端传错值查不出数据
        String status = WinningClaimStatusEnum.forName(claimStatus) == null ? null : claimStatus;
        return winningRecordMapper.selectPageByClaimStatus(status, offset, pageSize);
    }

    @Override
    public int countForAdmin(String claimStatus) {
        String status = WinningClaimStatusEnum.forName(claimStatus) == null ? null : claimStatus;
        return winningRecordMapper.countByClaimStatus(status);
    }

    @Override
    public boolean adminUpdateStatus(Long recordId, String claimStatus, String shippingCompany, String shippingNo) {
        if (recordId == null || WinningClaimStatusEnum.forName(claimStatus) == null) {
            throw new ServiceException(ServiceErrorCodeConstants.CLAIM_STATUS_INVALID);
        }
        if (WinningClaimStatusEnum.SHIPPED.name().equals(claimStatus)
                && (!StringUtils.hasText(shippingCompany) || !StringUtils.hasText(shippingNo))) {
            throw new ServiceException(ServiceErrorCodeConstants.SHIPPING_INFO_EMPTY);
        }
        WinningRecordDO current = winningRecordMapper.selectById(recordId);
        if (current == null) {
            throw new ServiceException(ServiceErrorCodeConstants.WINNING_RECORD_NOT_EXIST);
        }
        // 已签收 = 终态:不能再改状态或物流(幂等的"再签收一次"除外)。
        // 前端已把下拉换成只读徽标,这里再兜一层,防止直接调接口绕过。
        if (WinningClaimStatusEnum.COMPLETED.name().equals(current.getClaimStatus())
                && !WinningClaimStatusEnum.COMPLETED.name().equals(claimStatus)) {
            throw new ServiceException(ServiceErrorCodeConstants.CLAIM_FROZEN);
        }
        winningRecordMapper.adminUpdateClaimStatus(recordId, claimStatus,
                StringUtils.hasText(shippingCompany) ? shippingCompany.trim() : null,
                StringUtils.hasText(shippingNo) ? shippingNo.trim() : null);
        log.info("管理端修改领奖状态: 中奖记录[{}] → {}", recordId, claimStatus);

        // 管理端标记"已发货"时同样给用户发通知(与走工单发货保持一致)
        if (WinningClaimStatusEnum.SHIPPED.name().equals(claimStatus)) {
            notifyShipped(recordId, shippingCompany.trim(), shippingNo.trim());
        }
        return true;
    }

    // ==================== 内部方法 ====================

    /** 取中奖记录并校验归属(防越权操作他人中奖记录) */
    private WinningRecordDO requireOwnedRecord(Long userId, Long recordId) {
        if (recordId == null) {
            throw new ServiceException(ServiceErrorCodeConstants.WINNING_RECORD_NOT_EXIST);
        }
        WinningRecordDO record = winningRecordMapper.selectById(recordId);
        if (record == null) {
            throw new ServiceException(ServiceErrorCodeConstants.WINNING_RECORD_NOT_EXIST);
        }
        if (userId == null || !userId.equals(record.getWinnerId())) {
            throw new ServiceException(ServiceErrorCodeConstants.WINNING_RECORD_NOT_OWNED);
        }
        return record;
    }

    /** 发货通知:邮件 + 短信,失败只记日志(不影响发货结果) */
    private void notifyShipped(Long recordId, String shippingCompany, String shippingNo) {
        WinningRecordDO record = winningRecordMapper.selectById(recordId);
        if (record == null) {
            return;
        }
        String content = String.format("好消息!您在「%s」活动中获得的奖品「%s」已发货,承运:%s,单号:%s,请注意查收。",
                record.getActivityName(), record.getPrizeName(), shippingCompany, shippingNo);
        try {
            qqEmailService.sendSimpleMail(record.getWinnerEmail(), "【抽奖系统】您的奖品已发货", content);
        } catch (Exception e) {
            log.warn("发货邮件通知失败(不影响发货): {}", e.getMessage());
        }
        try {
            if (record.getWinnerPhoneNumber() != null) {
                smsBaoService.sendCustomNotification(record.getWinnerPhoneNumber().getValue(), content);
            }
        } catch (Exception e) {
            log.warn("发货短信通知失败(不影响发货): {}", e.getMessage());
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
