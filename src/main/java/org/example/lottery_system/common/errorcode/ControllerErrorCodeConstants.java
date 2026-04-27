package org.example.lottery_system.common.errorcode;

public interface ControllerErrorCodeConstants {
    //人员模块错误
    ErrorCode REGISTER_ERROR=new ErrorCode(100,"注册失败");
    ErrorCode LOGIN_ERROR=new ErrorCode(101,"登录失败");
    //活动模块错误
    ErrorCode ACTIVITY_CREATE_ERROR=new ErrorCode(200,"创建活动失败");
    ErrorCode ACTIVITY_FIND_ERROR=new ErrorCode(201,"查询活动失败");
    ErrorCode ACTIVITY_FIND_DETAIL_ERROR=new ErrorCode(202,"查询活动详细失败");
    //奖品模块错误
    ErrorCode PRIZE_ERROR=new ErrorCode(300,"奖品失败");
    ErrorCode PRIZE_NOT_FOUND_ERROR=new ErrorCode(301,"奖品不存在");
    //抽奖模块错误
    ErrorCode LOTTERY_ERROR=new ErrorCode(400,"抽奖失败");
}
