package org.example.lottery_system.service.impl;

import org.example.lottery_system.common.errorcode.ServiceErrorCodeConstants;
import org.example.lottery_system.common.exception.ServiceException;
import org.example.lottery_system.common.utils.JacksonUtil;
import org.example.lottery_system.common.utils.RedisUtil;
import org.example.lottery_system.controller.param.CreateActivityParam;
import org.example.lottery_system.controller.param.CreatePrizeByActivityParam;
import org.example.lottery_system.controller.param.CreateUserByActivityParam;
import org.example.lottery_system.controller.param.PageParam;
import org.example.lottery_system.dao.dataobject.ActivityDO;
import org.example.lottery_system.dao.dataobject.ActivityPrizeDO;
import org.example.lottery_system.dao.dataobject.ActivityUserDO;
import org.example.lottery_system.dao.dataobject.PrizeDO;
import org.example.lottery_system.dao.mapper.*;
import org.example.lottery_system.service.ActivityService;
import org.example.lottery_system.service.dto.ActivityDTO;
import org.example.lottery_system.service.dto.ActivityDetailDTO;
import org.example.lottery_system.service.dto.ActivityPageDTO;
import org.example.lottery_system.service.dto.CreateActivityDTO;
import org.example.lottery_system.service.enums.ActivityPrizeStatusEnum;
import org.example.lottery_system.service.enums.ActivityPrizeTiersEnum;
import org.example.lottery_system.service.enums.ActivityStatusEnum;
import org.example.lottery_system.service.enums.ActivityUserStatusEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class) // 事务回滚
public class ActivityServiceImpl implements ActivityService {
    private static final String ACTIVITY_PREFIX = "ACTIVITY_";
    private static final Long ACTIVITY_TIMEOUT = 60 * 60 * 24 * 7L;
    private static final Logger logger = LoggerFactory.getLogger(ActivityServiceImpl.class);
    @Autowired
    private ActivityMapper activityMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private PrizeMapper prizeMapper;
    @Autowired
    private ActivityPrizeMapper activityPrizeMapper;
    @Autowired
    private ActivityUserMapper activityUserMapper;
    @Autowired
    private WinningRecordMapper winningRecordMapper;
    @Autowired
    private RedisUtil redisUtil;

    @Override
    public CreateActivityDTO createActivity(CreateActivityParam param) {
        // 校验参数
        checkActivityInfo(param);
        // 保存活动信息
        ActivityDO activityDO = new ActivityDO();
        activityDO.setActivityName(param.getActivityName());
        activityDO.setDescription(param.getDescription());
        activityDO.setStatus(ActivityStatusEnum.RUNNING.name());
        activityMapper.insert(activityDO);
        // 保存活动关联奖品
        List<CreatePrizeByActivityParam> prizeParams = param.getActivityPrizeList();
        List<ActivityPrizeDO> activityPrizeDOList = prizeParams
                .stream()
                .map(prizeParam -> {
                    ActivityPrizeDO activityPrizeDO = new ActivityPrizeDO();
                    activityPrizeDO.setActivityId(activityDO.getId());
                    activityPrizeDO.setPrizeId(prizeParam.getPrizeId());
                    activityPrizeDO.setPrizeAmount(prizeParam.getPrizeAmount());
                    activityPrizeDO.setPrizeTier(prizeParam.getPrizeTier());
                    activityPrizeDO.setStatus(ActivityPrizeStatusEnum.INIT.name());
                    return activityPrizeDO;
                }).collect(Collectors.toList());
        activityPrizeMapper.batchInsert(activityPrizeDOList);
        // 保存活动关联用户
        List<CreateUserByActivityParam> userParams = param.getActivityUserList();
        List<ActivityUserDO> activityUserDOList = userParams
                .stream()
                .map(userParam -> {
                    ActivityUserDO activityUserDO = new ActivityUserDO();
                    activityUserDO.setActivityId(activityDO.getId());
                    activityUserDO.setUserId(userParam.getUserId());
                    activityUserDO.setUserName(userParam.getUserName());
                    activityUserDO.setStatus(ActivityUserStatusEnum.RUNNING.name());
                    return activityUserDO;
                }).collect(Collectors.toList());
        activityUserMapper.batchInsert(activityUserDOList);
        // 整合完整活动信息,存入redis
        //先获取奖品基本属性列表
        //获取要查询的产品id
        List<Long> prizeIds = param.getActivityPrizeList().stream().map(CreatePrizeByActivityParam::getPrizeId).distinct().collect(Collectors.toList());
        List<PrizeDO> prizeDOList = prizeMapper.batchSelectByIds(prizeIds);
        ActivityDetailDTO detailDTO = convertToActivityDetailDTO(activityDO, activityUserDOList, activityPrizeDOList, prizeDOList);
        cacheActivity(detailDTO);
        // 返回
        CreateActivityDTO createActivityDTO = new CreateActivityDTO();
        createActivityDTO.setActivityId(activityDO.getId());
        return createActivityDTO;
    }

