package org.example.lottery_system.service;

import org.example.lottery_system.controller.param.CreateActivityParam;
import org.example.lottery_system.controller.param.PageParam;
import org.example.lottery_system.service.dto.ActivityDTO;
import org.example.lottery_system.service.dto.ActivityDetailDTO;
import org.example.lottery_system.service.dto.CreateActivityDTO;
import org.example.lottery_system.service.dto.PageListDTO;

public interface ActivityService {
    // 创建活动
    CreateActivityDTO createActivity(CreateActivityParam param);
    //分页查询活动列表
    PageListDTO<ActivityDTO> findActivityList(PageParam param);
    //查询活动详情
    ActivityDetailDTO getActivityDetail(Long activityId);
    //缓存活动详情
    void cacheActivity(Long activityId);
}
