package org.example.lottery_system.controller.param;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class UpdateEmailParam extends AccountVerifyParam {

    @NotBlank(message = "新邮箱不能为空")
    private String newEmail;
}
