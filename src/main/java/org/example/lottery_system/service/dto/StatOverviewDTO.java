package org.example.lottery_system.service.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/*
* 全局数据统计总览
* */
@Data
public class StatOverviewDTO implements Serializable {
    //活动总数
    private Integer activityTotal;
    //进行中活动数
    private Integer activityRunning;
    //已完成活动数
    private Integer activityCompleted;
    //已开奖活动数
    private Integer activityDrawn;
    //奖品总数
    private Integer prizeTotal;
    //注册用户数（普通用户）
    private Integer userTotal;
    //累计中奖人次
    private Integer winningTotal;
    //各奖品等级中奖分布
    private List<TierStat> tierStats;

    @Data
    public static class TierStat implements Serializable {
        //等级编码，用于排序
        private Integer tierCode;
        //等级名称，如“一等奖品”
        private String tierName;
        //该等级中奖数量
        private Integer count;
    }
}
