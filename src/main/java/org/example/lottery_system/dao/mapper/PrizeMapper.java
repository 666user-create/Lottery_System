package org.example.lottery_system.dao.mapper;

import jakarta.validation.constraints.NotNull;
import org.apache.ibatis.annotations.*;
import org.example.lottery_system.dao.dataobject.PrizeDO;

import java.util.List;

@Mapper
public interface PrizeMapper {
    //插入奖品
    @Insert("insert into prize( name,description,price,image_url) " +
            " values(#{name}, #{description}, #{price}, #{imageUrl})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(PrizeDO prizeDo);

    @Select("select count(1) from prize")
    int count();

    @Select("select * from prize order by id desc limit #{offset},#{pageSize}")
    List<PrizeDO> selectPrizeList(@Param("offset") Integer offset,
                                  @Param("pageSize") Integer pageSize);
    @Select("<script>select id from prize where id in <foreach collection='items' item='item' open='(' close=')'  separator=','>#{item}</foreach></script>")
    List<Long> selectExistByPrizeIds(@Param("items") List<Long> ids);

    @Select("<script>select * from prize where id in <foreach collection='items' item='item' open='(' close=')'  separator=','>#{item}</foreach></script>")
    List<PrizeDO> batchSelectByIds(@Param("items") List<Long> ids);

    @Select("select * from prize where id = #{id}")
    PrizeDO selectById(@Param("id") Long prizeId);
}
