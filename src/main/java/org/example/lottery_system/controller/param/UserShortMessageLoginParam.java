package org.example.lottery_system.controller.param;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

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
     *
     * 安全：验证码等同于临时登录凭证，只收不发。
     */
    @NotBlank(message = "短信验证码不能为空")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    private String verificationCode;
}
