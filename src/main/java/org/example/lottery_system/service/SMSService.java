package org.example.lottery_system.service;

import org.example.lottery_system.service.dto.WinnerRecordDTO;
import java.util.List;

public interface SMSService {
    /**
     * 批量发送中奖通知短信 (异步并发)
     * @param winnerList 中奖者列表
     */
    void notifyWinnersAsync(List<WinnerRecordDTO> winnerList);

    /**
     * 发送单条中奖通知短信 (异步)
     * @param winner 单个中奖者
     */
    void sendSmsAsync(WinnerRecordDTO winner);
}
