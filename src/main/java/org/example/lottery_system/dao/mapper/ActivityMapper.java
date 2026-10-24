package org.example.lottery_system.dao.mapper;

import org.apache.ibatis.annotations.*;
import org.example.lottery_system.dao.dataobject.ActivityDO;

import java.util.List;

@Mapper
public interface ActivityMapper {
    @Insert("insert into activity (activity_name, description, status) values (#{activityName}, #{description}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(ActivityDO activityDo);
    @Select("select count(1) from activity")
    int count();
    @Select("select * from activity order by id desc limit #{offset},#{pageSize}")
    List<ActivityDO> selectActivityList(@Param("offset") Integer offset,
                                        @Param("pageSize") Integer pageSize);
    // 按状态分页查询（status 为 null 时查全部），供用户端做「进行中/已结束」筛选
    @Select("<script>select * from activity " +
            "<where>" +
            "<if test='status != null and status != \"\"'>and status = #{status}</if>" +
            "</where> " +
            "order by id desc limit #{offset},#{pageSize}</script>")
    List<ActivityDO> selectActivityListByStatus(@Param("offset") Integer offset,
                                               @Param("pageSize") Integer pageSize,
                                               @Param("status") String status);
    // 统计总数，status 非空时只数该状态
    @Select("<script>select count(1) from activity " +
            "<where>" +
            "<if test='status != null and status != \"\"'>and status = #{status}</if>" +
            "</where></script>")
    int countByStatus(@Param("status") String status);
    @Select("select * from activity where id = #{id}")
    ActivityDO selectById(@Param("id") Long id);

    @Update("update activity set status = #{status} where id = #{id}")
    void updateStatus(@Param("id") Long id, @Param("status") String status);

    // 物理删除活动主表记录（关联的奖品/人员/中奖记录由 Service 先行清理）
    @Delete("delete from activity where id = #{id}")
    int deleteById(@Param("id") Long id);
}
