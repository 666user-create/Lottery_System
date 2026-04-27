package org.example.lottery_system.controller.result;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class FindActivityListResult implements Serializable {
    //总量
    private Integer total;
    //当前列表
    private List<ActivityInfo> records;

    @Data
    public static class ActivityInfo implements Serializable {
        //活动id
        private Long activityId;
        //活动名称
        private String activityName;
        //活动描述
        private String description;
        //活动是否有效
        private Boolean valid;
    }
}
