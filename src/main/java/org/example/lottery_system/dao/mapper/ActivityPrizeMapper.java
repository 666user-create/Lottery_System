package org.example.lottery_system.dao.mapper;

import jakarta.validation.constraints.NotNull;
import org.apache.ibatis.annotations.*;
import org.example.lottery_system.dao.dataobject.ActivityPrizeDO;

import java.util.List;

@Mapper
public interface ActivityPrizeMapper {
    @Insert("<script>insert into activity_prize(activity_id,prize_id,prize_amount,prize_tier,status) values <foreach collection='items' item='activityPrize' index='index' separator=',' >(#{activityPrize.activityId}, #{activityPrize.prizeId}, #{activityPrize.prizeAmount}, #{activityPrize.prizeTier}, #{activityPrize.status})</foreach></script>")
    @Options(useGeneratedKeys = true, keyProperty = "id",keyColumn = "id")
    int batchInsert(@Param("items") List<ActivityPrizeDO> activityPrizeDOList);

    /*
     * 必须显式按 prize_tier 排序，否则抽奖顺序不确定。
     *
     * 实测（2026-10-13）：本查询走 uk_a_p_id(activity_id, prize_id) 索引，
     * 无 ORDER BY 时 MySQL 按索引里 prize_id 的顺序返回，即 prize_id 升序。
     * 而抽奖顺序应该是一等奖品 → 二等奖品 → 三等奖品。
     * 二者只在 prize_id 恰好与等级同序时才一致：
     *   活动198（prize_id 19/27/28 恰对应一/二/三）表现正常，
     *   一旦管理员把不同奖品配到不同等级，奖项顺序就会错乱。
     *
     * FIELD 用与 ActivityPrizeTiersEnum 一致的顺序号，而非按枚举名字符串排
     * （FIRST_PRIZE < SECOND_PRIZE < THIRD_PRIZE 的字典序恰好也对，
     *   但依赖字典序是隐式约定，等级增减后极易失效）。
     * */
    @Select("select * from activity_prize where activity_id = #{activityId} " +
            "order by field(prize_tier, 'FIRST_PRIZE', 'SECOND_PRIZE', 'THIRD_PRIZE'), id")
    List<ActivityPrizeDO> selectByActivityId(@Param("activityId") Long activityId);

    @Select("select * from activity_prize where activity_id = #{activityId} and prize_id = #{prizeId}")
    ActivityPrizeDO selectByAPId(@Param("activityId") Long activityId,
                                 @Param("prizeId") Long prizeId);
    @Select("select count(1) from activity_prize where activity_id = #{activityId} and status = #{status}")
    int countPrize(@Param("activityId") Long activityId, @Param("status") String status);

    @Update("update activity_prize set status = #{status} where activity_id = #{activityId} and prize_id = #{prizeId}")
    void updateStatus(@Param("activityId") Long activityId, @Param("prizeId") Long prizeId, @Param("status") String status);

    // 删除活动时清理其关联奖品（由 Service 在删除主表前调用）
    @Delete("delete from activity_prize where activity_id = #{activityId}")
    int deleteByActivityId(@Param("activityId") Long activityId);
}
