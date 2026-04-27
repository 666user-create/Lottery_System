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
    //活动模块错误
    ErrorCode CREATE_ACTIVITY_EMPTY = new ErrorCode(200, "创建活动参数为空");
    ErrorCode ACTIVITY_USER_ERROR = new ErrorCode(201, "活动关联人员异常");
    ErrorCode ACTIVITY_PRIZE_ERROR = new ErrorCode(202, "活动关联奖品异常");
    ErrorCode ACTIVITY_USER_PRIZE_ERROR = new ErrorCode(203, "人员奖品数量不合适");
    ErrorCode ACTIVITY_PRIZE_TIER_ERROR = new ErrorCode(204, "活动奖品等级异常");
    ErrorCode ACTIVITY_STATUS_CONVERT_ERROR = new ErrorCode(205, "活动相关状态转换异常");
    ErrorCode CACHE_ACTIVITY_NOT_EXIST = new ErrorCode(206, "缓存活动id不存在");
    //奖品模块错误
    ErrorCode PRIZE_ERROR = new ErrorCode(300, "奖品失败");
    //抽奖模块错误
    ErrorCode ACTIVITY_OR_PRIZE_IS_EMPTY = new ErrorCode(400, "抽奖活动或相关奖品不存在");
    ErrorCode ACTIVITY_COMPLETED = new ErrorCode(401, "抽奖活动已结束,无法抽奖");
    ErrorCode ACTIVITY_PRIZE_COMPLETED = new ErrorCode(402, "抽奖活动奖品已被抽取,无法抽奖");
    ErrorCode ACTIVITY_USER_PRIZE_NUM_ERROR = new ErrorCode(403, "中奖人员与奖品数量不一致");
    //图片模块错误
    ErrorCode PIC_ERROR = new ErrorCode(500, "图片上传失败");
}
