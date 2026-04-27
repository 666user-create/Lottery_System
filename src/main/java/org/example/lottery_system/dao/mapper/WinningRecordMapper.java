package org.example.lottery_system.dao.mapper;

import jakarta.validation.constraints.NotNull;
import org.apache.ibatis.annotations.*;
import org.example.lottery_system.dao.dataobject.WinningRecordDO;

import java.util.List;

@Mapper
public interface WinningRecordMapper {

    @Insert("<script>" +
            " insert into winning_record (activity_id, activity_name," +
            " prize_id, prize_name, prize_tier, " +
            " winner_id, winner_name, winner_email, winner_phone_number, winning_time) " +
            " values " +
            " <foreach collection='items' item='item' separator=','>" +
            " (" +
            " #{item.activityId}, #{item.activityName}," +
            " #{item.prizeId}, #{item.prizeName}, #{item.prizeTier}, " +
            " #{item.winnerId}, #{item.winnerName}, #{item.winnerEmail}, #{item.winnerPhoneNumber}, #{item.winningTime}" +
            " )" +
            " </foreach>" +
            " </script>")
    int batchInsert(@Param("items") @NotNull List<WinningRecordDO> winningRecordDOList);

    @Select("select * from winning_record where activity_id = #{activityId}")
    List<WinningRecordDO> selectByActivityId(@Param("activityId") Long activityId);

    @Select("select count(*) from winning_record where activity_id = #{activityId} and prize_id = #{prizeId}")
    int count(@Param("activityId") Long activityId, @Param("prizeId") Long prizeId);

    //删除活动或者奖品下的中奖记录
    @Delete("<script>delete from winning_record " +
            " where activity_id = #{activityId} " +
            " <if test='prizeId != null'>and prize_id = #{prizeId}</if> " +
            " </script>")
    void deleteRecords(@Param("activityId") Long activityId, @Param("prizeId") Long prizeId);

    @Select("<script>" +
            " select * from winning_record " +
            " where activity_id = #{activityId} " +
            " <if test='prizeId != null'>and prize_id = #{prizeId}</if> " +
            " </script>")
    List<WinningRecordDO> selectByActivityIdOrPrizeId(@Param("activityId") Long activityId, @Param("prizeId") Long prizeId);
}
