package org.example.lottery_system.controller.param;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreatePrizeByActivityParam {
    // 奖品id
    @NotNull(message = "奖品id不能为空")
    private Long prizeId;
    // 奖品数量
    @NotNull(message = "奖品数量不能为空")
    private Long prizeAmount;
    // 奖品等级
    private String prizeTier;
}
