package org.example.lottery_system.service.impl;

import org.example.lottery_system.common.errorcode.ServiceErrorCodeConstants;
import org.example.lottery_system.common.exception.ServiceException;
import org.example.lottery_system.common.utils.JacksonUtil;
import org.example.lottery_system.common.utils.RedisUtil;
import org.example.lottery_system.controller.param.DrawPrizeParam;
import org.example.lottery_system.controller.param.ShowWinningRecordsParam;
import org.example.lottery_system.controller.result.WinningRecordsResult;
import org.example.lottery_system.dao.dataobject.*;
import org.example.lottery_system.dao.mapper.*;
import org.example.lottery_system.service.DrawPrizeService;
import org.example.lottery_system.service.dto.WinningRecordDTO;
import org.example.lottery_system.service.enums.ActivityPrizeTiersEnum;
import org.example.lottery_system.service.enums.ActivityStatusEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

import static org.example.lottery_system.common.config.DirectRabbitConfig.EXCHANGE_NAME;
import static org.example.lottery_system.common.config.DirectRabbitConfig.ROUTING;

@Service
public class DrawPrizeServiceImpl implements DrawPrizeService {
    private static final Long WINNING_RECORDS_TIMEOUT = 60 * 60 * 24 * 7L;
    private static final String WINNING_RECORDS_PREFIX = "WINNING_RECORDS_";
    private static Logger logger = LoggerFactory.getLogger(DrawPrizeServiceImpl.class);
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private ActivityMapper activityMapper;
    @Autowired
    private ActivityPrizeMapper activityPrizeMapper;
    @Autowired
    private PrizeMapper prizeMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WinningRecordMapper winningRecordMapper;
    @Autowired
    private RedisUtil redisUtil;

    @Override
    public void drawPrize(DrawPrizeParam param) {
        Map<String, String> map = new HashMap<>();
        map.put("messageId", String.valueOf(UUID.randomUUID()));
        map.put("messageData", JacksonUtil.writeValueAsString(param));
        //发消息:交换机,绑定的key,消息体
        rabbitTemplate.convertAndSend(EXCHANGE_NAME, ROUTING, map);
        logger.info("mq消息发送成功:{}", map);
    }

    @Override
    public Boolean checkDrawPrizeParam(DrawPrizeParam param) {
        ActivityDO activityDO = activityMapper.selectById(param.getActivityId());
        ActivityPrizeDO activityPrizeDO = activityPrizeMapper.selectByAPId(
                param.getActivityId(), param.getPrizeId()
        );
        //活动或者奖品是否存在
        if (activityDO == null || activityPrizeDO == null) {
            //throw new ServiceException(ServiceErrorCodeConstants.ACTIVITY_OR_PRIZE_IS_EMPTY);
            logger.info("活动或者奖品不存在:{}", JacksonUtil.writeValueAsString(param));
            return false;
        }
        //活动是否有效
        if (activityDO.getStatus().equalsIgnoreCase(ActivityStatusEnum.COMPLETED.name())) {
            //throw new ServiceException(ServiceErrorCodeConstants.ACTIVITY_COMPLETED);
            logger.info("活动已结束:{}", JacksonUtil.writeValueAsString(param));
            return false;
        }
        //奖品是否有效
        if (activityPrizeDO.getStatus().equalsIgnoreCase(ActivityStatusEnum.COMPLETED.name())) {
            //throw new ServiceException(ServiceErrorCodeConstants.ACTIVITY_PRIZE_COMPLETED);
            logger.info("奖品已结束:{}", JacksonUtil.writeValueAsString(param));
            return false;
        }
        //中奖人数是否与奖品数一致
        if (activityPrizeDO.getPrizeAmount() != param.getWinnerList().size()) {
            //throw new ServiceException(ServiceErrorCodeConstants.ACTIVITY_USER_PRIZE_NUM_ERROR);
            logger.info("中奖人数与奖品数不一致:{}", JacksonUtil.writeValueAsString(param));
            return false;
        }
        return true;
    }

