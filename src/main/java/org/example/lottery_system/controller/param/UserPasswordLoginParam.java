package org.example.lottery_system.controller.param;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
public class UserPasswordLoginParam extends UserLoginParam{
    /**
     * 手机或邮箱
     */
    @NotBlank(message = "手机号或邮箱不能为空")
    private String loginName;
    /**
     * 密码
     *
     * 安全：只收不发——接收密码用于校验，但任何日志/序列化输出都不包含它。
     */
    @NotBlank(message = "密码不能为空")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    private String password;

}
