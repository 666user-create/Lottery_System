package org.example.lottery_system.service;

import org.example.lottery_system.controller.param.UpdateEmailParam;
import org.example.lottery_system.controller.param.UpdatePasswordParam;
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
    /*
    * 根据id查询用户信息
    * */
    UserDTO findById(Long userId);
    /*
    * 查询用户详情（除密码外的全部信息：基本资料 + 收货信息 + 时间戳）。
    * 仅管理端「查看详情」使用；密码任何情况下都不返回。
    * */
    UserDTO findDetailById(Long userId);
    /*
    * 修改邮箱（原密码 或 短信验证码 二选一验证）
    * */
    boolean updateEmail(Long userId, UpdateEmailParam param);
    /*
    * 修改密码（原密码 或 短信验证码 二选一验证）
    * */
    boolean updatePassword(Long userId, UpdatePasswordParam param);
    /*
    * 获取当前登录用户用于验证的手机号（脱敏后返回，供前端展示发送验证码的目标）
    * */
    String getMaskedPhoneNumber(Long userId);
    /*
    * 给当前登录用户的本人手机号发送「账号安全」场景验证码。
    * 手机号由服务端根据 userId 反查，不接受客户端传入。
    * */
    boolean sendSecurityVerificationCode(Long userId);

    /*
    * 启用/停用账号（仅管理员调用，路径已由 LoginInterceptor 限定 ADMIN）。
    * operatorId 用于两条兜底校验：不能停用自己、不能停用最后一个可用管理员。
    * */
    void changeUserStatus(Long operatorId, Long targetUserId, String status);
}
