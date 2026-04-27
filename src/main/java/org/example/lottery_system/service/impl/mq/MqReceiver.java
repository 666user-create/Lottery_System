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
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
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
    private ThreadPoolTaskExecutor asyncServiceExecutor;
    @Autowired
    private ActivityPrizeMapper activityPrizeMapper;
    @Autowired
    private WinningRecordMapper winningRecordMapper;
    private static final Logger logger = LoggerFactory.getLogger(MqReceiver.class);
    @RabbitHandler
    public void process(Map<String,String> message){
        //成功接受队列消息
        logger.info("mq消息成功接收:{}", JacksonUtil.writeValueAsString(message));
        
            try {
                String paramString = message.get("messageData");
                DrawPrizeParam param = JacksonUtil.readValue(paramString, DrawPrizeParam.class);
                //处理抽奖流程
                try {
                    //检验抽奖请求是否有效
                    if (!drawPrizeService.checkDrawPrizeParam(param)) {
                        logger.warn("抽奖请求校验未通过:{}", JacksonUtil.writeValueAsString(param));
                        return;
                    }
                    //状态扭转处理
                    statusConvert(param);
                    //保存中奖者名单
                    List<WinningRecordDO> winningRecordDOList = drawPrizeService.saveWinnerRecords(param);
                    //通知中奖者(邮箱)
                    if (winningRecordDOList != null && !winningRecordDOList.isEmpty()) {
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
                    //通知中奖者(邮箱)
                    qqEmailService.notifyWinners(winnerRecordDTOList);
                    //通知中奖者(短信) - 并发异步发送
                    for (WinnerRecordDTO winner : winnerRecordDTOList) {
                        smsService.sendSmsAsync(winner);
                    }
                }
            } catch (ServiceException e) {
                //发生异常,需要保证事务一致性(回滚),抛出异常
                logger.error("mq消息处理异常:{} :{}", e.getCode(), e.getMessage(), e);
                rollback(param);
            } catch (Exception e) {
                logger.error("mq消息处理异常", e);
                rollback(param);
            }
        } catch (Exception e) {
            logger.error("mq消息处理异常: 消息读取或处理失败", e);
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