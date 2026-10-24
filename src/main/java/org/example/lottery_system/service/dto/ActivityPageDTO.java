package org.example.lottery_system.service.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 活动分页查询结果。
 *
 * 与通用分页 DTO 分开：除分页数据外，还需返回「进行中/已结束」的全量计数，
 * 供用户大厅的统计卡直接展示，避免前端只对当前页数据计数导致数字失真。
 */
@Data
public class ActivityPageDTO implements Serializable {
    // 筛选后的总量（受 status 影响，用于分页器显示总页数）
    private Integer total;
    // 当前页记录
    private List<ActivityDTO> records;
    // 进行中活动总数（全量，不受分页与筛选影响）
    private Integer runningCount;
    // 已结束活动总数（全量，不受分页与筛选影响）
    private Integer completedCount;

    public ActivityPageDTO(Integer total, List<ActivityDTO> records,
                           Integer runningCount, Integer completedCount) {
        this.total = total;
        this.records = records;
        this.runningCount = runningCount;
        this.completedCount = completedCount;
    }
}