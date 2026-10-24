package org.example.lottery_system.service;

import org.example.lottery_system.controller.param.CreateActivityParam;
import org.example.lottery_system.controller.param.PageParam;
import org.example.lottery_system.service.dto.ActivityDTO;
import org.example.lottery_system.service.dto.ActivityDetailDTO;
import org.example.lottery_system.service.dto.ActivityPageDTO;
import org.example.lottery_system.service.dto.CreateActivityDTO;

public interface ActivityService {
    // 创建活动
    CreateActivityDTO createActivity(CreateActivityParam param);
    //分页查询活动列表，status 为 null 时查全部
    ActivityPageDTO findActivityList(PageParam param, String status);
    //查询活动详情
    ActivityDetailDTO getActivityDetail(Long activityId);
    //缓存活动详情
    void cacheActivity(Long activityId);

    /* ============ 活动管理（管理端）============ */
    // 结束活动：置为 COMPLETED，并同步其奖品/人员的终态
    void endActivity(Long activityId);
    // 删除活动：物理删除主表及关联数据
    void deleteActivity(Long activityId);
}
