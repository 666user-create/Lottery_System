package org.example.lottery_system.service;

import org.example.lottery_system.service.dto.WinnerRecordDTO;
import java.util.List;

public interface QQEmailService {
    // 发送简单邮件
    // @param to 收件人邮箱
    // @param subject 邮件主题
    // @param content 邮件内容
    void sendSimpleMail(String to, String subject, String content);

    /**
     * 批量发送中奖通知邮件
     * @param winnerList 中奖者列表
     */
    void notifyWinners(List<WinnerRecordDTO> winnerList);
}
