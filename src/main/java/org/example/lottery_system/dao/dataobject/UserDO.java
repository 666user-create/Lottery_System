package org.example.lottery_system.dao.dataobject;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class UserDO extends BaseDO {
    //用户名
    private String userName;
    //邮箱
    private String email;
    //手机号
    private Encrypt phoneNumber;
    //密码
    private String password;
    //角色
    private String identity;
}
