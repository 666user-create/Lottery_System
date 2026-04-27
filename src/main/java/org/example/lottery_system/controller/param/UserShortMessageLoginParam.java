package org.example.lottery_system.controller.param;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.validation.annotation.Validated;

@Data
@EqualsAndHashCode(callSuper = true)
public class UserShortMessageLoginParam extends UserLoginParam {
    /**
     * 手机号
     */
    @NotBlank(message = "手机号不能为空")
    private String loginMobile;
    /**
     * 短信验证码
     */
    @NotBlank(message = "短信验证码不能为空")
    private String verificationCode;
}
