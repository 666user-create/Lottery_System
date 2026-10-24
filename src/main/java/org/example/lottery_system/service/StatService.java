package org.example.lottery_system.service;

import org.example.lottery_system.service.dto.ActivityStatDTO;
import org.example.lottery_system.service.dto.StatOverviewDTO;

public interface StatService {
    /*
     * 查询全局数据总览
     * */
    StatOverviewDTO getOverview();

    /*
     * 查询指定活动的统计详情
     * 活动不存在时返回 null，由 Controller 层抛出 ControllerException
     * */
    ActivityStatDTO getActivityStat(Long activityId);
}
