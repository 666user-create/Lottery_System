package org.example.lottery_system.controller.param;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

@Data
public class UserRegisterParam {
    /*
     * 姓名
     * 修复 BUG-010：原实现无长度校验，超长值直穿 DB（user_name varchar(255)）
     * → Data too long 异常 → 500 且泄露 SQL
     * */
    @NotBlank(message = "姓名不能为空")
    @Size(max = 255, message = "姓名长度不能超过255")
    private String name;
    /*
     * 邮箱
     * */
    @NotBlank(message = "邮箱不能为空")
    @Size(max = 255, message = "邮箱长度不能超过255")
    private String mail;
    /*
     * 手机号
     * */
    @NotBlank(message = "手机号不能为空")
    @Size(max = 32, message = "手机号长度不能超过32")
    private String phoneNumber;
    /*
     * 密码
     *
     * 安全：@JsonProperty(access = WRITE_ONLY) 表示「只反序列化、不序列化」——
     * 客户端提交的密码照常能收进来，但 writeValueAsString(param) 打日志时该字段被排除。
     * @ToString.Exclude 同时挡住 controller 里 logger.info("...:{}", param) 这种隐式 toString。
     * 两者都要，因为项目里两种打印方式都存在（见 UserController 与 DrawPrizeController）。
     * */
    @Size(max = 255, message = "密码长度不能超过255")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    private String password;

    /*
     * 身份
     * 注意：该字段会被服务端策略覆盖（见 UserController.applyRegisterIdentityPolicy）
     * */
    @NotBlank(message = "身份不能为空")
    @Size(max = 32, message = "身份长度不能超过32")
    private String identity;
}
