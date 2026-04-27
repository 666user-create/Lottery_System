package org.example.lottery_system.controller.result;

import lombok.Data;

import java.io.Serializable;

@Data
public class BaseUserInfoResult implements Serializable {
    //用户id
    private Long userId;
    //用户名
    private String userName;
    //身份
    private String identity;
}
