package org.example.lottery_system.dao.dataobject;

import lombok.Data;

/*
* 单个活动下各奖品的中奖情况
* 说明：这不是一张数据表，而是连表聚合查询的接收对象
* */
@Data
public class ActivityPrizeStatDO {
    //奖品id
    private Long prizeId;
    //奖品名称
    private String prizeName;
    //奖品等级（库中存的是枚举名，如 FIRST_PRIZE）
    private String prizeTier;
    //该奖品在本活动中的份数
    private Long prizeAmount;
    //该奖品在本活动中已中奖的份数
    private Integer drawn;
}
