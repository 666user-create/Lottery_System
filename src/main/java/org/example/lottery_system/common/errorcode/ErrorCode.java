package org.example.lottery_system.common.errorcode;

import lombok.Data;

@Data
public class ErrorCode {
    /**
     * 错误码
     */
    private final Integer code;
    /**
     * 错误描述
     */
    private final String msg;

    public ErrorCode(Integer code, String msg) {
        this.code=code;
        this.msg=msg;
    }
}
