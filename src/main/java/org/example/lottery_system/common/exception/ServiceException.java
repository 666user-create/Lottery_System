package org.example.lottery_system.common.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.example.lottery_system.common.errorcode.ErrorCode;

@Data
//防止出现异常
@EqualsAndHashCode(callSuper=true)
public class ServiceException extends RuntimeException{
    /*
     * 异常码
     * @see com.example.lottery_system.common.errorcode.ServiceErrorCodeConstants
     * */
    private Integer code;
    /*
     * 异常消息
     * */
    private String message;

    //为了序列化，需要无参构造函数
    public ServiceException(){

    }

    public ServiceException(Integer code,String message){
        this.code=code;
        this.message=message;
    }

    public ServiceException(ErrorCode errorCode){
        this.code=errorCode.getCode();
        this.message=errorCode.getMsg();
    }
}
