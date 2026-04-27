package org.example.lottery_system.dao.dataobject;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = true)
public class WinningRecordDO extends BaseDO{
    //活动id
    private Long activityId;
    //活动名称
    private String activityName;
    //奖品id
    private Long prizeId;
    //奖品名称
    private String prizeName;
    //奖品等级
    private String prizeTier;
    //中奖者id
    private Long winnerId;
    //中奖者姓名
    private String winnerName;
    //中奖者邮箱
    private String winnerEmail;
    //中奖者手机号
    private Encrypt winnerPhoneNumber;
    //中奖时间
    private Date winningTime;
}
