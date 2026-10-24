package org.example.lottery_system.controller.param;

import lombok.Data;

/**
 * 收货信息参数(个人中心维护默认收货地址 / 领奖时提交)
 */
@Data
public class ReceiverInfoParam {
    /** 收货人姓名 */
    private String receiverName;
    /** 收货人手机号 */
    private String receiverPhone;
    /** 收货地址 */
    private String receiverAddress;
}
