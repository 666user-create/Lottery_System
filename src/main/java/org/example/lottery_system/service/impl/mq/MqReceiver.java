package org.example.lottery_system.service.impl.mq;

import org.example.lottery_system.common.config.DirectRabbitConfig;
import org.example.lottery_system.common.exception.ServiceException;
import org.example.lottery_system.common.utils.JacksonUtil;
import org.example.lottery_system.controller.param.DrawPrizeParam;
import org.example.lottery_system.dao.dataobject.ActivityPrizeDO;
import org.example.lottery_system.dao.dataobject.WinningRecordDO;
import org.example.lottery_system.dao.mapper.ActivityPrizeMapper;
import org.example.lottery_system.dao.mapper.WinningRecordMapper;
import org.example.lottery_system.service.dto.WinnerRecordDTO;
import org.example.lottery_system.service.DrawPrizeService;
import org.example.lottery_system.service.QQEmailService;
import org.example.lottery_system.service.SMSService;
import org.example.lottery_system.service.activitystatus.ActivityStatusManager;
import org.example.lottery_system.service.dto.ConvertActivityStatusDTO;
import org.example.lottery_system.service.enums.ActivityPrizeStatusEnum;
import org.example.lottery_system.service.enums.ActivityStatusEnum;
import org.example.lottery_system.service.enums.ActivityUserStatusEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.example.lottery_system.common.config.DirectRabbitConfig.QUEUE_NAME;

@Component
@RabbitListener(queues = QUEUE_NAME)
public class MqReceiver {
    @Autowired
    private DrawPrizeService drawPrizeService;
    @Autowired
    private ActivityStatusManager activityStatusManager;
    @Autowired
    private QQEmailService qqEmailService;
    @Autowired
    private SMSService smsService;
    @Autowired
    private ActivityPrizeMapper activityPrizeMapper;
    @Autowired
    private WinningRecordMapper winningRecordMapper;
    private static final Logger logger = LoggerFactory.getLogger(MqReceiver.class);

    @RabbitHandler
    public void process(Map<String, String> message) {
        logger.info("mq消息成功接收:{}", JacksonUtil.writeValueAsString(message));

        // ===== 第 1 段：只做消息解析，消息本身坏了直接放弃（重试无意义）=====
        DrawPrizeParam param;
        try {
            param = JacksonUtil.readValue(message.get("messageData"), DrawPrizeParam.class);
        } catch (Exception e) {
            logger.error("mq消息处理异常: 消息读取失败", e);
            return;
        }

        // ===== 第 2 段：核心流程（校验 + 状态扭转 + 落中奖名单），只有这一段失败才回滚 =====
        List<WinningRecordDO> winningRecordDOList;
        try {
            if (!drawPrizeService.checkDrawPrizeParam(param)) {
                logger.warn("抽奖请求校验未通过:{}", JacksonUtil.writeValueAsString(param));
                return;
            }
            statusConvert(param);
            winningRecordDOList = drawPrizeService.saveWinnerRecords(param);
        } catch (ServiceException e) {
            logger.error("mq消息处理异常:{} :{}", e.getCode(), e.getMessage(), e);
            safeRollback(param);
            return;
        } catch (Exception e) {
            logger.error("mq消息处理异常", e);
            safeRollback(param);
            return;
        }

        // ===== 第 3 段：通知属于附带动作，失败绝不能影响开奖结果 =====
        notifyWinnersQuietly(winningRecordDOList);
    }

    /**
     * 通知中奖者（邮件 + 短信），逐条兜底。
     *
     * <p>为什么不能把通知放进第 2 段的 try：{@code @Async} 的提交失败（如线程池已关闭、
     * 或拒绝策略被改回抛异常的那种）会一路抛到 MQ 消费线程；若与核心流程共用一个 catch，
     * 就会把「短信没发出去」升级成「回滚开奖、删除中奖记录」。
     * 通知是可降级的附带动作，因此这里全部吞掉，只记 WARN。
     *
     * <p>注意当前拒绝策略是 CallerRunsPolicy（见 ExecutorConfig）：
     * 池满时不会抛异常，而是由本消费线程顺带执行短信（当前短信宝为 Mock 化通道、
     * 失败快，几十条通知的额外耗时可接受）。因此下面的 catch 属防御性代码，
     * 主要用于兜住「池已关闭」等场景。
     */
    private void notifyWinnersQuietly(List<WinningRecordDO> winningRecordDOList) {
        if (winningRecordDOList == null || winningRecordDOList.isEmpty()) {
            return;
        }
        List<WinnerRecordDTO> winnerRecordDTOList = winningRecordDOList.stream().map(doItem -> {
            WinnerRecordDTO dto = new WinnerRecordDTO();
            dto.setWinnerName(doItem.getWinnerName());
            dto.setWinnerEmail(doItem.getWinnerEmail());
            dto.setActivityName(doItem.getActivityName());
            dto.setPrizeTier(doItem.getPrizeTier());
            dto.setPrizeName(doItem.getPrizeName());
            if (doItem.getWinnerPhoneNumber() != null) {
                Map<String, String> phoneMap = new HashMap<>();
                phoneMap.put("value", doItem.getWinnerPhoneNumber().getValue());
                dto.setWinnerPhoneNumber(phoneMap);
            }
            return dto;
        }).collect(Collectors.toList());

        try {
            qqEmailService.notifyWinners(winnerRecordDTOList);
        } catch (Exception e) {
            logger.warn("中奖邮件通知失败(不影响开奖结果): {}", e.getMessage());
        }
        for (WinnerRecordDTO winner : winnerRecordDTOList) {
            try {
                smsService.sendSmsAsync(winner);
            } catch (Exception e) {
                // 防御性兜底：CallerRunsPolicy 不抛异常，此处主要覆盖「池已关闭」等提交失败场景
                logger.warn("中奖短信提交失败(不影响开奖结果) user={}: {}",
                        winner.getWinnerName(), e.getMessage());
            }
        }
    }

