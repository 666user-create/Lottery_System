package org.example.lottery_system.common.errorcode;

public interface GlobalErrorCodeConstants {
    ErrorCode SUCCESS=new ErrorCode(200,"成功");
    ErrorCode BAD_REQUEST=new ErrorCode(400,"请求参数有误");
    ErrorCode UNAUTHORIZED=new ErrorCode(401,"未登录或Token已失效");
    ErrorCode FORBIDDEN=new ErrorCode(403,"需要管理员权限");
    ErrorCode NOT_FOUND=new ErrorCode(404,"资源不存在");
    ErrorCode METHOD_NOT_ALLOWED=new ErrorCode(405,"请求方法不支持");
    ErrorCode UNSUPPORTED_MEDIA_TYPE=new ErrorCode(415,"不支持的请求类型");
    ErrorCode INTERNAL_SERVER_ERROR=new ErrorCode(500,"系统异常");
    ErrorCode UNKNOWN=new ErrorCode(999,"未知错误");
}
