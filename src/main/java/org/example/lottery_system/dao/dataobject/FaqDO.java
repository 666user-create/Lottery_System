package org.example.lottery_system.dao.dataobject;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * FAQ 知识库实体,继承 BaseDO 自带 id/gmtCreate/gmtModified
 *
 * 修复 C-6/BUG-010：原实现无任何校验注解，question/answer 缺失或超长时
 * 直穿 DB 约束（NOT NULL / varchar(500)）→ 500 且响应中泄露完整 SQL 与表结构。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class FaqDO extends BaseDO {
    /** 问题 */
    @NotBlank(message = "问题不能为空")
    @Size(max = 500, message = "问题长度不能超过500")
    private String question;
    /** 答案 */
    @NotBlank(message = "答案不能为空")
    private String answer;
    /** 分类,如:抽奖规则/领奖/账号 */
    @Size(max = 100, message = "分类长度不能超过100")
    private String category;
}
