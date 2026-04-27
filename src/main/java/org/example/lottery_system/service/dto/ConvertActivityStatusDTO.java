package org.example.lottery_system.service.dto;

import lombok.Data;
import org.example.lottery_system.service.enums.ActivityPrizeStatusEnum;
import org.example.lottery_system.service.enums.ActivityStatusEnum;
import org.example.lottery_system.service.enums.ActivityUserStatusEnum;

import java.util.List;

@Data
public class ConvertActivityStatusDTO {
    //活动id
    private Long activityId;
    //目标活动状态
    private ActivityStatusEnum targetActivityStatus;
    //奖品id
    private Long prizeId;
    //目标奖品状态
    private ActivityPrizeStatusEnum targetPrizeStatus;
    //目标用户id
    private List<Long> userIds;
    //目标用户状态
    private ActivityUserStatusEnum targetUserStatus;
}
