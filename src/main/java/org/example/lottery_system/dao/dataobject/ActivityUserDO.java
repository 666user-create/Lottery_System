package org.example.lottery_system.dao.dataobject;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper=true)
public class ActivityUserDO extends BaseDO {
    //活动id
    private Long activityId;
    //用户id
    private Long userId;
    //用户名
    private String userName;
    //用户状态
    private String status;
}
