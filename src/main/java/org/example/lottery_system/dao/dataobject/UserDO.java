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
    //账号状态：ACTIVE 正常 / DISABLED 已停用（软停用，不删行）
    private String status;
    //收货人姓名(领奖履约用)
    private String receiverName;
    //收货人手机号(与 phoneNumber 一致,加密存储)
    private Encrypt receiverPhone;
    //收货地址
    private String receiverAddress;
}
