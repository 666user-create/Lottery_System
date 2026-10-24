package org.example.lottery_system.controller.result;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/*
* 单个活动统计详情返回结果
* */
@Data
public class ActivityStatResult implements Serializable {
    //活动id
    private Long activityId;
    //活动名称
    private String activityName;
    //活动状态描述
    private String statusMessage;
    //参与人数
    private Integer participantTotal;
    //奖品总份数
    private Long prizeAmountTotal;
    //已中奖份数
    private Integer drawnTotal;
    //各奖品中奖明细
    private List<PrizeStat> prizeStats;

    @Data
    public static class PrizeStat implements Serializable {
        //奖品id
        private Long prizeId;
        //奖品名称
        private String prizeName;
        //奖品等级名称
        private String prizeTierName;
        //总份数
        private Long prizeAmount;
        //已中奖份数
        private Integer drawn;
    }
}
