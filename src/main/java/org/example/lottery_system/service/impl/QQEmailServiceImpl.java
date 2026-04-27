package org.example.lottery_system.service.impl;

import org.example.lottery_system.service.dto.WinnerRecordDTO;
import org.example.lottery_system.service.QQEmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class QQEmailServiceImpl implements QQEmailService {

    // Spring Boot 根据 properties 文件自动配置好的发送器
    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Override
    public void sendSimpleMail(String to, String subject, String content) {
        try {
            // 构建简单的文本邮件对象
            SimpleMailMessage message = new SimpleMailMessage();

            // 发件人（必须与配置文件里的 username 保持一致）
            message.setFrom(fromEmail);
            // 收件人
            message.setTo(to);
            // 邮件标题
            message.setSubject(subject);
            // 邮件正文
            message.setText(content);

            // 执行发送
            mailSender.send(message);
            System.out.println("纯文本邮件发送成功！已发送至：" + to);

        } catch (Exception e) {
            System.err.println("邮件发送失败，原因：" + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 批量发送中奖通知邮件
     * @param winnerList 你的项目返回的中奖者列表
     */
    @Override
    public void notifyWinners(List<WinnerRecordDTO> winnerList) {
        if (winnerList == null || winnerList.isEmpty()) {
            return;
        }

        // 遍历所有中奖者
        for (WinnerRecordDTO winner : winnerList) {
            // 如果没有邮箱，直接跳过
            if (winner.getWinnerEmail() == null || winner.getWinnerEmail().isEmpty()) {
                System.out.println("用户 " + winner.getWinnerName() + " 邮箱为空，跳过发送");
                continue;
            }

            // 1. 动态拼接邮件标题
            String subject = "【中奖通知】恭喜您在「" + winner.getActivityName() + "」活动中奖！";

            // 2. 动态拼接邮件正文（使用 String.format 填充占位符 %s）
            String content = String.format(
                    "亲爱的 %s：\n\n" +
                    "恭喜您！您在我们的「%s」活动中运气爆棚！\n" +
                    "您获得的奖品是：【%s】%s。\n\n" +
                    "请注意保持手机畅通（预留号码：%s），我们的工作人员会尽快联系您发放奖品。\n\n" +
                    "感谢您的参与！\n" +
                    "—— 活动系统自动发送",
                    winner.getWinnerName(),       // 填充中奖人
                    winner.getActivityName(),     // 填充活动名
                    formatPrizeTier(winner.getPrizeTier()), // 填充奖项等级(如二等奖)
                    winner.getPrizeName(),        // 填充奖品名
                    getPhoneValue(winner)         // 填充手机号
            );

            // 3. 调用本类的邮件发送方法
            try {
                sendSimpleMail(winner.getWinnerEmail(), subject, content);
                
                // 【⚠️重要避坑】如果是批量发送，千万不能发太快，否则会被QQ邮箱当做垃圾邮件屏蔽！
                // 每次发完暂停 1 秒钟
                Thread.sleep(1000);
            } catch (Exception e) {
                System.err.println("给 " + winner.getWinnerEmail() + " 发送邮件失败");
            }
        }
    }

    // ====== 辅助方法 ======

    // 翻译一下奖项等级，让邮件更好看
    private String formatPrizeTier(String tier) {
        if ("FIRST_PRIZE".equals(tier)) return "一等奖";
        if ("SECOND_PRIZE".equals(tier)) return "二等奖";
        if ("THIRD_PRIZE".equals(tier)) return "三等奖";
        return "特别奖";
    }

    // 安全提取手机号
    private String getPhoneValue(WinnerRecordDTO winner) {
        if (winner.getWinnerPhoneNumber() != null && winner.getWinnerPhoneNumber().containsKey("value")) {
            return winner.getWinnerPhoneNumber().get("value");
        }
        return "未留存";
    }
    
}