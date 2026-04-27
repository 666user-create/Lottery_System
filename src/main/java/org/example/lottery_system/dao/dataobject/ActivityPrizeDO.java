package org.example.lottery_system.dao.dataobject;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper=true)
public class ActivityPrizeDO extends BaseDO {
    //活动id
    private Long activityId;
    //奖品id
    private Long prizeId;
    //数量
    private Long prizeAmount;
    //等级
    private String prizeTier;
    //奖品状态
    private String status;
}
