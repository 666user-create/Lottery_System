package org.example.lottery_system.service.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PrizeDTO {
    //奖品id
    private Long prizeId;
    //奖品名
    private String name;
    //描述
    private String description;
    //价格
    private BigDecimal price;
    //图片索引
    private String imageUrl;
}
