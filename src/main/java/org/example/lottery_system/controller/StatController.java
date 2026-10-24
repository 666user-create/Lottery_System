package org.example.lottery_system.controller;

import org.example.lottery_system.common.errorcode.ControllerErrorCodeConstants;
import org.example.lottery_system.common.exception.ControllerException;
import org.example.lottery_system.common.pojo.CommonResult;
import org.example.lottery_system.controller.result.ActivityStatResult;
import org.example.lottery_system.controller.result.StatOverviewResult;
import org.example.lottery_system.service.StatService;
import org.example.lottery_system.service.dto.ActivityStatDTO;
import org.example.lottery_system.service.dto.StatOverviewDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.stream.Collectors;

/*
* 数据统计接口
*
* 权限：/api/stat/** 已在 LoginInterceptor 中登记为 adminOnly，仅管理员可访问
* */
@RestController
@RequestMapping("/api/stat")
public class StatController {
    private static final Logger logger = LoggerFactory.getLogger(StatController.class);

    @Autowired
    private StatService statService;

    /*
     * 全局数据总览
     * */
    @GetMapping("/overview")
    public CommonResult<StatOverviewResult> overview() {
        logger.info("查询数据统计总览");
        StatOverviewDTO statOverviewDTO = statService.getOverview();
        return CommonResult.success(convertToStatOverviewResult(statOverviewDTO));
    }

    /*
     * 指定活动的统计详情
     * */
    @GetMapping("/activity")
    public CommonResult<ActivityStatResult> activityStat(
            @RequestParam("activityId") Long activityId) {
        logger.info("查询活动统计:{}", activityId);
        ActivityStatDTO activityStatDTO = statService.getActivityStat(activityId);
        if (activityStatDTO == null) {
            throw new ControllerException(ControllerErrorCodeConstants.ACTIVITY_FIND_DETAIL_ERROR);
        }
        return CommonResult.success(convertToActivityStatResult(activityStatDTO));
    }

    private StatOverviewResult convertToStatOverviewResult(StatOverviewDTO statOverviewDTO) {
        if (statOverviewDTO == null) {
            throw new ControllerException(ControllerErrorCodeConstants.ACTIVITY_FIND_ERROR);
        }
        StatOverviewResult result = new StatOverviewResult();
        result.setActivityTotal(statOverviewDTO.getActivityTotal());
        result.setActivityRunning(statOverviewDTO.getActivityRunning());
        result.setActivityCompleted(statOverviewDTO.getActivityCompleted());
        result.setActivityDrawn(statOverviewDTO.getActivityDrawn());
        result.setPrizeTotal(statOverviewDTO.getPrizeTotal());
        result.setUserTotal(statOverviewDTO.getUserTotal());
        result.setWinningTotal(statOverviewDTO.getWinningTotal());
        if (CollectionUtils.isEmpty(statOverviewDTO.getTierStats())) {
            result.setTierStats(Arrays.asList());
        } else {
            result.setTierStats(
                    statOverviewDTO.getTierStats().stream()
                            .map(tierStat -> {
                                StatOverviewResult.TierStat item = new StatOverviewResult.TierStat();
                                item.setTierName(tierStat.getTierName());
                                item.setCount(tierStat.getCount());
                                return item;
                            }).collect(Collectors.toList())
            );
        }
        return result;
    }

    private ActivityStatResult convertToActivityStatResult(ActivityStatDTO activityStatDTO) {
        ActivityStatResult result = new ActivityStatResult();
        result.setActivityId(activityStatDTO.getActivityId());
        result.setActivityName(activityStatDTO.getActivityName());
        result.setStatusMessage(activityStatDTO.getStatusMessage());
        result.setParticipantTotal(activityStatDTO.getParticipantTotal());
        result.setPrizeAmountTotal(activityStatDTO.getPrizeAmountTotal());
        result.setDrawnTotal(activityStatDTO.getDrawnTotal());
        if (CollectionUtils.isEmpty(activityStatDTO.getPrizeStats())) {
            result.setPrizeStats(Arrays.asList());
        } else {
            result.setPrizeStats(
                    activityStatDTO.getPrizeStats().stream()
                            .map(prizeStat -> {
                                ActivityStatResult.PrizeStat item = new ActivityStatResult.PrizeStat();
                                item.setPrizeId(prizeStat.getPrizeId());
                                item.setPrizeName(prizeStat.getPrizeName());
                                item.setPrizeTierName(prizeStat.getPrizeTierName());
                                item.setPrizeAmount(prizeStat.getPrizeAmount());
                                item.setDrawn(prizeStat.getDrawn());
                                return item;
                            }).collect(Collectors.toList())
            );
        }
        return result;
    }
}
