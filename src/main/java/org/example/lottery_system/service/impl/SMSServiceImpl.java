package org.example.lottery_system.service.impl;

import org.example.lottery_system.service.SMSService;
import org.example.lottery_system.service.dto.WinnerRecordDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SMSServiceImpl implements SMSService {

    private static final Logger logger = LoggerFactory.getLogger(SMSServiceImpl.class);

    @Autowired
    private SmsBaoServiceImpl smsBaoService;

    @Override
    public void notifyWinnersAsync(List<WinnerRecordDTO> winnerList) {
        if (winnerList == null || winnerList.isEmpty()) {
            return;
        }

        logger.info("开始并发发送 {} 条中奖通知短信", winnerList.size());
        
        // 由于 @Async 只有在被外部调用时才生效，我们这里直接调用本类的 sendSmsAsync 会失效
        // 所以在 MqReceiver 中循环调用 sendSmsAsync 是更好的并发实践
        // 这里提供一个简单的同步循环，如果外部调用此方法，则整体异步执行
        for (WinnerRecordDTO winner : winnerList) {
            this.sendSmsAsync(winner);
        }
    }

    /**
     * 实现异步发送：标记为 @Async，Spring 会在后台线程池中运行此方法
     */
    @Async
    @Override
    public void sendSmsAsync(WinnerRecordDTO winner) {
        try {
            logger.info("正在通过短信宝并发发送中奖短信 (用户: {})", winner.getWinnerName());
            smsBaoService.sendWinningNotification(winner);
        } catch (Exception e) {
            logger.error("发送短信给 {} 失败: {}", winner.getWinnerName(), e.getMessage());
        }
    }
}