    @Override
    public ActivityPageDTO findActivityList(PageParam param, String status) {
        // 非法状态值直接忽略而不是抛错：用户端可能带上过期的筛选参数，
        // 让它退化为「全部」比整页报错更稳妥
        String realStatus = null;
        if (StringUtils.hasText(status) && ActivityStatusEnum.forName(status) != null) {
            realStatus = status;
        }
        // 筛选后的总量，用于分页器算总页数
        int total = activityMapper.countByStatus(realStatus);
        int runningCount = activityMapper.countByStatus(ActivityStatusEnum.RUNNING.name());
        int completedCount = activityMapper.countByStatus(ActivityStatusEnum.COMPLETED.name());
        // 超出实际页数时返回空列表（如筛选后只剩 1 页却请求第 3 页）
        List<ActivityDO> activityDoList = param.offset() >= total
                ? Collections.emptyList()
                : activityMapper.selectActivityListByStatus(param.offset(), param.getPageSize(), realStatus);
        //转换为DTO
        List<ActivityDTO> activityDTOList = activityDoList.stream().map(activityDO -> {
            ActivityDTO activityDTO = new ActivityDTO();
            activityDTO.setActivityId(activityDO.getId());
            activityDTO.setActivityName(activityDO.getActivityName());
            activityDTO.setDescription(activityDO.getDescription());
            activityDTO.setStatus(ActivityStatusEnum.valueOf(activityDO.getStatus()));
            return activityDTO;
        }).collect(Collectors.toList());
        return new ActivityPageDTO(total, activityDTOList, runningCount, completedCount);
    }

    @Override
    public ActivityDetailDTO getActivityDetail(Long activityId) {
        if (activityId == null) {
            logger.warn("activityId is null,查询活动详细失败");
            return null;
        }
        //查询redis
        ActivityDetailDTO detailDTO = getActivityFromCache(activityId);
        if (detailDTO != null) {
            logger.info("查询活动信息成功,activityDTO:{}", JacksonUtil.writeValueAsString(detailDTO));
            return detailDTO;
        }
        //redis中不存在,查询数据库
        //查询活动信息
        ActivityDO activityDO = activityMapper.selectById(activityId);
        if (activityDO == null) {
            logger.warn("查询活动信息为空,activityId:{}", activityId);
            return null;
        }
        //查询用户信息
        List<ActivityUserDO> activityUserDOList = activityUserMapper.selectByActivityId(activityId);
        //查询奖品信息
        List<ActivityPrizeDO> activityPrizeDOList = activityPrizeMapper.selectByActivityId(activityId);
        //奖品表查询
        List<PrizeDO> prizeDOList = prizeMapper.batchSelectByIds(activityPrizeDOList.stream()
                .map(ActivityPrizeDO::getPrizeId)
                .collect(Collectors.toList()));
        //整合并存入redis
        detailDTO = convertToActivityDetailDTO(activityDO, activityUserDOList, activityPrizeDOList, prizeDOList);
        cacheActivity(detailDTO);
        return detailDTO;
    }

    @Override
    public void cacheActivity(Long activityId) {
        if (activityId == null) {
            logger.warn("要缓存的活动id为空,activityId:{}", activityId);
            throw new ServiceException(ServiceErrorCodeConstants.CACHE_ACTIVITY_NOT_EXIST);
        }
        //查询表数据
        //查询活动信息
        ActivityDO activityDO = activityMapper.selectById(activityId);
        if (activityDO == null) {
            logger.error("要缓存的活动id有误,activityId:{}", activityId);
            throw new ServiceException(ServiceErrorCodeConstants.CACHE_ACTIVITY_NOT_EXIST);
        }
        //查询用户信息
        List<ActivityUserDO> activityUserDOList = activityUserMapper.selectByActivityId(activityId);
        //查询奖品信息
        List<ActivityPrizeDO> activityPrizeDOList = activityPrizeMapper.selectByActivityId(activityId);
        //奖品表查询
        List<PrizeDO> prizeDOList = prizeMapper.batchSelectByIds(activityPrizeDOList.stream()
                .map(ActivityPrizeDO::getPrizeId)
                .collect(Collectors.toList()));
        //整合完整活动信息并缓存
        cacheActivity(convertToActivityDetailDTO(activityDO, activityUserDOList, activityPrizeDOList, prizeDOList));
    }