    /** 回滚本身再失败就必须人工介入，单独兜底，避免把原始异常冲掉 */
    private void safeRollback(DrawPrizeParam param) {
        try {
            rollback(param);
        } catch (Exception e) {
            logger.error("回滚失败，需人工核查: activityId={}, prizeId={}",
                    param.getActivityId(), param.getPrizeId(), e);
        }
    }

    private void rollback(DrawPrizeParam param) {
        //回滚活动状态:恢复处理请求之前的状态
        //1.回滚状态:活动,奖品,人员
        //判断状态是否需要回滚
        //(1)不需要
        if(!statusNeedRollback(param)){
            return;
        }
        //(2)需要
        rollbackStatus(param);
        //2.回滚中奖名单
        //(1)不需要
        if (!winnerNeedRollback(param)){
            return;
        }
        //(2)需要
        rollbackWinner(param);
    }

    private void rollbackWinner(DrawPrizeParam param) {
        //回滚中奖名单
        drawPrizeService.deleteWinnerRecords(param.getActivityId(), param.getPrizeId());
    }

    private boolean winnerNeedRollback(DrawPrizeParam param) {
        //判断是否存在中奖者
        int count = winningRecordMapper.count(param.getActivityId(),param.getPrizeId());
        return count > 0;
    }

    private void rollbackStatus(DrawPrizeParam param) {
        //回滚活动状态
        ConvertActivityStatusDTO convertActivityStatusDTO = new ConvertActivityStatusDTO();
        convertActivityStatusDTO.setActivityId(param.getActivityId());
        convertActivityStatusDTO.setTargetActivityStatus(ActivityStatusEnum.RUNNING);
        convertActivityStatusDTO.setPrizeId(param.getPrizeId());
        convertActivityStatusDTO.setTargetPrizeStatus(ActivityPrizeStatusEnum.INIT);
        convertActivityStatusDTO.setUserIds(param.getWinnerList().stream().map(DrawPrizeParam.Winner::getUserId).collect(Collectors.toList()));
        convertActivityStatusDTO.setTargetUserStatus(ActivityUserStatusEnum.RUNNING);
        activityStatusManager.rollbackHandleEvent(convertActivityStatusDTO);
    }

    private boolean statusNeedRollback(DrawPrizeParam param) {
        //判断活动状态是否需要回滚
        //扭转状态时保证了状态一致性,不需要回滚(不包含活动)
        //只要判断人员/奖品是否扭转过,就能判断是否需要回滚
        ActivityPrizeDO activityPrizeDO = activityPrizeMapper.selectByAPId(param.getActivityId(), param.getPrizeId());
        if (activityPrizeDO .getStatus().equalsIgnoreCase(ActivityPrizeStatusEnum.COMPLETED.name())) {
            return true;
        }
        return false;
    }

    //状态扭转处理
    private void statusConvert(DrawPrizeParam param) {
        //活动状态,奖品状态,人员状态
        //扭转奖品状态
        //扭转人员状态
        //判断活动是否执行完成,执行完成再扭转活动状态
        //如上设计
        //活动有依赖性,维护性差
        //状态扭转环境可能会扩展,需要考虑扩展性
        //灵活性差
        //解决:设计模式(责任链模式,策略模式)
        ConvertActivityStatusDTO convertActivityStatusDTO = new ConvertActivityStatusDTO();
        convertActivityStatusDTO.setActivityId(param.getActivityId());
        convertActivityStatusDTO.setTargetActivityStatus(ActivityStatusEnum.COMPLETED);
        convertActivityStatusDTO.setPrizeId(param.getPrizeId());
        convertActivityStatusDTO.setTargetPrizeStatus(ActivityPrizeStatusEnum.COMPLETED);
        convertActivityStatusDTO.setUserIds(param.getWinnerList().stream().map(DrawPrizeParam.Winner::getUserId).collect(Collectors.toList()));
        convertActivityStatusDTO.setTargetUserStatus(ActivityUserStatusEnum.COMPLETED);
        activityStatusManager.handlerEvent(convertActivityStatusDTO);
    }
}
