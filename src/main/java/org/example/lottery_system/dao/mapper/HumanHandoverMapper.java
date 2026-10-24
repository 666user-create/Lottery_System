package org.example.lottery_system.dao.mapper;

import org.apache.ibatis.annotations.*;
import org.example.lottery_system.dao.dataobject.HumanHandoverDO;

import java.util.List;

@Mapper
public interface HumanHandoverMapper {

    /** 新增工单 */
    @Insert("insert into human_handover(user_id, session_id, type, ref_id, question, status) " +
            "values(#{userId}, #{sessionId}, #{type}, #{refId}, #{question}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(HumanHandoverDO handoverDO);

    /** 按 id 查工单(处理领奖工单时需要知道 type 与关联的中奖记录) */
    @Select("select * from human_handover where id = #{id}")
    HumanHandoverDO selectById(@Param("id") Long id);

    /** 按状态查工单(管理端);type 为空时查全部类型 */
    @Select("<script>" +
            "select * from human_handover " +
            "<where>" +
            "  <if test='status != null and status != \"\"'> and status = #{status}</if>" +
            "  <if test='type != null and type != \"\"'> and type = #{type}</if>" +
            "</where>" +
            "order by gmt_create desc limit #{offset}, #{pageSize}" +
            "</script>")
    List<HumanHandoverDO> selectPage(@Param("status") String status,
                                     @Param("type") String type,
                                     @Param("offset") Integer offset,
                                     @Param("pageSize") Integer pageSize);

    /** 按状态统计数量(管理端角标);type 为空时统计全部类型 */
    @Select("<script>" +
            "select count(1) from human_handover " +
            "<where>" +
            "  <if test='status != null and status != \"\"'> and status = #{status}</if>" +
            "  <if test='type != null and type != \"\"'> and type = #{type}</if>" +
            "</where>" +
            "</script>")
    int count(@Param("status") String status, @Param("type") String type);

    /** 更新工单状态(处理中/已解决) */
    @Update("update human_handover set status = #{status}, handler = #{handler}, remark = #{remark}, " +
            "gmt_modified = now() where id = #{id}")
    int updateStatus(HumanHandoverDO handoverDO);

    /**
     * 删除工单(各阶段都可删)
     *
     * 说明:工单只保留「待处理 / 处理中 / 已解决」三种状态,不再有「已关闭」;
     * 需要作废的工单直接删除即可。
     */
    @Delete("delete from human_handover where id = #{id}")
    int deleteById(@Param("id") Long id);
}
