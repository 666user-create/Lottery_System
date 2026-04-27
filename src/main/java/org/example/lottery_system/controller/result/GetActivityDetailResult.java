package org.example.lottery_system.controller.result;

import lombok.Data;
import org.example.lottery_system.service.dto.ActivityDetailDTO;
import org.example.lottery_system.service.enums.ActivityPrizeStatusEnum;
import org.example.lottery_system.service.enums.ActivityPrizeTiersEnum;
import org.example.lottery_system.service.enums.ActivityStatusEnum;
import org.example.lottery_system.service.enums.ActivityUserStatusEnum;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
public class GetActivityDetailResult implements Serializable {
    //活动信息
    //活动id
    private Long activityId;
    //活动名
    private String activityName;
    //活动描述
    private String desc;
    //活动状态
    private Boolean status;
    //奖品信息
    private List<Prize> prizes;
    //用户信息
    private List<User>  users;


    @Data
    public static class Prize {
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
        //奖品等级
        private String tiers;
        //奖品数量
        private Long prizeAmount;
        //奖品状态
        private Boolean valid;
    }
    @Data
    public static class User {
        //用户id
        private Long userId;
        //用户名
        private String name;
        //用户状态
        private Boolean valid;
    }
}