    @Override
    public List<WinningRecordDO> saveWinnerRecords(DrawPrizeParam param) {
        if (param == null || param.getActivityId() == null || CollectionUtils.isEmpty(param.getWinnerList())) {
            logger.warn("保存中奖者名单参数为空:{}", JacksonUtil.writeValueAsString(param));
            return Collections.emptyList();
        }
        //查询相关信息
        ActivityDO activityDO = activityMapper.selectById(param.getActivityId());
        if (activityDO == null) {
            logger.error("保存中奖者名单失败,活动不存在:{}", param.getActivityId());
            return Collections.emptyList();
        }
        List<UserDO> userDOList = userMapper.batchSelectByIds(param.getWinnerList().stream()
                .map(DrawPrizeParam.Winner::getUserId)
                .collect(Collectors.toList()));
        if (CollectionUtils.isEmpty(userDOList)) {
            logger.error("保存中奖者名单失败,中奖者列表为空:{}", JacksonUtil.writeValueAsString(param.getWinnerList()));
            return Collections.emptyList();
        }
        PrizeDO prizeDO = prizeMapper.selectById(param.getPrizeId());
        if (prizeDO == null) {
            logger.error("保存中奖者名单失败,奖品不存在:{}", param.getPrizeId());
            return Collections.emptyList();
        }
        ActivityPrizeDO activityPrizeDO = activityPrizeMapper.selectByAPId(param.getActivityId(), param.getPrizeId());
        if (activityPrizeDO == null) {
            logger.error("保存中奖者名单失败,活动奖品关系不存在:{},{}", param.getActivityId(), param.getPrizeId());
            return Collections.emptyList();
        }
        //构造中奖者记录
        List<WinningRecordDO> winningRecordDOList = userDOList.stream()
                .map(userDO -> {
                    WinningRecordDO winningRecordDO = new WinningRecordDO();
                    winningRecordDO.setActivityId(activityDO.getId());
                    winningRecordDO.setActivityName(activityDO.getActivityName());
                    winningRecordDO.setPrizeId(prizeDO.getId());
                    winningRecordDO.setPrizeName(prizeDO.getName());
                    winningRecordDO.setPrizeTier(activityPrizeDO.getPrizeTier());
                    winningRecordDO.setWinnerId(userDO.getId());
                    winningRecordDO.setWinnerName(userDO.getUserName());
                    winningRecordDO.setWinnerEmail(userDO.getEmail());
                    winningRecordDO.setWinnerPhoneNumber(userDO.getPhoneNumber());
                    winningRecordDO.setWinningTime(param.getWinningTime());
                    return winningRecordDO;
                })
                .collect(Collectors.toList());
        if (!CollectionUtils.isEmpty(winningRecordDOList)) {
            winningRecordMapper.batchInsert(winningRecordDOList);
        }
        //缓存中奖者名单
        //1.缓存奖品维度的中奖记录(key:WinningRecord_activityId_prizeId,winningRecordDOList)
        cacheWinningRecords(param.getActivityId() + "_" + param.getPrizeId(), winningRecordDOList, WINNING_RECORDS_TIMEOUT);
        //2.缓存整个活动维度的中奖记录(key:WinningRecord_activityId,winningRecordDOList)
        //活动已完成再存放
        if (activityDO.getStatus().equalsIgnoreCase(ActivityStatusEnum.COMPLETED.name())) {
            List<WinningRecordDO> asList = winningRecordMapper.selectByActivityId(param.getActivityId());
            cacheWinningRecords(String.valueOf(param.getActivityId()), asList, WINNING_RECORDS_TIMEOUT);
        }
        return winningRecordDOList;
    }

    @Override
    public void deleteWinnerRecords(Long activityId, Long prizeId) {
        if (activityId == null) {
            logger.error("删除中奖者名单失败,活动id为空");
            return;
        }
        //删除数据库中的中奖记录
        winningRecordMapper.deleteRecords(activityId, prizeId);
        //删除缓存中的中奖记录
        //无论有没有prizeId,都删除活动维度的中奖记录
        if (prizeId != null) {
            //删除活动维度的中奖记录
            deleteWinningRecords(activityId + "_" + prizeId);
        }
        deleteWinningRecords(String.valueOf(activityId));
    }

