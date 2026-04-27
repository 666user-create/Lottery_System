package org.example.lottery_system.service.dto;

import lombok.Data;
import org.example.lottery_system.service.enums.UserIdentityEnum;

@Data
public class UserLoginDTO {
    /**
     * 登录成功后返回的JWT令牌
     */
    private String token;
    /**
     * 用户身份
     */
    private UserIdentityEnum identity;
}
