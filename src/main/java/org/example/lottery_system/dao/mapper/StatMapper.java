package org.example.lottery_system.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.lottery_system.dao.dataobject.ActivityPrizeStatDO;
import org.example.lottery_system.dao.dataobject.PrizeTierStatDO;

import java.util.List;

/*
* 数据统计专用 Mapper
*
* 设计约定：本接口内全部为只读聚合查询，不含任何 insert/update/delete，
* 因此不需要事务，也不会影响既有业务数据。
* */
@Mapper
public interface StatMapper {

    /*
     * 活动总数
     * */
    @Select("select count(1) from activity")
    int countActivity();

    /*
     * 按状态统计活动数（status 传入 ActivityStatusEnum 的枚举名）
     * */
    @Select("select count(1) from activity where status = #{status}")
    int countActivityByStatus(@Param("status") String status);

    /*
     * 已开奖活动数（中奖记录中奖现的活动去重）
     * */
    @Select("select count(distinct activity_id) from winning_record")
    int countDrawnActivity();

    /*
     * 奖品总数
     * */
    @Select("select count(1) from prize")
    int countPrize();

    /*
     * 按身份统计用户数（identity 传入 UserIdentityEnum 的枚举名）
     * */
    @Select("select count(1) from user where identity = #{identity}")
    int countUserByIdentity(@Param("identity") String identity);

    /*
     * 累计中奖人次
     * */
    @Select("select count(1) from winning_record")
    int countWinningRecord();

    /*
     * 各奖品等级的中奖数量分布
     * */
    @Select("select prize_tier, count(1) as cnt from winning_record group by prize_tier")
    List<PrizeTierStatDO> countWinningByTier();

    /*
     * 某个活动的参与人数
     * */
    @Select("select count(1) from activity_user where activity_id = #{activityId}")
    int countActivityUser(@Param("activityId") Long activityId);

    /*
     * 某个活动的奖品总份数
     * */
    @Select("select coalesce(sum(prize_amount), 0) from activity_prize where activity_id = #{activityId}")
    Long sumPrizeAmount(@Param("activityId") Long activityId);

    /*
     * 某个活动的已中奖份数
     * */
    @Select("select count(1) from winning_record where activity_id = #{activityId}")
    int countWinningByActivity(@Param("activityId") Long activityId);

    /*
     * 某个活动下各奖品的“已中奖 / 总份数”明细
     * */
    @Select("select ap.prize_id, p.name as prize_name, ap.prize_tier, ap.prize_amount, " +
            " (select count(1) from winning_record w " +
            "   where w.activity_id = ap.activity_id and w.prize_id = ap.prize_id) as drawn " +
            " from activity_prize ap join prize p on p.id = ap.prize_id " +
            " where ap.activity_id = #{activityId}")
    List<ActivityPrizeStatDO> selectActivityPrizeStat(@Param("activityId") Long activityId);
}
