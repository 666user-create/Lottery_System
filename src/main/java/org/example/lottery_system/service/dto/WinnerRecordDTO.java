package org.example.lottery_system.service.dto;

import lombok.Data;
import java.util.Map;

@Data
public class WinnerRecordDTO {
    // 中奖者姓名
    private String winnerName;
    // 中奖者邮箱
    private String winnerEmail;
    // 活动名称
    private String activityName;
    // 奖项等级 (如 FIRST_PRIZE, SECOND_PRIZE)
    private String prizeTier;
    // 奖品名称
    private String prizeName;
    // 中奖者手机号 (包含 "value" key)
    private Map<String, String> winnerPhoneNumber;
}
