package org.example.lottery_system.controller.param;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

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
     * */
    @Size(max = 255, message = "密码长度不能超过255")
    private String password;

    /*
     * 身份
     * 注意：该字段会被服务端策略覆盖（见 UserController.applyRegisterIdentityPolicy）
     * */
    @NotBlank(message = "身份不能为空")
    @Size(max = 32, message = "身份长度不能超过32")
    private String identity;
}
