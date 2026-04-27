package org.example.lottery_system.dao.dataobject;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ActivityDO extends BaseDO{
    //活动名称
    private String activityName;
    //活动描述
    private String description;
    //活动状态
    private String status;
}
