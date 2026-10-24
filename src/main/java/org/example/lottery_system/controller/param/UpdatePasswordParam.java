package org.example.lottery_system.controller.param;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class UpdatePasswordParam extends AccountVerifyParam {

    @NotBlank(message = "新密码不能为空")
    private String newPassword;
}
