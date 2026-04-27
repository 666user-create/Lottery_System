package org.example.lottery_system.dao.dataobject;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
public class PrizeDO extends BaseDO {
    //奖品名
    private String name;
    //描述
    private String description;
    //价格
    private BigDecimal price;
    //图片索引
    private String imageUrl;
}
