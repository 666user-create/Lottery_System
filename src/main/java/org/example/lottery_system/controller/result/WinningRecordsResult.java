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
    //中奖记录id(领奖时用它定位记录)
    private Long recordId;
    //活动名称
    private String activityName;
    //领奖状态:NOT_CLAIMED未领取/CLAIMED已申请待发货/SHIPPED已发货/COMPLETED已签收
    private String claimStatus;
    //领奖状态中文文案
    private String claimStatusText;
    //承运公司(已发货时有值)
    private String shippingCompany;
    //物流单号(已发货时有值)
    private String shippingNo;
}