    // 缓存活动信息
    private void cacheActivity(ActivityDetailDTO detailDTO) {
        //key:ACTIVITY_activityId
        //value:ActivityDetailDTO
        if (detailDTO == null || detailDTO.getActivityId() == null) {
            logger.error("缓存活动信息失败,活动信息为空");
            return;
        }
        try {
            redisUtil.set(ACTIVITY_PREFIX + detailDTO.getActivityId(), JacksonUtil.writeValueAsString(detailDTO), ACTIVITY_TIMEOUT);
        } catch (Exception e) {
            logger.error("缓存活动信息失败,activityId:{}", detailDTO.getActivityId(), e);
        }
    }

    // 从redis中获取活动信息
    private ActivityDetailDTO getActivityFromCache(Long activityId) {
        if (activityId == null) {
            logger.error("从缓存中获取活动信息失败,activityId为空");
            return null;
        }
        try {
            String cacheStr = redisUtil.get(ACTIVITY_PREFIX + activityId);
            if (!StringUtils.hasText(cacheStr)) {
                logger.info("从缓存中获取活动信息为空,activityId:{}", activityId);
                return null;
            }
            return JacksonUtil.readValue(cacheStr, ActivityDetailDTO.class);
        } catch (Exception e) {
            logger.error("从缓存中获取活动信息失败,activityId:{}", activityId, e);
            return null;
        }
    }

    private ActivityDetailDTO convertToActivityDetailDTO(ActivityDO activityDO, List<ActivityUserDO> activityUserDOList, List<ActivityPrizeDO> activityPrizeDOList, List<PrizeDO> prizeDOList) {
        ActivityDetailDTO detailDTO = new ActivityDetailDTO();
        detailDTO.setActivityId(activityDO.getId());
        detailDTO.setActivityName(activityDO.getActivityName());
        detailDTO.setDesc(activityDO.getDescription());
        detailDTO.setStatus(ActivityStatusEnum.forName(activityDO.getStatus()));
        List<ActivityDetailDTO.PrizeDTO> prizeDTOList = activityPrizeDOList
                .stream()
                .map(apDo -> {
                    ActivityDetailDTO.PrizeDTO prizeDTO = new ActivityDetailDTO.PrizeDTO();
                    prizeDTO.setPrizeId(apDo.getPrizeId());
                    Optional<PrizeDO> optionalPrizeDO
                            = prizeDOList.stream()
                            .filter(prizeDO -> prizeDO.getId().equals(apDo.getPrizeId())).findFirst();
                    optionalPrizeDO.ifPresent(prizeDO -> {
                        prizeDTO.setName(prizeDO.getName());
                        prizeDTO.setDescription(prizeDO.getDescription());
                        prizeDTO.setPrice(prizeDO.getPrice());
                        prizeDTO.setImageUrl(prizeDO.getImageUrl());
                    });
                    prizeDTO.setTiers(ActivityPrizeTiersEnum.forName(apDo.getPrizeTier()));
                    prizeDTO.setPrizeAmount(apDo.getPrizeAmount());
                    prizeDTO.setStatus(ActivityPrizeStatusEnum.forName(apDo.getStatus()));
                    return prizeDTO;
                }).collect(Collectors.toList());
        detailDTO.setPrizeDTOList(prizeDTOList);
        //用户信息
        List<ActivityDetailDTO.UserDTO> userDTOList = activityUserDOList
                .stream()
                .map(auDo -> {
                    ActivityDetailDTO.UserDTO userDTO = new ActivityDetailDTO.UserDTO();
                    userDTO.setUserId(auDo.getUserId());
                    userDTO.setName(auDo.getUserName());
                    userDTO.setStatus(ActivityUserStatusEnum.forName(auDo.getStatus()));
                    return userDTO;
                }).collect(Collectors.toList());
        detailDTO.setUserDTOList(userDTOList);
        return detailDTO;
    }

