package org.example.lottery_system.service.dto;

import lombok.Data;
import org.example.lottery_system.service.enums.ActivityStatusEnum;

import java.io.Serializable;

@Data
public class ActivityDTO{
    //活动id
    private Long activityId;
    //活动名称
    private String activityName;
    //活动描述
    private String description;
    //活动状态
    private ActivityStatusEnum status;
    //活动是否有效
    public boolean isValid() {
        return status.equals(ActivityStatusEnum.RUNNING);
    }
}
