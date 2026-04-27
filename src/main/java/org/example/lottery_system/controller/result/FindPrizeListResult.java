package org.example.lottery_system.controller.result;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
public class FindPrizeListResult implements Serializable {
    //总量
    private Integer total;
    //当前列表
    private List<PrizeInfo> records;

    @Data
    public static class PrizeInfo {
        //奖品id
        private Long prizeId;
        //奖品名称
        private String prizeName;
        //奖品描述
        private String description;
        //奖品价格
        private BigDecimal price;
        //奖品图片url
        private String imageUrl;
    }
}
