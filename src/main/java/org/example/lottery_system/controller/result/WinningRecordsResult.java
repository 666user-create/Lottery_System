package org.example.lottery_system.controller.result;

import lombok.Data;

import java.util.Date;

@Data
public class WinningRecordsResult {
    //获奖人id
    private Long winnerId;
    //获奖人姓名
    private String winnerName;
    //奖品名称
    private String prizeName;
    //获奖等级
    private String prizeTier;
    //获奖时间
    private Date winningTime;
}
