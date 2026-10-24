package org.example.lottery_system.service.dto;

import lombok.Data;
import org.example.lottery_system.service.enums.UserIdentityEnum;

import java.io.Serializable;
import java.util.Date;

@Data
public class UserDTO implements Serializable {
    //用户id
    private Long userId;
    //用户名
    private String userName;
    //邮箱
    private String email;
    //手机号
    private String phoneNumber;
    //角色
    private UserIdentityEnum identity;
    //账号状态：ACTIVE 正常 / DISABLED 已停用
    private String status;

    // ===== 以下字段仅供「用户详情」使用，列表接口不填（保持列表响应轻量）=====
    //收货人姓名 / 收货人手机号(已解密) / 收货地址：领奖发货用
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    //注册时间 / 最后修改时间
    private Date gmtCreate;
    private Date gmtModified;
}
