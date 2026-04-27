package org.example.lottery_system.controller;

import org.example.lottery_system.common.errorcode.ControllerErrorCodeConstants;
import org.example.lottery_system.common.exception.ControllerException;
import org.example.lottery_system.common.pojo.CommonResult;
import org.example.lottery_system.common.utils.JacksonUtil;
import org.example.lottery_system.controller.param.CreateActivityParam;
import org.example.lottery_system.controller.param.PageParam;
import org.example.lottery_system.controller.result.CreateActivityResult;
import org.example.lottery_system.controller.result.FindActivityListResult;
import org.example.lottery_system.controller.result.FindPrizeListResult;
import org.example.lottery_system.controller.result.GetActivityDetailResult;
import org.example.lottery_system.service.ActivityService;
import org.example.lottery_system.service.dto.ActivityDTO;
import org.example.lottery_system.service.dto.ActivityDetailDTO;
import org.example.lottery_system.service.dto.CreateActivityDTO;
import org.example.lottery_system.service.dto.PageListDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/activity")
public class ActivityController {
    private static Logger logger = LoggerFactory.getLogger(ActivityController.class);
    @Autowired
    private ActivityService activityService;

    @PostMapping("/create")
    public CommonResult<CreateActivityResult> createActivity(
            @Validated @RequestBody CreateActivityParam param) {
        logger.info("createActivity: {}",
                JacksonUtil.writeValueAsString(param));
        return CommonResult.success(
                result(activityService.createActivity(param)));
    }

    @GetMapping("/find-list")
    public CommonResult<FindActivityListResult> findActivityList(PageParam param) {
        logger.info("findActivityList: {}",
                JacksonUtil.writeValueAsString(param));
        return CommonResult.success(convertToFindActivityListResult(activityService.findActivityList(param)));
    }

    @GetMapping("/detail/find")
    public CommonResult<GetActivityDetailResult> getActivityDetail(@RequestParam("activityId") Long activityId) {
        logger.info("getActivityDetail: {}", JacksonUtil.writeValueAsString(activityId));
        ActivityDetailDTO detailDTO = activityService.getActivityDetail(activityId);
        return CommonResult.success(convertToGetActivityDetailResult(detailDTO));
    }

    private GetActivityDetailResult convertToGetActivityDetailResult(ActivityDetailDTO detailDTO) {
        if (detailDTO == null) {
            throw new ControllerException(ControllerErrorCodeConstants.ACTIVITY_FIND_DETAIL_ERROR);
        }
        GetActivityDetailResult result = new GetActivityDetailResult();
        result.setActivityId(detailDTO.getActivityId());
        result.setActivityName(detailDTO.getActivityName());
        result.setDesc(detailDTO.getDesc());
        result.setStatus(detailDTO.valid());
        result.setPrizes(
                detailDTO.getPrizeDTOList().stream()
                        .sorted(Comparator.comparingInt(prizeDTO -> prizeDTO.getTiers().getCode()))
                        .map(prizeDTO -> {
                            GetActivityDetailResult.Prize prize = new GetActivityDetailResult.Prize();
                            prize.setPrizeId(prizeDTO.getPrizeId());
                            prize.setName(prizeDTO.getName());
                            prize.setImageUrl(prizeDTO.getImageUrl());
                            prize.setPrice(prizeDTO.getPrice());
                            prize.setDescription(prizeDTO.getDescription());
                            prize.setTiers(prizeDTO.getTiers().getMessage());
                            prize.setPrizeAmount(prizeDTO.getPrizeAmount());
                            prize.setValid(prizeDTO.valid());
                            return prize;
                        }).collect(Collectors.toList()));
        result.setUsers(
                detailDTO.getUserDTOList().stream()
                        .map(userDTO -> {
                            GetActivityDetailResult.User user = new GetActivityDetailResult.User();
                            user.setUserId(userDTO.getUserId());
                            user.setName(userDTO.getName());
                            user.setValid(userDTO.valid());
                            return user;
                        }).collect(Collectors.toList()));
        return result;
    }

    private FindActivityListResult convertToFindActivityListResult(PageListDTO<ActivityDTO> activityList) {
        if (activityList == null) {
            throw new ControllerException(ControllerErrorCodeConstants.ACTIVITY_FIND_ERROR);
        }
        FindActivityListResult result = new FindActivityListResult();
        result.setTotal(activityList.getTotal());
        result.setRecords(activityList.getRecords().stream()
                .map(activityDTO -> {
                    FindActivityListResult.ActivityInfo activityInfo = new FindActivityListResult.ActivityInfo();
                    activityInfo.setActivityId(activityDTO.getActivityId());
                    activityInfo.setActivityName(activityDTO.getActivityName());
                    activityInfo.setDescription(activityDTO.getDescription());
                    activityInfo.setValid(activityDTO.isValid());
                    return activityInfo;
                })
                .collect(Collectors.toList()));
        return result;
    }

    private CreateActivityResult result(CreateActivityDTO activity) {
        if (activity == null) {
            throw new ControllerException(ControllerErrorCodeConstants.ACTIVITY_CREATE_ERROR);
        }
        CreateActivityResult result = new CreateActivityResult();
        result.setActivityId(activity.getActivityId());
        return result;
    }
}