    //校验活动是否有效
    private void checkActivityInfo(CreateActivityParam param) {
        if (param == null) {
            throw new ServiceException(ServiceErrorCodeConstants.CREATE_ACTIVITY_EMPTY);
        }
        //人员id在人员表中是否存在
        List<Long> userIds = param.getActivityUserList()
                .stream()
                .map(CreateUserByActivityParam::getUserId)
                .distinct()
                .collect(Collectors.toList());
        List<Long> existUserIds = userMapper.selectExistByUserIds(userIds);
        if (CollectionUtils.isEmpty(existUserIds)) {
            throw new ServiceException(ServiceErrorCodeConstants.ACTIVITY_USER_ERROR);
        }
        userIds.forEach(userId -> {
            if (!existUserIds.contains(userId)) {
                throw new ServiceException(ServiceErrorCodeConstants.ACTIVITY_USER_ERROR);
            }
        });
        //奖品id在奖品表中是否存在
        List<Long> prizeIds = param.getActivityPrizeList()
                .stream()
                .map(CreatePrizeByActivityParam::getPrizeId)
                .distinct()
                .collect(Collectors.toList());
        List<Long> existPrizeIds = prizeMapper.selectExistByPrizeIds(prizeIds);
        prizeIds.forEach(prizeId -> {
            if (!existPrizeIds.contains(prizeId)) {
                throw new ServiceException(ServiceErrorCodeConstants.ACTIVITY_PRIZE_ERROR);
            }
        });
        //人员大等于奖品数量
        int userAmount = param.getActivityUserList().size();
        long prizeAmount = param.getActivityPrizeList()
                .stream()
                .mapToLong(CreatePrizeByActivityParam::getPrizeAmount)
                .sum();
        if (userAmount < prizeAmount) {
            throw new ServiceException(ServiceErrorCodeConstants.ACTIVITY_USER_PRIZE_ERROR);
        }
        //校验活动奖品等级有效性
        param.getActivityPrizeList().forEach(prize -> {
            if (StringUtils.hasText(prize.getPrizeTier()) && ActivityPrizeTiersEnum.forName(prize.getPrizeTier()) == null) {
                throw new ServiceException(ServiceErrorCodeConstants.ACTIVITY_PRIZE_TIER_ERROR);
            }
        });
    }

    /* ============ 活动管理（管理端）============ */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void endActivity(Long activityId) {
        ActivityDO activityDO = getActivityDOForUpdate(activityId);
        // 已是结束态则直接返回，避免重复操作把状态回退
        if (ActivityStatusEnum.COMPLETED.name().equals(activityDO.getStatus())) {
            return;
        }
        activityMapper.updateStatus(activityId, ActivityStatusEnum.COMPLETED.name());
        // 同步关联的奖品与人员终态，保证列表与详情口径一致
        List<ActivityPrizeDO> prizeDOList = activityPrizeMapper.selectByActivityId(activityId);
        prizeDOList.forEach(prizeDO -> activityPrizeMapper.updateStatus(
                activityId, prizeDO.getPrizeId(), ActivityPrizeStatusEnum.COMPLETED.name()));
        List<ActivityUserDO> userDOList = activityUserMapper.selectByActivityId(activityId);
        if (!CollectionUtils.isEmpty(userDOList)) {
            List<Long> userIds = userDOList.stream().map(ActivityUserDO::getUserId)
                    .collect(Collectors.toList());
            activityUserMapper.batchUpdateStatus(activityId, userIds,
                    ActivityUserStatusEnum.COMPLETED.name());
        }
        // 缓存里存的是旧的活动详情，不清会导致抽奖页仍读到 RUNNING
        redisUtil.delete(ACTIVITY_PREFIX + activityId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteActivity(Long activityId) {
        // 先确认活动存在，避免删一个不存在的 id 却返回成功
        getActivityDOForUpdate(activityId);
        // 删除顺序：中奖记录 → 关联奖品/人员 → 活动主表
        // winning_record 虽无外键约束，但留着会成为孤儿数据，
        // 且公开中奖名单页按 activity_id 查询，会查出已删除活动的中奖人
        winningRecordMapper.deleteRecords(activityId, null);
        activityPrizeMapper.deleteByActivityId(activityId);
        activityUserMapper.deleteByActivityId(activityId);
        activityMapper.deleteById(activityId);
        redisUtil.delete(ACTIVITY_PREFIX + activityId);
    }

    // 取出活动，不存在时抛 404 语义错误
    private ActivityDO getActivityDOForUpdate(Long activityId) {
        if (activityId == null) {
            throw new ServiceException(ServiceErrorCodeConstants.ACTIVITY_ID_IS_EMPTY);
        }
        ActivityDO activityDO = activityMapper.selectById(activityId);
        if (activityDO == null) {
            throw new ServiceException(ServiceErrorCodeConstants.ACTIVITY_NOT_EXIST);
        }
        return activityDO;
    }
}
