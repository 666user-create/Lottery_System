package org.example.lottery_system.dao.dataobject;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * FAQ 向量实体,embedding 字段存 JSON 数组字符串
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class FaqEmbeddingDO extends BaseDO {
    /** 对应 faq 表 id */
    private Long faqId;
    /** 向量,JSON 数组字符串,如 [0.12,-0.35,...] */
    private String embedding;
}
