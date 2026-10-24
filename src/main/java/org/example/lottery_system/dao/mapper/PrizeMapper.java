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

    /*
     * 删除奖品。
     * 注意：本项目所有表都没有外键约束，删除是否安全完全由应用层把关，
     * 必须先用 countUsedByActivity 判定该奖品是否仍被活动引用。
     */
    @Delete("delete from prize where id = #{id}")
    int deleteById(@Param("id") Long prizeId);

    /*
     * 统计该奖品被多少个活动引用。
     * 不为 0 就不能删：删掉会让对应活动的奖品清单、抽奖逻辑出现断链。
     * winning_record.prize_id 不参与判定——那张表已冗余存了 prize_name，
     * 活动删除后其 activity_prize 记录会一并清理，剩下的只是历史留痕。
     */
    @Select("select count(1) from activity_prize where prize_id = #{prizeId}")
    int countUsedByActivity(@Param("prizeId") Long prizeId);
}
