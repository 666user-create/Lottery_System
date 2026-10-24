package org.example.lottery_system.common.errorcode;

public interface ServiceErrorCodeConstants {
    //人员模块错误
    ErrorCode REGISTER_ERROR = new ErrorCode(100, "注册失败");
    ErrorCode MAIL_ERROR = new ErrorCode(101, "邮箱校验失败");
    ErrorCode PHONE_ERROR = new ErrorCode(102, "手机号校验失败");
    ErrorCode IDENTITY_ERROR = new ErrorCode(103, "身份校验失败");
    ErrorCode PASSWORD_EMPTY_ERROR = new ErrorCode(104, "密码为空");
    ErrorCode PASSWORD_FORMAT_ERROR = new ErrorCode(105, "密码格式错误");
    ErrorCode MAIL_USED_ERROR = new ErrorCode(106, "邮箱已注册");
    ErrorCode PHONE_USED_ERROR = new ErrorCode(107, "手机号已注册");
    ErrorCode REGISTER_FORBIDDEN = new ErrorCode(113, "仅管理员可在后台代建普通用户");
    ErrorCode LOGIN_INFO_NOT_EXIST=new ErrorCode(108, "登录信息不存在");
    ErrorCode LOGIN_NOT_EXIST=new ErrorCode(109, "登录不存在");
    ErrorCode USER_INFO_IS_EMPTY=new ErrorCode(110, "用户信息为空");
    ErrorCode PASSWORD_ERROR = new ErrorCode(111, "密码校验失败");
    ErrorCode VERIFICATION_CODE_ERROR = new ErrorCode(112, "验证码校验失败");
    ErrorCode PHONE_NOT_ADMIN = new ErrorCode(114, "该手机号未登记为管理员账号");
    ErrorCode SMS_SEND_TOO_FREQUENT = new ErrorCode(115, "发送过于频繁，请稍后再试");
    ErrorCode SMS_SEND_HOUR_LIMIT = new ErrorCode(116, "验证码发送次数已达上限，请一小时后再试");
    ErrorCode SMS_SEND_FAILED = new ErrorCode(117, "短信发送失败，请稍后重试");
    // 修复 BUG-005：手机号未注册需独立错误码
    // 原实现复用 114（"该手机号未登记为管理员账号"），对"未注册"场景文案误导；
    // 实测已注册普通用户可正常发码（功能无缺陷），故仅新增语义明确的错误码
    ErrorCode PHONE_NOT_REGISTERED = new ErrorCode(118, "该手机号尚未注册，请先注册后再登录");
    //账号安全（修改邮箱/密码）所需错误码
    ErrorCode OLD_PASSWORD_EMPTY = new ErrorCode(119, "请输入原密码");
    ErrorCode PASSWORD_NOT_SET = new ErrorCode(120, "该账号未设置密码，请改用短信验证码验证");
    ErrorCode NEW_PASSWORD_SAME_AS_OLD = new ErrorCode(121, "新密码不能与原密码相同");
    ErrorCode VERIFY_TYPE_ERROR = new ErrorCode(122, "验证方式不正确");
    ErrorCode MAIL_SAME_AS_OLD = new ErrorCode(123, "新邮箱与当前邮箱相同");
    ErrorCode VERIFICATION_CODE_EMPTY = new ErrorCode(124, "请输入验证码");
    ErrorCode UPDATE_NO_CHANGE = new ErrorCode(125, "未做任何修改");
    //管理员口令验证（verifyType=secret）——仅管理员可用于改自己的邮箱/密码
    ErrorCode SECRET_EMPTY = new ErrorCode(126, "请输入管理员口令");
    ErrorCode SECRET_ERROR = new ErrorCode(127, "管理员口令不正确");
    ErrorCode SECRET_ONLY_ADMIN = new ErrorCode(128, "仅管理员可使用口令验证");
    //账号停用（软删除）所需错误码
    ErrorCode USER_DISABLED = new ErrorCode(129, "账号已停用，请联系管理员");
    ErrorCode CANNOT_DISABLE_SELF = new ErrorCode(130, "不能停用自己的账号");
    ErrorCode CANNOT_DISABLE_LAST_ADMIN = new ErrorCode(131, "不能停用最后一个管理员账号");
    // 原先非法 status 复用 122「验证方式不正确」，语义错位：
    // 122 专供改邮箱/改密码的 verifyType，与账号状态无关，用户看不懂
    ErrorCode USER_STATUS_INVALID = new ErrorCode(132, "账号状态值不合法");
    /** 管理端查用户详情时目标 id 不存在（列表是实时的，可能已被删/改） */
    ErrorCode USER_NOT_EXIST = new ErrorCode(133, "用户不存在或已被删除");
    //活动模块错误
    ErrorCode CREATE_ACTIVITY_EMPTY = new ErrorCode(200, "创建活动参数为空");
    ErrorCode ACTIVITY_USER_ERROR = new ErrorCode(201, "活动关联人员异常");
    ErrorCode ACTIVITY_PRIZE_ERROR = new ErrorCode(202, "活动关联奖品异常");
    ErrorCode ACTIVITY_USER_PRIZE_ERROR = new ErrorCode(203, "人员奖品数量不合适");
    ErrorCode ACTIVITY_PRIZE_TIER_ERROR = new ErrorCode(204, "活动奖品等级异常");
    ErrorCode ACTIVITY_STATUS_CONVERT_ERROR = new ErrorCode(205, "活动相关状态转换异常");
    ErrorCode CACHE_ACTIVITY_NOT_EXIST = new ErrorCode(206, "缓存活动id不存在");
    // 活动管理（结束/删除）所需错误码
    ErrorCode ACTIVITY_ID_IS_EMPTY = new ErrorCode(207, "活动id不能为空");
    ErrorCode ACTIVITY_NOT_EXIST = new ErrorCode(208, "活动不存在或已被删除");
    //奖品模块错误
    ErrorCode PRIZE_ERROR = new ErrorCode(300, "奖品失败");
    ErrorCode PRIZE_NOT_EXIST = new ErrorCode(301, "奖品不存在或已被删除");
    ErrorCode PRIZE_IN_USE = new ErrorCode(302, "该奖品已被活动使用，无法删除");
    //抽奖模块错误
    ErrorCode ACTIVITY_OR_PRIZE_IS_EMPTY = new ErrorCode(400, "抽奖活动或相关奖品不存在");
    ErrorCode ACTIVITY_COMPLETED = new ErrorCode(401, "抽奖活动已结束,无法抽奖");
    ErrorCode ACTIVITY_PRIZE_COMPLETED = new ErrorCode(402, "抽奖活动奖品已被抽取,无法抽奖");
    ErrorCode ACTIVITY_USER_PRIZE_NUM_ERROR = new ErrorCode(403, "中奖人员与奖品数量不一致");
    //图片模块错误
    ErrorCode PIC_ERROR = new ErrorCode(500, "图片上传失败");

