package org.example.lottery_system.common.pojo;

import lombok.Data;
import org.example.lottery_system.common.errorcode.ErrorCode;
import org.example.lottery_system.common.errorcode.GlobalErrorCodeConstants;
import org.springframework.util.Assert;

import java.io.Serializable;

@Data
public class CommonResult<T> implements Serializable {
    /*
    * 返回错误码
    * */
    private Integer code;
    /*
    * 返回数据
    * */
    private T data;
    /*
    * 返回消息
    * */
    private String message;
    //成功返回
    public static <T> CommonResult<T> success(T data){
        CommonResult<T> result=new CommonResult<T>();
        result.code= GlobalErrorCodeConstants.SUCCESS.getCode();
        result.data=data;
        result.message=GlobalErrorCodeConstants.SUCCESS.getMsg();

        return result;
    }
    //失败返回
    public static <T> CommonResult<T> error(Integer code,String message){
        Assert.isTrue(!GlobalErrorCodeConstants.SUCCESS.getCode().equals(code),
                "code正常");
        CommonResult<T> result=new CommonResult<T>();
        result.code=code;
        result.message=message;
        return result;
    }
    //失败返回
    public static <T> CommonResult<T> error(ErrorCode errorCode){
        return error(errorCode.getCode(),errorCode.getMsg());
    }
}
