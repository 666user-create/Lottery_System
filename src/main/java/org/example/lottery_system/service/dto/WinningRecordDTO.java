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
    //中奖记录id(领奖功能要用它定位记录)
    private Long recordId;
    //活动名称(界面上区分是哪个活动中的奖)
    private String activityName;
    //领奖状态:NOT_CLAIMED/CLAIMED/SHIPPED/COMPLETED
    private String claimStatus;
    //领奖状态中文文案
    private String claimStatusText;
    //承运公司
    private String shippingCompany;
    //物流单号
    private String shippingNo;
}
