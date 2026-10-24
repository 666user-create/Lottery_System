package org.example.lottery_system.controller;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.example.lottery_system.common.errorcode.ControllerErrorCodeConstants;
import org.example.lottery_system.common.errorcode.GlobalErrorCodeConstants;
import org.example.lottery_system.common.errorcode.ServiceErrorCodeConstants;
import org.example.lottery_system.common.exception.ControllerException;
import org.example.lottery_system.common.exception.ServiceException;
import org.example.lottery_system.common.pojo.CommonResult;
import org.example.lottery_system.common.utils.JacksonUtil;
import org.example.lottery_system.common.utils.JWTUtil;
import org.example.lottery_system.controller.param.ReceiverInfoParam;
import org.example.lottery_system.controller.param.UpdateEmailParam;
import org.example.lottery_system.controller.param.UpdatePasswordParam;
import org.example.lottery_system.controller.param.UserPasswordLoginParam;
import org.example.lottery_system.controller.param.UserRegisterParam;
import org.example.lottery_system.controller.param.UserShortMessageLoginParam;
import org.example.lottery_system.controller.result.AccountSecurityResult;
import org.example.lottery_system.controller.result.BaseUserInfoResult;
import org.example.lottery_system.controller.result.ReceiverInfoResult;
import org.example.lottery_system.controller.result.UserDetailResult;
import org.example.lottery_system.controller.result.UserLoginResult;
import org.example.lottery_system.controller.result.UserRegisterResult;
import org.example.lottery_system.dao.dataobject.UserDO;
import org.example.lottery_system.service.ClaimService;
import org.example.lottery_system.service.dto.UserDTO;
import org.example.lottery_system.service.UserService;
import org.example.lottery_system.service.VerificationCodeService;
import org.example.lottery_system.service.dto.UserLoginDTO;
import org.example.lottery_system.service.dto.UserRegisterDTO;
import org.example.lottery_system.service.enums.UserIdentityEnum;
import org.example.lottery_system.service.enums.VerificationSceneEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
    /** 领奖履约服务:收货地址的读写 */
    @Autowired
    private ClaimService claimService;
    /**
     * 管理员口令。原先硬编码为 "lottery-admin-2024"，现统一从配置读取，
     * 与 UserServiceImpl 的 verifyType=secret 校验共用同一个值。
     */
    @Value("${admin.secret}")
    private String adminSecretConfig;
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
     * 公开注册页无 token 默认创建普通用户，携带 X-Admin-Secret 正确口令可创建管理员；
     * 已登录管理员代建时默认创建普通用户，携带 X-Admin-Secret 口令可创建管理员；
     * 普通用户不允许创建任何账号。
     */
    private void applyRegisterIdentityPolicy(UserRegisterParam param, HttpServletRequest request) {
        String token = request.getHeader("token");
        String adminSecret = request.getHeader("X-Admin-Secret");
        // 与 UserServiceImpl 的 verifyType=secret 共用同一配置项，避免两份常量不同步
        boolean hasSecret = adminSecretConfig != null && adminSecretConfig.equals(adminSecret);
        Claims claims = JWTUtil.parseJWT(token);
        if (claims == null) {
            // 无登录态：携带正确口令 → ADMIN，否则 → NORMAL
            param.setIdentity(hasSecret ? UserIdentityEnum.ADMIN.name() : UserIdentityEnum.NORMAL.name());
            return;
        }
        Object identityClaim = claims.get("identity");
        String identity = identityClaim == null ? null : identityClaim.toString();
        if (UserIdentityEnum.ADMIN.name().equalsIgnoreCase(identity)) {
            // 管理员代建：携带正确口令才允许创建管理员，否则默认创建普通用户
            param.setIdentity(hasSecret ? UserIdentityEnum.ADMIN.name() : UserIdentityEnum.NORMAL.name());
        } else {
            throw new ServiceException(ServiceErrorCodeConstants.REGISTER_FORBIDDEN);
        }
    }
    /*
     * 登录场景发短信验证码（公开接口）
     * */
    @PostMapping("/verification-code/send")
    public CommonResult<Boolean> verificationCode(@RequestParam("phoneNumber") String phoneNumber){
        boolean success=verificationCodeService.sendVerificationCode(
                phoneNumber, VerificationSceneEnum.LOGIN);
        return CommonResult.success(success);
    }

    /*
     * 账号安全场景发短信验证码（需登录）
     *
     * 与登录发码关键差异：
     *   1. 必须带 token，服务端据此查出**本人**手机号，前端传不了手机号；
     *   2. 固定使用 SECURITY 场景，登录码无法用于改邮箱/改密码。
     * 若不做第 1 点，任何人都能对任意手机号发码；不做第 2 点，隔离失效。
     * */
    @PostMapping("/security/verification-code/send")
    public CommonResult<Boolean> securityVerificationCode(HttpServletRequest request) {
        Long userId = requireLoginUserId(request);
        logger.info("账号安全发码 userId:{} 手机号:{}", userId, userService.getMaskedPhoneNumber(userId));
        return CommonResult.success(userService.sendSecurityVerificationCode(userId));
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

    /*
     * 查询单个用户详情（仅管理员，路径已在 LoginInterceptor 登记 adminOnly）。
     * 返回除密码外的全部信息：基本资料 + 收货信息 + 注册/修改时间。
     * 密码属凭证，任何情况下都不返回。
     * */
    @GetMapping("/base-user/find-detail")
    public CommonResult<UserDetailResult> findUserDetail(@RequestParam("userId") Long userId) {
        logger.info("查询用户详情 userId:{}", userId);
        UserDTO userDTO = userService.findDetailById(userId);
        if (userDTO == null) {
            return CommonResult.error(ServiceErrorCodeConstants.USER_NOT_EXIST.getCode(),
                    ServiceErrorCodeConstants.USER_NOT_EXIST.getMsg());
        }
        return CommonResult.success(convertToUserDetailResult(userDTO));
    }

    /*
     * 启用/停用账号（仅管理员，路径已在 LoginInterceptor 登记 adminOnly）。
     * status 取 ACTIVE / DISABLED；停用会同时被 LoginInterceptor 拦掉该用户已签发的旧 token。
     * */
    @PostMapping("/status/update")
    public CommonResult<Boolean> updateUserStatus(@RequestParam("userId") Long targetUserId,
                                                  @RequestParam("status") String status,
                                                  HttpServletRequest request) {
        Long operatorId = requireLoginUserId(request);
        logger.info("更新账号状态 targetId:{} status:{}", targetUserId, status);
        userService.changeUserStatus(operatorId, targetUserId, status);
        return CommonResult.success(true);
    }
    /*
     * 查询当前登录用户信息
     * */
    @GetMapping("/me")
    public CommonResult<BaseUserInfoResult> currentUser(HttpServletRequest request) {
        Claims claims = JWTUtil.parseJWT(request.getHeader("token"));
        if (claims == null) {
            return CommonResult.error(GlobalErrorCodeConstants.UNAUTHORIZED.getCode(),
                    GlobalErrorCodeConstants.UNAUTHORIZED.getMsg());
        }
        Object idObj = claims.get("id");
        Long userId = idObj instanceof Long ? (Long) idObj : Long.valueOf(idObj.toString());
        UserDTO userDTO = userService.findById(userId);
        if (userDTO == null) {
            return CommonResult.error(GlobalErrorCodeConstants.UNAUTHORIZED.getCode(), "用户不存在");
        }
        BaseUserInfoResult result = new BaseUserInfoResult();
        result.setUserId(userDTO.getUserId());
        result.setUserName(userDTO.getUserName());
        result.setIdentity(userDTO.getIdentity().name());
        return CommonResult.success(result);
    }

    /*
     * 修改邮箱
     * 支持两种验证方式（二选一）：
     *   1. verifyType=password 输入原密码
     *   2. verifyType=code     使用登录时的阿里云短信验证码
     * */
    @PostMapping("/email/update")
    public CommonResult<Boolean> updateEmail(@Validated @RequestBody UpdateEmailParam param,
                                             HttpServletRequest request) {
        logger.info("updateEmail verifyType:{}", param.getVerifyType());
        Long userId = requireLoginUserId(request);
        return CommonResult.success(userService.updateEmail(userId, param));
    }

    /*
     * 修改密码
     * 支持两种验证方式（二选一）：
     *   1. verifyType=password 输入原密码
     *   2. verifyType=code     使用登录时的阿里云短信验证码
     * */
    @PostMapping("/password/update")
    public CommonResult<Boolean> updatePassword(@Validated @RequestBody UpdatePasswordParam param,
                                                HttpServletRequest request) {
        logger.info("updatePassword verifyType:{}", param.getVerifyType());
        Long userId = requireLoginUserId(request);
        return CommonResult.success(userService.updatePassword(userId, param));
    }

    /*
     * 账号安全页所需的补充信息：当前邮箱 + 脱敏手机号
     * 前端据此回填「当前邮箱」，并在选中验证码方式时展示「验证码将发送至 138****8000」，
     * 避免让用户自己填手机号（明文手机号永远不会出现在响应体里）
     * */
    @GetMapping("/account/security")
    public CommonResult<AccountSecurityResult> accountSecurity(HttpServletRequest request) {
        Long userId = requireLoginUserId(request);
        UserDTO user = userService.findById(userId);
        AccountSecurityResult result = new AccountSecurityResult();
        result.setEmail(user == null ? null : user.getEmail());
        result.setMaskedPhoneNumber(userService.getMaskedPhoneNumber(userId));
        return CommonResult.success(result);
    }

    /**
     * 查询收货信息(个人中心 → 收货地址)
     * GET /user/receiver-info
     */
    @GetMapping("/receiver-info")
    public CommonResult<ReceiverInfoResult> getReceiverInfo(HttpServletRequest request) {
        Long userId = requireLoginUserId(request);
        UserDO user = claimService.getReceiverInfo(userId);
        ReceiverInfoResult result = new ReceiverInfoResult();
        if (user != null) {
            result.setReceiverName(user.getReceiverName());
            result.setReceiverPhone(user.getReceiverPhone() == null ? null : user.getReceiverPhone().getValue());
            result.setReceiverAddress(user.getReceiverAddress());
        }
        result.setComplete(org.springframework.util.StringUtils.hasText(result.getReceiverName())
                && org.springframework.util.StringUtils.hasText(result.getReceiverPhone())
                && org.springframework.util.StringUtils.hasText(result.getReceiverAddress()));
        return CommonResult.success(result);
    }

    /**
     * 保存收货信息(个人中心 → 收货地址)
     * POST /user/receiver-info  body: {"receiverName":"张三","receiverPhone":"138...","receiverAddress":"..."}
     */
    @PostMapping("/receiver-info")
    public CommonResult<Boolean> saveReceiverInfo(@RequestBody ReceiverInfoParam param, HttpServletRequest request) {
        Long userId = requireLoginUserId(request);
        if (param == null
                || !org.springframework.util.StringUtils.hasText(param.getReceiverName())
                || !org.springframework.util.StringUtils.hasText(param.getReceiverPhone())
                || !org.springframework.util.StringUtils.hasText(param.getReceiverAddress())) {
            throw new ServiceException(ServiceErrorCodeConstants.RECEIVER_INFO_INCOMPLETE);
        }
        claimService.saveReceiverInfo(userId, param.getReceiverName(),
                param.getReceiverPhone(), param.getReceiverAddress());
        return CommonResult.success(true);
    }

    /** 从 token 中取当前用户ID，未登录直接抛 401（交由全局异常处理） */
    private Long requireLoginUserId(HttpServletRequest request) {
        Claims claims = JWTUtil.parseJWT(request.getHeader("token"));
        if (claims == null) {
            throw new ServiceException(GlobalErrorCodeConstants.UNAUTHORIZED);
        }
        Object idObj = claims.get("id");
        if (idObj == null) {
            throw new ServiceException(GlobalErrorCodeConstants.UNAUTHORIZED);
        }
        return idObj instanceof Long ? (Long) idObj : Long.valueOf(idObj.toString());
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
                    baseUserInfoResult.setStatus(userDTO.getStatus());
                    return baseUserInfoResult;
                })
                .collect(Collectors.toList());
    }

    /**
     * 用户详情转换（除密码外全字段）。
     * 身份/状态额外补一份中文文案，前端不用自己维护映射表。
     */
    private UserDetailResult convertToUserDetailResult(UserDTO userDTO) {
        UserDetailResult detail = new UserDetailResult();
        detail.setUserId(userDTO.getUserId());
        detail.setUserName(userDTO.getUserName());
        detail.setEmail(userDTO.getEmail());
        detail.setPhoneNumber(userDTO.getPhoneNumber());
        detail.setIdentity(userDTO.getIdentity() == null ? null : userDTO.getIdentity().name());
        detail.setIdentityText(userDTO.getIdentity() == null ? null : userDTO.getIdentity().getMessage());
        detail.setStatus(userDTO.getStatus());
        detail.setStatusText("DISABLED".equals(userDTO.getStatus()) ? "已停用" : "正常");
        detail.setReceiverName(userDTO.getReceiverName());
        detail.setReceiverPhone(userDTO.getReceiverPhone());
        detail.setReceiverAddress(userDTO.getReceiverAddress());
        detail.setGmtCreate(userDTO.getGmtCreate());
        detail.setGmtModified(userDTO.getGmtModified());
        return detail;
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
