package org.example.lottery_system.service.impl;

import org.example.lottery_system.dao.dataobject.ActivityDO;
import org.example.lottery_system.dao.dataobject.ActivityPrizeStatDO;
import org.example.lottery_system.dao.dataobject.PrizeTierStatDO;
import org.example.lottery_system.dao.mapper.ActivityMapper;
import org.example.lottery_system.dao.mapper.StatMapper;
import org.example.lottery_system.service.StatService;
import org.example.lottery_system.service.dto.ActivityStatDTO;
import org.example.lottery_system.service.dto.StatOverviewDTO;
import org.example.lottery_system.service.enums.ActivityPrizeTiersEnum;
import org.example.lottery_system.service.enums.ActivityStatusEnum;
import org.example.lottery_system.service.enums.UserIdentityEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/*
* 数据统计实现
*
* 设计说明：
* 1.本类只做只读聚合，不写任何业务数据，因此不加 @Transactional；
* 2.统计数据刻意不走 Redis 缓存：数据量小、访问频率低，
*   缓存反而会引入“抽奖后统计不刷新”的一致性问题；
* 3.聚合与排序逻辑集中在 Service，Controller 只负责 DTO -> Result 的字段搬运。
* */
@Service
public class StatServiceImpl implements StatService {
    private static final Logger logger = LoggerFactory.getLogger(StatServiceImpl.class);

    @Autowired
    private StatMapper statMapper;
    @Autowired
    private ActivityMapper activityMapper;

    @Override
    public StatOverviewDTO getOverview() {
        StatOverviewDTO statOverviewDTO = new StatOverviewDTO();
        statOverviewDTO.setActivityTotal(statMapper.countActivity());
        statOverviewDTO.setActivityRunning(
                statMapper.countActivityByStatus(ActivityStatusEnum.RUNNING.name()));
        statOverviewDTO.setActivityCompleted(
                statMapper.countActivityByStatus(ActivityStatusEnum.COMPLETED.name()));
        statOverviewDTO.setActivityDrawn(statMapper.countDrawnActivity());
        statOverviewDTO.setPrizeTotal(statMapper.countPrize());
        statOverviewDTO.setUserTotal(
                statMapper.countUserByIdentity(UserIdentityEnum.NORMAL.name()));
        statOverviewDTO.setWinningTotal(statMapper.countWinningRecord());

        //各奖品等级中奖分布：数据库里存的是枚举名，这里翻译成中文并按等级编码排序
        List<StatOverviewDTO.TierStat> tierStatList = new ArrayList<>();
        List<PrizeTierStatDO> prizeTierStatDOList = statMapper.countWinningByTier();
        if (!CollectionUtils.isEmpty(prizeTierStatDOList)) {
            for (PrizeTierStatDO prizeTierStatDO : prizeTierStatDOList) {
                ActivityPrizeTiersEnum tiersEnum =
                        ActivityPrizeTiersEnum.forName(prizeTierStatDO.getPrizeTier());
                StatOverviewDTO.TierStat tierStat = new StatOverviewDTO.TierStat();
                tierStat.setTierCode(tiersEnum == null ? Integer.MAX_VALUE : tiersEnum.getCode());
                tierStat.setTierName(tiersEnum == null ? "未知等级" : tiersEnum.getMessage());
                tierStat.setCount(prizeTierStatDO.getCnt() == null ? 0 : prizeTierStatDO.getCnt());
                tierStatList.add(tierStat);
            }
            tierStatList.sort(Comparator.comparingInt(StatOverviewDTO.TierStat::getTierCode));
        }
        statOverviewDTO.setTierStats(tierStatList);
        return statOverviewDTO;
    }

    @Override
    public ActivityStatDTO getActivityStat(Long activityId) {
        if (activityId == null) {
            return null;
        }
        ActivityDO activityDO = activityMapper.selectById(activityId);
        if (activityDO == null) {
            logger.info("统计活动不存在:{}", activityId);
            return null;
        }

        ActivityStatDTO activityStatDTO = new ActivityStatDTO();
        activityStatDTO.setActivityId(activityDO.getId());
        activityStatDTO.setActivityName(activityDO.getActivityName());
        activityStatDTO.setStatus(activityDO.getStatus());
        ActivityStatusEnum statusEnum = ActivityStatusEnum.forName(activityDO.getStatus());
        activityStatDTO.setStatusMessage(
                statusEnum == null ? activityDO.getStatus() : statusEnum.getMessage());
        activityStatDTO.setParticipantTotal(statMapper.countActivityUser(activityId));

        Long prizeAmountTotal = statMapper.sumPrizeAmount(activityId);
        activityStatDTO.setPrizeAmountTotal(prizeAmountTotal == null ? 0L : prizeAmountTotal);
        activityStatDTO.setDrawnTotal(statMapper.countWinningByActivity(activityId));

        List<ActivityStatDTO.PrizeStat> prizeStatList = new ArrayList<>();
        List<ActivityPrizeStatDO> prizeStatDOList = statMapper.selectActivityPrizeStat(activityId);
        if (!CollectionUtils.isEmpty(prizeStatDOList)) {
            for (ActivityPrizeStatDO prizeStatDO : prizeStatDOList) {
                ActivityPrizeTiersEnum tiersEnum =
                        ActivityPrizeTiersEnum.forName(prizeStatDO.getPrizeTier());
                ActivityStatDTO.PrizeStat prizeStat = new ActivityStatDTO.PrizeStat();
                prizeStat.setPrizeId(prizeStatDO.getPrizeId());
                prizeStat.setPrizeName(prizeStatDO.getPrizeName());
                prizeStat.setPrizeTier(prizeStatDO.getPrizeTier());
                prizeStat.setPrizeTierName(tiersEnum == null ? "未知等级" : tiersEnum.getMessage());
                prizeStat.setTierCode(tiersEnum == null ? Integer.MAX_VALUE : tiersEnum.getCode());
                prizeStat.setPrizeAmount(
                        prizeStatDO.getPrizeAmount() == null ? 0L : prizeStatDO.getPrizeAmount());
                prizeStat.setDrawn(prizeStatDO.getDrawn() == null ? 0 : prizeStatDO.getDrawn());
                prizeStatList.add(prizeStat);
            }
            prizeStatList.sort(Comparator.comparingInt(ActivityStatDTO.PrizeStat::getTierCode));
        }
        activityStatDTO.setPrizeStats(prizeStatList);
        return activityStatDTO;
    }
}
