package org.example.lottery_system.service.dto;

import lombok.Data;
import org.example.lottery_system.service.enums.ActivityPrizeTiersEnum;

import java.io.Serializable;
import java.util.Date;

@Data
public class WinningRecordDTO implements Serializable {
    //获奖人id
    private Long winnerId;
    //获奖人姓名
    private String winnerName;
    //奖品名称
    private String prizeName;
    //获奖等级
    private ActivityPrizeTiersEnum prizeTier;
    //获奖时间
    private Date winningTime;
}