    @Override
    public List<WinningRecordDTO> getRecords(ShowWinningRecordsParam param) {
        //查询Redis
        String key=param.getPrizeId()==null?String.valueOf(param.getActivityId()):
                param.getActivityId()+"_"+param.getPrizeId();
        List<WinningRecordDO> winningRecordDOList = getWinningRecords(key);
        if(!CollectionUtils.isEmpty(winningRecordDOList)){
            return convertToWinningRecordDTO(winningRecordDOList);
        }
        //没有再查询数据库,存记录到Redis中
        winningRecordDOList = winningRecordMapper.selectByActivityIdOrPrizeId(param.getActivityId(), param.getPrizeId());
        if (CollectionUtils.isEmpty(winningRecordDOList)) {
            logger.info("查询中奖者名单失败,中奖记录为空:{}", JacksonUtil.writeValueAsString(param));
            return Arrays.asList();
        }
        cacheWinningRecords(key, winningRecordDOList, WINNING_RECORDS_TIMEOUT);
        return convertToWinningRecordDTO(winningRecordDOList);
    }

    private List<WinningRecordDTO> convertToWinningRecordDTO(List<WinningRecordDO> winningRecordDOList) {
        if (CollectionUtils.isEmpty(winningRecordDOList)) {
            return Arrays.asList();
        }
        return winningRecordDOList.stream()
                .map(winningRecordDO -> {
                    WinningRecordDTO winningRecordDTO = new WinningRecordDTO();
                    winningRecordDTO.setWinnerId(winningRecordDO.getWinnerId());
                    winningRecordDTO.setWinnerName(winningRecordDO.getWinnerName());
                    winningRecordDTO.setPrizeName(winningRecordDO.getPrizeName());
                    winningRecordDTO.setPrizeTier(ActivityPrizeTiersEnum.forName(winningRecordDO.getPrizeTier()));
                    winningRecordDTO.setWinningTime(winningRecordDO.getWinningTime());
                    return winningRecordDTO;
                })
                .collect(Collectors.toList());
    }

    private void deleteWinningRecords(String key) {
        try {
            if (redisUtil.hasKey(WINNING_RECORDS_PREFIX + key)) {
                redisUtil.delete(WINNING_RECORDS_PREFIX + key);
            }
        } catch (Exception e) {
            logger.error("删除缓存中的中奖记录失败:{}:{}", WINNING_RECORDS_PREFIX + key, key, e);
        }
    }

    private void cacheWinningRecords(String key, List<WinningRecordDO> winningRecordDOList, Long time) {
        try {
            if (!StringUtils.hasText(key) || CollectionUtils.isEmpty(winningRecordDOList)) {
                logger.warn("缓存中奖者名单为空:{}:{}", key, JacksonUtil.writeValueAsString(winningRecordDOList));
                return;
            }
            String cacheStr = JacksonUtil.writeValueAsString(winningRecordDOList);
            redisUtil.set(WINNING_RECORDS_PREFIX + key, cacheStr, time);
        } catch (Exception e) {
            logger.error("缓存中奖者名单失败:{}:{}", WINNING_RECORDS_PREFIX + key, key, e);
        }

    }

    private List<WinningRecordDO> getWinningRecords(String key) {
        try {
            if (!StringUtils.hasText(key)) {
                logger.error("要从缓存中查询的中奖记录的key为空");
                return Collections.emptyList();
            }
            String cacheStr = redisUtil.get(WINNING_RECORDS_PREFIX + key);
            if (!StringUtils.hasText(cacheStr)) {
                return Collections.emptyList();
            }
            return JacksonUtil.readListValue(cacheStr, WinningRecordDO.class);
        } catch (Exception e) {
            logger.error("从缓存中查询中奖者名单失败:{}:{}", WINNING_RECORDS_PREFIX + key, key, e);
            return Collections.emptyList();
        }

    }
}