    // ==================== 领奖履约(600 段) ====================
    ErrorCode RECEIVER_INFO_INCOMPLETE = new ErrorCode(600, "收货信息不完整,请先补全收货人、手机号和地址");
    ErrorCode WINNING_RECORD_NOT_EXIST = new ErrorCode(601, "中奖记录不存在");
    ErrorCode WINNING_RECORD_NOT_OWNED = new ErrorCode(602, "只能操作自己的中奖记录");
    ErrorCode CLAIM_DUPLICATE = new ErrorCode(603, "该奖品已申请领奖,请勿重复申请");
    ErrorCode CLAIM_STATUS_INVALID = new ErrorCode(604, "当前领奖状态不允许该操作,请刷新后重试");
    ErrorCode CLAIM_NOT_SHIPPED = new ErrorCode(605, "只有已发货的奖品才能确认收货");
    ErrorCode SHIPPING_INFO_EMPTY = new ErrorCode(606, "请填写承运公司与物流单号");
    ErrorCode CLAIM_TICKET_NOT_EXIST = new ErrorCode(607, "领奖工单不存在或已被处理");
    /** 已签收是终态:不允许再改状态或物流,保证履约记录不可篡改 */
    ErrorCode CLAIM_FROZEN = new ErrorCode(608, "该奖品已签收,不能再修改");
}
