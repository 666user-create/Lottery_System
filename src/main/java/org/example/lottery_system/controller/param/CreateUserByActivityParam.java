package org.example.lottery_system.controller.param;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateUserByActivityParam {
    // 用户id
    @NotNull(message = "用户id不能为空")
    private Long userId;
    // 用户名
    @NotBlank(message = "用户名不能为空")
    // 用户名
    private String userName;
}
