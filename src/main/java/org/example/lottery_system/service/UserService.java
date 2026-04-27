package org.example.lottery_system.service;

import org.example.lottery_system.controller.param.UserLoginParam;
import org.example.lottery_system.controller.param.UserRegisterParam;
import org.example.lottery_system.service.dto.UserDTO;
import org.example.lottery_system.service.dto.UserLoginDTO;
import org.example.lottery_system.service.dto.UserRegisterDTO;
import org.example.lottery_system.service.enums.UserIdentityEnum;

import java.util.List;

public interface UserService {
    /*
    * 注册
    * */
    UserRegisterDTO register(UserRegisterParam param);
    /*
    * 密码登录
    * */
    UserLoginDTO login(UserLoginParam param);
    /*
    * 查询用户列表
    * */
    List<UserDTO> findUserInfo(UserIdentityEnum identity);
}
