package org.example.lottery_system.controller;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.example.lottery_system.common.errorcode.ControllerErrorCodeConstants;
import org.example.lottery_system.common.errorcode.ServiceErrorCodeConstants;
import org.example.lottery_system.common.exception.ControllerException;
import org.example.lottery_system.common.exception.ServiceException;
import org.example.lottery_system.common.pojo.CommonResult;
import org.example.lottery_system.common.utils.JacksonUtil;
import org.example.lottery_system.common.utils.JWTUtil;
import org.example.lottery_system.controller.param.UserPasswordLoginParam;
import org.example.lottery_system.controller.param.UserRegisterParam;
import org.example.lottery_system.controller.param.UserShortMessageLoginParam;
import org.example.lottery_system.controller.result.BaseUserInfoResult;
import org.example.lottery_system.controller.result.UserLoginResult;
import org.example.lottery_system.controller.result.UserRegisterResult;
import org.example.lottery_system.service.dto.UserDTO;
import org.example.lottery_system.service.UserService;
import org.example.lottery_system.service.VerificationCodeService;
import org.example.lottery_system.service.dto.UserLoginDTO;
import org.example.lottery_system.service.dto.UserRegisterDTO;
import org.example.lottery_system.service.enums.UserIdentityEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/user")
public class UserController {
    private static final Logger logger= LoggerFactory.getLogger(UserController.class);
    @Autowired
    private UserService userService;
    @Autowired
    private VerificationCodeService verificationCodeService;
    /*
    * 注册
    * */
    @PostMapping("/register")
    public CommonResult<UserRegisterResult> userRegister(@Validated @RequestBody UserRegisterParam param,
                                                         HttpServletRequest request) {
        applyRegisterIdentityPolicy(param, request);
        logger.info("userRegister param:{}", JacksonUtil.writeValueAsString(param));
        UserRegisterDTO userRegisterDTO = userService.register(param);
        return CommonResult.success(convertToUserRegisterResult(userRegisterDTO));
    }

    /**
     * 公开注册页（无有效管理员 token）只能创建管理员账号；
     * 已登录管理员代建时只能创建普通用户，不信任客户端传入的 identity。
     */
    private void applyRegisterIdentityPolicy(UserRegisterParam param, HttpServletRequest request) {
        String token = request.getHeader("token");
        Claims claims = JWTUtil.parseJWT(token);
        if (claims == null) {
            param.setIdentity(UserIdentityEnum.ADMIN.name());
            return;
        }
        Object identityClaim = claims.get("identity");
        String identity = identityClaim == null ? null : identityClaim.toString();
        if (UserIdentityEnum.ADMIN.name().equalsIgnoreCase(identity)) {
            param.setIdentity(UserIdentityEnum.NORMAL.name());
        } else {
            throw new ServiceException(ServiceErrorCodeConstants.REGISTER_FORBIDDEN);
        }
    }
    @PostMapping("/verification-code/send")
    public CommonResult<Boolean> verificationCode(@RequestParam("phoneNumber") String phoneNumber){
        boolean success=verificationCodeService.sendVerificationCode(phoneNumber);
        return CommonResult.success(success);
    }
    /*
    * 密码登录
    * */
    @PostMapping("/password/login")
    public CommonResult<UserLoginResult> userPasswordLogin(@Validated @RequestBody UserPasswordLoginParam param){
        logger.info("userPasswordLogin param:{}", JacksonUtil.writeValueAsString(param));
        UserLoginDTO userLoginDTO=userService.login(param);
        return CommonResult.success(convertToUserLoginResult(userLoginDTO));
    }
    /*
     * 短信登录
     * */
    @PostMapping("/message/login")
    public CommonResult<UserLoginResult> shortMessageLogin(@Validated @RequestBody UserShortMessageLoginParam param){
        logger.info("userShortMessageLogin param:{}", JacksonUtil.writeValueAsString(param));
        UserLoginDTO userLoginDTO=userService.login(param);
        return CommonResult.success(convertToUserLoginResult(userLoginDTO));
    }
    /*
     * 查询用户列表
     * */
    @GetMapping("/base-user/find-list")
    public  CommonResult<List<BaseUserInfoResult>> findBaseUserInfo(String identity){
        logger.info("findBaseUserInfo identity:{}", identity);
        List<UserDTO> userDTOList=userService.findUserInfo(UserIdentityEnum.forName(identity));
        return CommonResult.success(convertToList(userDTOList));
    }

    private List<BaseUserInfoResult> convertToList(List<UserDTO> userDTOList) {
        if(CollectionUtils.isEmpty(userDTOList)){
            return Arrays.asList();
        }

        return userDTOList.stream()
                .map(userDTO -> {
                    BaseUserInfoResult baseUserInfoResult=new BaseUserInfoResult();
                    baseUserInfoResult.setUserId(userDTO.getUserId());
                    baseUserInfoResult.setUserName(userDTO.getUserName());
                    baseUserInfoResult.setIdentity(userDTO.getIdentity().name());
                    return baseUserInfoResult;
                })
                .collect(Collectors.toList());
    }

    private UserLoginResult convertToUserLoginResult(UserLoginDTO userLoginDTO) {
        if (userLoginDTO == null) {
            throw new ServiceException(ControllerErrorCodeConstants.LOGIN_ERROR);
        }
        UserLoginResult userLoginResult=new UserLoginResult();
        userLoginResult.setToken(userLoginDTO.getToken());
        userLoginResult.setIdentity(userLoginDTO.getIdentity().name());
        return userLoginResult;
    }


    private UserRegisterResult convertToUserRegisterResult(UserRegisterDTO userRegisterDTO) {
        UserRegisterResult userRegisterResult=new UserRegisterResult();
        if(userRegisterDTO==null){
            throw new ControllerException(ControllerErrorCodeConstants.REGISTER_ERROR);
        }
        userRegisterResult.setUserId(userRegisterDTO.getUserId());
        return userRegisterResult;
    }
}
