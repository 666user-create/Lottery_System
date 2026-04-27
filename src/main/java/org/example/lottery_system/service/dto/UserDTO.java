package org.example.lottery_system.service.dto;

import lombok.Data;
import org.example.lottery_system.service.enums.UserIdentityEnum;

@Data
public class UserDTO {
    //用户id
    private Long userId;
    //用户名
    private String userName;
    //邮箱
    private String email;
    //手机号
    private String phoneNumber;
    //角色
    private UserIdentityEnum identity;
}
