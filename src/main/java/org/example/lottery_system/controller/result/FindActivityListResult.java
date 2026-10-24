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
    //进行中活动总数（全量统计，不受分页与 status 筛选影响）
    //用于用户大厅顶部统计卡；此前前端只能对当前页 records 数 valid，
    //导致活动超过一页时统计数字与真实总数不符
    private Integer runningCount;
    //已结束活动总数
    private Integer completedCount;

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
