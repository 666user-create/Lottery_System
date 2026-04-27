package org.example.lottery_system.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import jakarta.validation.constraints.NotBlank;
import org.example.lottery_system.common.errorcode.ServiceErrorCodeConstants;
import org.example.lottery_system.common.exception.ServiceException;
import org.example.lottery_system.common.utils.JWTUtil;
import org.example.lottery_system.common.utils.RegexUtil;
import org.example.lottery_system.controller.param.UserLoginParam;
import org.example.lottery_system.controller.param.UserPasswordLoginParam;
import org.example.lottery_system.controller.param.UserRegisterParam;
import org.example.lottery_system.controller.param.UserShortMessageLoginParam;
import org.example.lottery_system.dao.dataobject.Encrypt;
import org.example.lottery_system.dao.dataobject.UserDO;
import org.example.lottery_system.service.dto.UserDTO;
import org.example.lottery_system.dao.mapper.UserMapper;
import org.example.lottery_system.service.UserService;
import org.example.lottery_system.service.VerificationCodeService;
import org.example.lottery_system.service.dto.UserLoginDTO;
import org.example.lottery_system.service.dto.UserRegisterDTO;
import org.example.lottery_system.service.enums.UserIdentityEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {
    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private VerificationCodeService verificationCodeService;

    @Override
    public UserRegisterDTO register(UserRegisterParam param) {
        String phone = param.getPhoneNumber();
        log.info("{} 请求注册", phone);
        try {
            checkRegisterInfo(param);
            UserDO userDo = new UserDO();
            userDo.setUserName(param.getName());
            userDo.setEmail(param.getMail());
            userDo.setPhoneNumber(new Encrypt(param.getPhoneNumber()));
            userDo.setIdentity(param.getIdentity());
            if (StringUtils.hasText(param.getPassword())) {
                userDo.setPassword(DigestUtil.sha256Hex(param.getPassword()));
            }
            userMapper.insert(userDo);
            UserRegisterDTO userRegisterDTO = new UserRegisterDTO();
            userRegisterDTO.setUserId(userDo.getId());
            log.info("{} 请求成功", phone);
            return userRegisterDTO;
        } catch (ServiceException e) {
            log.warn("{} 请求失败: {}", phone, e.getMessage());
            throw e;
        }
    }

    @Override
    public UserLoginDTO login(UserLoginParam param) {
        UserLoginDTO userLoginDTO = new UserLoginDTO();
        //类型校验
        if (param instanceof UserPasswordLoginParam loginParam) {
            //密码登录
            userLoginDTO = loginByUserPassword(loginParam);
        } else if (param instanceof UserShortMessageLoginParam loginParam) {
            //短信验证码登录
            userLoginDTO = loginByShortMessage(loginParam);
        } else {
            throw new ServiceException(ServiceErrorCodeConstants.LOGIN_INFO_NOT_EXIST);
        }
        return userLoginDTO;
    }

    @Override
    public List<UserDTO> findUserInfo(UserIdentityEnum identity) {
        String identityString = null == identity ? null : identity.name();
        //查表
        List<UserDO> userDOList = userMapper.selectUserListByIdentity(identityString);
        //转换为DTO
        List<UserDTO> userDTOList = userDOList.stream()
                .map(userDO -> {
                    UserDTO userDTO = new UserDTO();
                    userDTO.setUserId(userDO.getId());
                    userDTO.setUserName(userDO.getUserName());
                    userDTO.setEmail(userDO.getEmail());
                    userDTO.setPhoneNumber(userDO.getPhoneNumber().getValue());
                    userDTO.setIdentity(UserIdentityEnum.forName(userDO.getIdentity()));
                    return userDTO;
                })
                .collect(Collectors.toList());
        return userDTOList;
    }

    //短信验证码登录
    private UserLoginDTO loginByShortMessage(UserShortMessageLoginParam loginParam) {
        if (!RegexUtil.checkMobile(loginParam.getLoginMobile())) {
            throw new ServiceException(ServiceErrorCodeConstants.PHONE_ERROR);
        }
        //获取用户数据
        UserDO userDo = userMapper.selectByPhoneNumber(new Encrypt(loginParam.getLoginMobile()));
        if (userDo == null) {
            throw new ServiceException(ServiceErrorCodeConstants.USER_INFO_IS_EMPTY);
        } else if (StringUtils.hasText(loginParam.getMandatoryIdentity())
                && !loginParam.getMandatoryIdentity().equalsIgnoreCase(userDo.getIdentity())) {
            throw new ServiceException(ServiceErrorCodeConstants.IDENTITY_ERROR);
        }
        //校验短信验证码
        String verificationCode = verificationCodeService.getVerificationCode(loginParam.getLoginMobile());
        if(!loginParam.getVerificationCode().equals(verificationCode)) {
            throw new ServiceException(ServiceErrorCodeConstants.VERIFICATION_CODE_ERROR);
        }
        verificationCodeService.clearVerificationCode(loginParam.getLoginMobile());
        //返回登录信息
        Map<String, Object> claim = new HashMap<>();
        claim.put("id", userDo.getId());
        claim.put("identity", userDo.getIdentity());
        String token = JWTUtil.genJwt(claim);

        UserLoginDTO userLoginDTO = new UserLoginDTO();
        userLoginDTO.setToken(token);
        userLoginDTO.setIdentity(UserIdentityEnum.forName(userDo.getIdentity()));
        return userLoginDTO;
    }

    //密码登录
    private UserLoginDTO loginByUserPassword(UserPasswordLoginParam loginParam) {
        UserDO userDo = null;
        //校验登录信息
        if (RegexUtil.checkMobile(loginParam.getLoginName())) {
            //手机号登录
            //校验用户是否存在
            userDo = userMapper.selectByPhoneNumber(new Encrypt(loginParam.getLoginName()));
        } else if (RegexUtil.checkMail(loginParam.getLoginName())) {
            //邮箱登录
            //校验用户是否存在
            userDo = userMapper.selectByEmail(loginParam.getLoginName());
        } else {
            throw new ServiceException(ServiceErrorCodeConstants.LOGIN_NOT_EXIST);
        }
        if (userDo == null) {
            throw new ServiceException(ServiceErrorCodeConstants.USER_INFO_IS_EMPTY);
        } else if (StringUtils.hasText(loginParam.getMandatoryIdentity()) &&
                !loginParam.getMandatoryIdentity().equalsIgnoreCase(userDo.getIdentity())) {
            //身份校验不通过
            throw new ServiceException(ServiceErrorCodeConstants.IDENTITY_ERROR);
        } else if (!DigestUtil.sha256Hex(loginParam.getPassword())
                .equals(userDo.getPassword())) {
            //密码校验不通过
            throw new ServiceException(ServiceErrorCodeConstants.PASSWORD_ERROR);
        }
        //返回登录信息
        Map<String, Object> claim = new HashMap<>();
        claim.put("id", userDo.getId());
        claim.put("identity", userDo.getIdentity());
        String token = JWTUtil.genJwt(claim);

        UserLoginDTO userLoginDTO = new UserLoginDTO();
        userLoginDTO.setToken(token);
        userLoginDTO.setIdentity(UserIdentityEnum.forName(userDo.getIdentity()));
        return userLoginDTO;
    }

    private void checkRegisterInfo(UserRegisterParam param) {
        if (param == null) {
            throw new ServiceException(ServiceErrorCodeConstants.REGISTER_ERROR);
        }
        //校验邮箱
        if (!RegexUtil.checkMail(param.getMail())) {
            throw new ServiceException(ServiceErrorCodeConstants.MAIL_ERROR);
        }
        //校验手机号
        if (!RegexUtil.checkMobile(param.getPhoneNumber())) {
            throw new ServiceException(ServiceErrorCodeConstants.PHONE_ERROR);
        }
        //校验身份信息
        if (UserIdentityEnum.forName(param.getIdentity()) == null) {
            throw new ServiceException(ServiceErrorCodeConstants.IDENTITY_ERROR);
        }
        //校验管理员密码(必填)
        if (param.getIdentity().equalsIgnoreCase(UserIdentityEnum.ADMIN.name())
                && !StringUtils.hasText(param.getPassword())) {
            throw new ServiceException(ServiceErrorCodeConstants.PASSWORD_EMPTY_ERROR);
        }
        //密码校验,至少6位
        if (StringUtils.hasText(param.getPassword()) && !RegexUtil.checkPassword(param.getPassword())) {
            throw new ServiceException(ServiceErrorCodeConstants.PASSWORD_FORMAT_ERROR);
        }
        //校验邮箱是否已注册
        if (checkMailUsed(param.getMail())) {
            throw new ServiceException(ServiceErrorCodeConstants.MAIL_USED_ERROR);
        }
        //校验手机号是否已注册
        if (checkPhoneNumberUsed(param.getPhoneNumber())) {
            throw new ServiceException(ServiceErrorCodeConstants.PHONE_USED_ERROR);
        }
    }

    //校验手机号是否已注册
    private boolean checkPhoneNumberUsed(@NotBlank(message = "手机号不能为空") String phoneNumber) {
        return userMapper.countByPhoneNumber(new Encrypt(phoneNumber)) > 0;
    }

    //校验邮箱是否已注册
    private boolean checkMailUsed(@NotBlank(message = "邮箱不能为空") String mail) {
        return userMapper.countByMail(mail) > 0;
    }
}
