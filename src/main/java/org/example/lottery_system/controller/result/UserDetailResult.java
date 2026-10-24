package org.example.lottery_system.controller.result;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户详情返回结果（管理端「查看详情」专用）。
 *
 * 除密码外的全部用户信息：基本资料 + 收货信息 + 时间戳。
 * 密码属于凭证，任何情况下都不返回给前端（列表接口 BaseUserInfoResult 同理）。
 */
@Data
public class UserDetailResult implements Serializable {
    /** 用户ID */
    private Long userId;
    /** 用户名 */
    private String userName;
    /** 邮箱 */
    private String email;
    /** 手机号（已解密的明文） */
    private String phoneNumber;
    /** 身份：ADMIN / NORMAL */
    private String identity;
    /** 身份中文名：管理员 / 普通用户 */
    private String identityText;
    /** 账号状态：ACTIVE / DISABLED */
    private String status;
    /** 状态中文名：正常 / 已停用 */
    private String statusText;
    /** 收货人姓名 */
    private String receiverName;
    /** 收货人手机号（已解密的明文） */
    private String receiverPhone;
    /** 收货地址 */
    private String receiverAddress;
    /** 注册时间 */
    private Date gmtCreate;
    /** 最后修改时间 */
    private Date gmtModified;
}
