package org.example.lottery_system.controller.result;

import lombok.Data;

/**
 * 收货信息返回结果(个人中心展示 / 领奖前校验是否填全)
 */
@Data
public class ReceiverInfoResult {
    /** 收货人姓名 */
    private String receiverName;
    /** 收货人手机号 */
    private String receiverPhone;
    /** 收货地址 */
    private String receiverAddress;
    /** 三项是否都已填写(前端据此提示"请先补全收货地址") */
    private Boolean complete;
}
