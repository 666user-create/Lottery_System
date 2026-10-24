package org.example.lottery_system.dao.mapper;

import org.apache.ibatis.annotations.*;
import org.example.lottery_system.dao.dataobject.FaqEmbeddingDO;

import java.util.List;

@Mapper
public interface FaqEmbeddingMapper {

    /** 保存向量 */
    @Insert("insert into faq_embedding(faq_id, embedding) values(#{faqId}, #{embedding})")
    int insert(FaqEmbeddingDO faqEmbeddingDO);

    /** 删向量(删 FAQ 时一起删) */
    @Delete("delete from faq_embedding where faq_id = #{faqId}")
    int deleteByFaqId(@Param("faqId") Long faqId);

    /** 查全部向量(检索时用,知识库小,一次性加载没问题) */
    @Select("select * from faq_embedding")
    List<FaqEmbeddingDO> selectAll();
}
