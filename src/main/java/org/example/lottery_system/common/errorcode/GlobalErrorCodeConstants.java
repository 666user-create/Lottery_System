package org.example.lottery_system.common.errorcode;

public interface GlobalErrorCodeConstants {
    ErrorCode SUCCESS=new ErrorCode(200,"成功");
    ErrorCode BAD_REQUEST=new ErrorCode(400,"请求参数有误");
    ErrorCode UNAUTHORIZED=new ErrorCode(401,"未登录或Token已失效");
    ErrorCode FORBIDDEN=new ErrorCode(403,"需要管理员权限");
    ErrorCode INTERNAL_SERVER_ERROR=new ErrorCode(500,"系统异常");
    ErrorCode UNKNOWN=new ErrorCode(999,"未知错误");
}
