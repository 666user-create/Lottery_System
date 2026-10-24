package org.example.lottery_system.dao.mapper;

import org.apache.ibatis.annotations.*;
import org.example.lottery_system.dao.dataobject.FaqDO;

import java.util.List;

@Mapper
public interface FaqMapper {

    /** 新增 FAQ,自动回填 id */
    @Insert("insert into faq(question, answer, category) values(#{question}, #{answer}, #{category})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(FaqDO faqDO);

    /** 删除 FAQ */
    @Delete("delete from faq where id = #{id}")
    int deleteById(@Param("id") Long id);

    /** 按 id 查询 */
    @Select("select * from faq where id = #{id}")
    FaqDO selectById(@Param("id") Long id);

    /**
     * 分页查询
     *
     * 排序按 id 升序(= 添加顺序):第一页是最早的知识,最后一页才是新加的。
     * 原先用 id desc(最新在前)会导致"最后一页显示最早那条",与知识库管理的直觉相反。
     */
    @Select("select * from faq order by id asc limit #{offset}, #{pageSize}")
    List<FaqDO> selectPage(@Param("offset") Integer offset, @Param("pageSize") Integer pageSize);

    /** 总数 */
    @Select("select count(1) from faq")
    int count();

    /** 查全部(检索时用) */
    @Select("select * from faq")
    List<FaqDO> selectAll();

    /** 按 id 批量查(检索结果回填用) */
    @Select("<script>" +
            "select * from faq where id in " +
            "<foreach collection='ids' item='id' open='(' close=')' separator=','>#{id}</foreach>" +
            "</script>")
    List<FaqDO> batchSelectByIds(@Param("ids") List<Long> ids);

    /** 关键词兜底检索(没向量时用):多个关键词 OR 匹配问题和答案 */
    @Select("<script>" +
            "select * from faq where " +
            "<foreach collection='keywords' item='kw' open='(' close=')' separator=' OR '>" +
            "question like concat('%', #{kw}, '%') or answer like concat('%', #{kw}, '%')" +
            "</foreach>" +
            " order by id desc limit #{limit}" +
            "</script>")
    List<FaqDO> searchByKeyword(@Param("keywords") List<String> keywords, @Param("limit") int limit);
}
