package org.example.lottery_system.controller.param;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserLoginParam implements Serializable {
    //强制某身份登录
    private String mandatoryIdentity;

}
