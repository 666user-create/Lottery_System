package org.example.lottery_system.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import jakarta.validation.constraints.NotBlank;
import org.example.lottery_system.common.errorcode.ServiceErrorCodeConstants;
import org.example.lottery_system.common.exception.ServiceException;
import org.example.lottery_system.common.utils.JWTUtil;
import org.example.lottery_system.common.utils.RegexUtil;
import org.example.lottery_system.controller.param.AccountVerifyParam;
import org.example.lottery_system.controller.param.UpdateEmailParam;
import org.example.lottery_system.controller.param.UpdatePasswordParam;
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
import org.example.lottery_system.service.enums.UserStatusEnum;
import org.example.lottery_system.service.enums.VerificationSceneEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
                    userDTO.setStatus(userDO.getStatus());
                    return userDTO;
                })
                .collect(Collectors.toList());
        return userDTOList;
    }

    @Override
    public UserDTO findById(Long userId) {
        UserDO userDO = userMapper.selectById(userId);
        if (userDO == null) {
            return null;
        }
        UserDTO userDTO = new UserDTO();
        userDTO.setUserId(userDO.getId());
        userDTO.setUserName(userDO.getUserName());
        userDTO.setEmail(userDO.getEmail());
        userDTO.setPhoneNumber(userDO.getPhoneNumber().getValue());
        userDTO.setIdentity(UserIdentityEnum.forName(userDO.getIdentity()));
        userDTO.setStatus(userDO.getStatus());
        return userDTO;
    }

    /**
     * 用户详情：除密码外的全部信息（基本资料 + 收货信息 + 时间戳）。
     *
     * 与 findById 的区别：findById 只填 5 个基础字段（/user/me 等轻量场景用），
     * 这里额外补上收货信息与 gmt_create / gmt_modified，供管理端详情页展示。
     * 密码字段一律不读不返回。
     */
    @Override
    public UserDTO findDetailById(Long userId) {
        UserDO userDO = userMapper.selectById(userId);
        if (userDO == null) {
            return null;
        }
        UserDTO userDTO = new UserDTO();
        userDTO.setUserId(userDO.getId());
        userDTO.setUserName(userDO.getUserName());
        userDTO.setEmail(userDO.getEmail());
        // Encrypt 是自定义 TypeHandler 的载体，明文取自 getValue()；手机号/收货电话都可能为空
        userDTO.setPhoneNumber(userDO.getPhoneNumber() == null ? null : userDO.getPhoneNumber().getValue());
        userDTO.setIdentity(UserIdentityEnum.forName(userDO.getIdentity()));
        userDTO.setStatus(userDO.getStatus());
        userDTO.setReceiverName(userDO.getReceiverName());
        userDTO.setReceiverPhone(userDO.getReceiverPhone() == null ? null : userDO.getReceiverPhone().getValue());
        userDTO.setReceiverAddress(userDO.getReceiverAddress());
        userDTO.setGmtCreate(userDO.getGmtCreate());
        userDTO.setGmtModified(userDO.getGmtModified());
        return userDTO;
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
        }
        requireActive(userDo);
        if (StringUtils.hasText(loginParam.getMandatoryIdentity())
                && !loginParam.getMandatoryIdentity().equalsIgnoreCase(userDo.getIdentity())) {
            throw new ServiceException(ServiceErrorCodeConstants.IDENTITY_ERROR);
        }
        //校验短信验证码（登录场景，与账号安全场景的验证码互不通用）
        String verificationCode = verificationCodeService.getVerificationCode(
                loginParam.getLoginMobile(), VerificationSceneEnum.LOGIN);
        if (!VerificationCodeService.matches(loginParam.getVerificationCode(), verificationCode)) {
            throw new ServiceException(ServiceErrorCodeConstants.VERIFICATION_CODE_ERROR);
        }
        verificationCodeService.clearVerificationCode(loginParam.getLoginMobile(), VerificationSceneEnum.LOGIN);
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
        }
        requireActive(userDo);
        if (StringUtils.hasText(loginParam.getMandatoryIdentity()) &&
                !loginParam.getMandatoryIdentity().equalsIgnoreCase(userDo.getIdentity())) {
            //身份校验不通过
            throw new ServiceException(ServiceErrorCodeConstants.IDENTITY_ERROR);
        }
        if (!DigestUtil.sha256Hex(loginParam.getPassword()).equals(userDo.getPassword())) {
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

    // ==================== 账号安全：修改邮箱 / 修改密码 ====================

    /** 验证方式：原密码 */
    public static final String VERIFY_TYPE_PASSWORD = "password";
    /** 验证方式：短信验证码（登录时使用的阿里云短信服务） */
    public static final String VERIFY_TYPE_CODE = "code";
    /** 验证方式：管理员口令（仅 ADMIN 可用于改自己的邮箱/密码） */
    public static final String VERIFY_TYPE_SECRET = "secret";

    /**
     * 管理员口令。与 UserController 的注册授权共用同一个配置项，
     * 保证「注册管理员」与「口令验证改凭证」不会出现两份不同步的常量。
     */
    @Value("${admin.secret}")
    private String adminSecret;

    @Override
    public String getMaskedPhoneNumber(Long userId) {
        UserDO userDo = userMapper.selectById(userId);
        if (userDo == null || userDo.getPhoneNumber() == null) {
            return null;
        }
        String phone = userDo.getPhoneNumber().getValue();
        if (!StringUtils.hasText(phone) || phone.length() < 7) {
            return null;
        }
        // 脱敏：138****8000
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    @Override
    public boolean sendSecurityVerificationCode(Long userId) {
        UserDO userDo = requireUser(userId);
        if (userDo.getPhoneNumber() == null || !StringUtils.hasText(userDo.getPhoneNumber().getValue())) {
            throw new ServiceException(ServiceErrorCodeConstants.PHONE_ERROR);
        }
        // 固定 SECURITY 场景，登录码不能用于改邮箱/改密码
        return verificationCodeService.sendVerificationCode(
                userDo.getPhoneNumber().getValue(), VerificationSceneEnum.SECURITY);
    }

    /*
     * 防并发互停：两个管理员在同一时刻互相停用对方时，各自都读到 count=2、都通过校验，
     * 两条 update 都执行后两个管理员同时被停用 —— 全系统再无人能启用账号。
     *
     * 解法是「按主键升序锁住全部管理员行」而非「锁目标行」：
     * 锁目标行时 A 锁 B、B 锁 A 互不阻塞，且互相等对方的目标行还会死锁。
     * 锁同一批行则两次停用彻底串行，后到的那次读到的 status 已被前一次改写，
     * 于是正确地被 131 拦下。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeUserStatus(Long operatorId, Long targetUserId, String status) {
        // 1. 目标状态必须是已知枚举，避免把任意字符串写进 status 列
        UserStatusEnum targetStatus = UserStatusEnum.forName(status);
        if (targetStatus == null) {
            throw new ServiceException(ServiceErrorCodeConstants.USER_STATUS_INVALID);
        }

        // 2. 目标账号必须存在
        UserDO target = userMapper.selectById(targetUserId);
        if (target == null) {
            throw new ServiceException(ServiceErrorCodeConstants.LOGIN_INFO_NOT_EXIST);
        }

        // 3. 幂等：已经是目标状态就直接返回，不产生多余日志与更新
        if (targetStatus.name().equals(target.getStatus())) {
            return;
        }

        // 4. 停用时的两条防锁死校验（启用不涉及）
        if (targetStatus == UserStatusEnum.DISABLED) {
            if (targetUserId.equals(operatorId)) {
                throw new ServiceException(ServiceErrorCodeConstants.CANNOT_DISABLE_SELF);
            }
            // 停用对象本身是管理员时，必须保证还剩至少一个可用管理员，
            // 否则整个系统再没人能启用账号、创建管理员，等于把自己锁死。
            if (UserIdentityEnum.ADMIN.name().equalsIgnoreCase(target.getIdentity())) {
                // 在内存里数加锁后读到的这份列表：与锁是同一份数据、同一时刻，
                // 另发 count(*) 会读到事务快照而使加锁失效。
                // 判定用 isActive()，与 LoginInterceptor / UserStatusEnum 完全同一套判法。
                long activeAdmins = userMapper.selectAdminRowsForUpdate().stream()
                        .filter(row -> UserStatusEnum.isActive(row.getStatus()))
                        .count();
                if (activeAdmins <= 1) {
                    throw new ServiceException(ServiceErrorCodeConstants.CANNOT_DISABLE_LAST_ADMIN);
                }
            }
        }

        userMapper.updateStatus(targetUserId, targetStatus.name());
        log.info("账号状态变更: operatorId={} targetId={} -> {}", operatorId, targetUserId, targetStatus);
    }

    @Override
    public boolean updateEmail(Long userId, UpdateEmailParam param) {
        UserDO userDo = requireUser(userId);
        String newEmail = param.getNewEmail() == null ? null : param.getNewEmail().trim();
        log.info("用户 {} 请求修改邮箱", userId);

        // 1. 邮箱格式
        if (!RegexUtil.checkMail(newEmail)) {
            throw new ServiceException(ServiceErrorCodeConstants.MAIL_ERROR);
        }
        // 2. 与原邮箱相同则无需修改
        if (newEmail.equalsIgnoreCase(userDo.getEmail())) {
            throw new ServiceException(ServiceErrorCodeConstants.MAIL_SAME_AS_OLD);
        }
        // 3. 新邮箱不能被他人占用
        UserDO occupied = userMapper.selectByEmail(newEmail);
        if (occupied != null && !occupied.getId().equals(userId)) {
            throw new ServiceException(ServiceErrorCodeConstants.MAIL_USED_ERROR);
        }
        // 4. 验证身份（原密码 或 短信验证码）
        verifyIdentity(userDo, param);

        // 5. 落库。验证码在验证成功后才消费，避免「验证失败却已失效」
        try {
            userMapper.updateEmail(userId, newEmail);
        } catch (DuplicateKeyException e) {
            // user.email 上有唯一索引 uk_email，并发下这里的应用层查重可能被穿透，
            // 由数据库兜底拒绝。翻译成业务错误码，避免用户看到 500。
            log.warn("修改邮箱触发唯一索引冲突 userId:{}", userId);
            throw new ServiceException(ServiceErrorCodeConstants.MAIL_USED_ERROR);
        }
        consumeVerificationCode(userDo, param);
        log.info("用户 {} 修改邮箱成功", userId);
        return true;
    }

    @Override
    public boolean updatePassword(Long userId, UpdatePasswordParam param) {
        UserDO userDo = requireUser(userId);
        log.info("用户 {} 请求修改密码", userId);

        // 1. 密码格式（复用注册时的 6-12 位字母数字规则）
        if (!RegexUtil.checkPassword(param.getNewPassword())) {
            throw new ServiceException(ServiceErrorCodeConstants.PASSWORD_FORMAT_ERROR);
        }
        // 2. 验证身份（原密码 或 短信验证码）
        verifyIdentity(userDo, param);

        // 3. 新密码不得与原密码相同。
        //    注意：若账号注册时未设密码（password 为空），此校验自然跳过。
        if (StringUtils.hasText(userDo.getPassword())
                && DigestUtil.sha256Hex(param.getNewPassword()).equals(userDo.getPassword())) {
            throw new ServiceException(ServiceErrorCodeConstants.NEW_PASSWORD_SAME_AS_OLD);
        }

        // 4. 落库
        userMapper.updatePassword(userId, DigestUtil.sha256Hex(param.getNewPassword()));
        consumeVerificationCode(userDo, param);
        log.info("用户 {} 修改密码成功", userId);
        return true;
    }

    /** 取用户，不存在则视为登录态失效 */
    private UserDO requireUser(Long userId) {
        UserDO userDo = userMapper.selectById(userId);
        if (userDo == null) {
            throw new ServiceException(ServiceErrorCodeConstants.USER_INFO_IS_EMPTY);
        }
        return userDo;
    }

    /** 停用的账号不允许登录、也不允许改自己的凭证 */
    private void requireActive(UserDO userDo) {
        if (userDo != null && !UserStatusEnum.isActive(userDo.getStatus())) {
            throw new ServiceException(ServiceErrorCodeConstants.USER_DISABLED);
        }
    }

    /**
     * 按 verifyType 校验身份。三种方式互斥，且都只针对本人：
     * 原密码/口令由服务端与库中记录比对，短信验证码只认服务端查出的本人手机号。
     */
    private void verifyIdentity(UserDO userDo, AccountVerifyParam param) {
        String verifyType = param.getVerifyType();
        if (VERIFY_TYPE_PASSWORD.equalsIgnoreCase(verifyType)) {
            verifyByPassword(userDo, param.getOldPassword());
        } else if (VERIFY_TYPE_CODE.equalsIgnoreCase(verifyType)) {
            verifyByCode(userDo, param.getVerificationCode());
        } else if (VERIFY_TYPE_SECRET.equalsIgnoreCase(verifyType)) {
            verifyBySecret(userDo, param.getAdminSecret());
        } else {
            throw new ServiceException(ServiceErrorCodeConstants.VERIFY_TYPE_ERROR);
        }
    }

    /**
     * 管理员口令验证。
     * 仅管理员可用：口令是所有管理员共用的常量，若放开给普通用户，
     * 等于给整个用户群体开了一条绕过「原密码 / 本人手机」的通道。
     */
    private void verifyBySecret(UserDO userDo, String secret) {
        if (!UserIdentityEnum.ADMIN.name().equalsIgnoreCase(userDo.getIdentity())) {
            throw new ServiceException(ServiceErrorCodeConstants.SECRET_ONLY_ADMIN);
        }
        if (!StringUtils.hasText(secret)) {
            throw new ServiceException(ServiceErrorCodeConstants.SECRET_EMPTY);
        }
        if (!secret.equals(adminSecret)) {
            throw new ServiceException(ServiceErrorCodeConstants.SECRET_ERROR);
        }
    }

    /** 原密码验证 */
    private void verifyByPassword(UserDO userDo, String oldPassword) {
        if (!StringUtils.hasText(oldPassword)) {
            throw new ServiceException(ServiceErrorCodeConstants.OLD_PASSWORD_EMPTY);
        }
        // 注册时未设密码的账号无法用密码验证，提示走短信验证码
        if (!StringUtils.hasText(userDo.getPassword())) {
            throw new ServiceException(ServiceErrorCodeConstants.PASSWORD_NOT_SET);
        }
        if (!DigestUtil.sha256Hex(oldPassword).equals(userDo.getPassword())) {
            throw new ServiceException(ServiceErrorCodeConstants.PASSWORD_ERROR);
        }
    }

    /**
     * 短信验证码验证。
     * 验证目标一律取用户本人的手机号（数据库中已由 TypeHandler 解密），
     * 不接受客户端传入，防止用他人手机号收到的验证码改他人账号。
     */
    private void verifyByCode(UserDO userDo, String inputCode) {
        if (!StringUtils.hasText(inputCode)) {
            throw new ServiceException(ServiceErrorCodeConstants.VERIFICATION_CODE_EMPTY);
        }
        if (userDo.getPhoneNumber() == null || !StringUtils.hasText(userDo.getPhoneNumber().getValue())) {
            throw new ServiceException(ServiceErrorCodeConstants.PHONE_ERROR);
        }
        String phone = userDo.getPhoneNumber().getValue();
        // 账号安全场景：登录码无法用于此处
        String cached = verificationCodeService.getVerificationCode(phone, VerificationSceneEnum.SECURITY);
        // 未发送 / 已过期 / 不匹配 统一返回验证码错误
        if (!VerificationCodeService.matches(inputCode, cached)) {
            throw new ServiceException(ServiceErrorCodeConstants.VERIFICATION_CODE_ERROR);
        }
    }

    /** 验证通过并成功落库后，删除验证码，避免同一验证码被重复使用 */
    private void consumeVerificationCode(UserDO userDo, AccountVerifyParam param) {
        if (VERIFY_TYPE_CODE.equalsIgnoreCase(param.getVerifyType())
                && userDo.getPhoneNumber() != null) {
            verificationCodeService.clearVerificationCode(
                    userDo.getPhoneNumber().getValue(), VerificationSceneEnum.SECURITY);
        }
    }
}
