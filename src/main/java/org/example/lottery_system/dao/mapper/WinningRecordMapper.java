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

    @Select("select * from winning_record where winner_id = #{winnerId} order by winning_time desc")
    List<WinningRecordDO> selectByWinnerId(@Param("winnerId") Long winnerId);

    // ==================== 领奖履约(状态推进) ====================
    //
    // 每个迁移都带"原状态条件",靠影响行数判断是否成功:
    //   - 影响行数 0 → 状态不符(已被处理过)或并发重复提交,由 Service 转成友好错误
    //   - 因此不需要额外加锁,天然防重复领奖 / 重复发货

    @Select("select * from winning_record where id = #{id}")
    WinningRecordDO selectById(@Param("id") Long id);

    /** 客户端:申请领奖 未领取 → 已申请待发货 */
    @Update("update winning_record set claim_status = 'CLAIMED', claim_time = now() " +
            "where id = #{id} and claim_status = 'NOT_CLAIMED'")
    int claimIfNotClaimed(@Param("id") Long id);

    /** 管理端:登记发货 已申请待发货 → 已发货 */
    @Update("update winning_record set claim_status = 'SHIPPED', " +
            "shipping_company = #{shippingCompany}, shipping_no = #{shippingNo}, shipped_time = now() " +
            "where id = #{id} and claim_status = 'CLAIMED'")
    int shipIfClaimed(@Param("id") Long id,
                      @Param("shippingCompany") String shippingCompany,
                      @Param("shippingNo") String shippingNo);

    /** 客户端:确认收货 已发货 → 已签收 */
    @Update("update winning_record set claim_status = 'COMPLETED', completed_time = now() " +
            "where id = #{id} and claim_status = 'SHIPPED'")
    int completeIfShipped(@Param("id") Long id);

    /** 管理端:取消领奖 已申请待发货 → 未领取(允许用户重新申请) */
    @Update("update winning_record set claim_status = 'NOT_CLAIMED', claim_time = null " +
            "where id = #{id} and claim_status = 'CLAIMED'")
    int revertClaimIfClaimed(@Param("id") Long id);

    // ==================== 管理端:中奖履约列表 / 直接改状态 ====================

    /** 管理端分页列表(claimStatus 为空时查全部) */
    @Select("<script>" +
            "select * from winning_record " +
            "<where>" +
            "  <if test='claimStatus != null and claimStatus != \"\"'> and claim_status = #{claimStatus}</if>" +
            "</where>" +
            "order by winning_time desc limit #{offset}, #{pageSize}" +
            "</script>")
    List<WinningRecordDO> selectPageByClaimStatus(@Param("claimStatus") String claimStatus,
                                                  @Param("offset") Integer offset,
                                                  @Param("pageSize") Integer pageSize);

    /** 管理端总数统计 */
    @Select("<script>" +
            "select count(1) from winning_record " +
            "<where>" +
            "  <if test='claimStatus != null and claimStatus != \"\"'> and claim_status = #{claimStatus}</if>" +
            "</where>" +
            "</script>")
    int countByClaimStatus(@Param("claimStatus") String claimStatus);

    /**
     * 管理端直接设置领奖状态(覆盖式,不校验原状态)
     *
     * 时间/物流按目标状态自动维护,保证「往前改」和「往后退」都不会留脏数据:
     *   - 未领取      → 清空全部履约痕迹
     *   - 已申请      → 清空物流与后续时间(撤销发货时用得上)
     *   - 已发货      → 写入物流、记录发货时间,清空签收时间(撤销签收时用得上)
     *   - 已签收      → 保留物流,记录签收时间
     */
    @Update("update winning_record set " +
            "claim_status = #{claimStatus}, " +
            "claim_time = case when #{claimStatus} = 'NOT_CLAIMED' then null " +
            "                  else ifnull(claim_time, now()) end, " +
            "shipping_company = case when #{claimStatus} = 'SHIPPED' then #{shippingCompany} " +
            "                        when #{claimStatus} = 'COMPLETED' then shipping_company " +
            "                        else null end, " +
            "shipping_no = case when #{claimStatus} = 'SHIPPED' then #{shippingNo} " +
            "                   when #{claimStatus} = 'COMPLETED' then shipping_no " +
            "                   else null end, " +
            "shipped_time = case when #{claimStatus} = 'SHIPPED' then ifnull(shipped_time, now()) " +
            "                    when #{claimStatus} = 'COMPLETED' then shipped_time " +
            "                    else null end, " +
            "completed_time = case when #{claimStatus} = 'COMPLETED' then ifnull(completed_time, now()) " +
            "                      else null end " +
            "where id = #{id}")
    int adminUpdateClaimStatus(@Param("id") Long id,
                              @Param("claimStatus") String claimStatus,
                              @Param("shippingCompany") String shippingCompany,
                              @Param("shippingNo") String shippingNo);
}
