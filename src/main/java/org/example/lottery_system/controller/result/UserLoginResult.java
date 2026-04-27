package org.example.lottery_system.controller.result;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserLoginResult implements Serializable {
    /**
     * 登录成功后返回的JWT令牌
     */
    private String token;
    /**
     * 用户身份
     */
    private String identity;
}
