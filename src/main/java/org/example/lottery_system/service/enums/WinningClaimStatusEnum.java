package org.example.lottery_system.service.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 中奖领奖状态(履约进度)。
 *
 * 设计要点(见 docs/design/中奖履约-领奖发货设计.md):
 *   状态按【中奖者个体】维护,存在 winning_record 表(一人一行),
 *   而不是放在 activity_prize(活动-奖项粒度,一个奖项可能有多名中奖者,粒度不匹配)。
 *
 * 流转规则:
 *   NOT_CLAIMED --用户申请领奖--> CLAIMED --管理员登记发货--> SHIPPED --用户确认收货--> COMPLETED
 *   工单取消(CLOSED)时:CLAIMED 回退为 NOT_CLAIMED,允许重新申请
 *
 * 谁来推进:
 *   客户端(用户):申请领奖(NOT_CLAIMED→CLAIMED)、确认收货(SHIPPED→COMPLETED)
 *   管理端(管理员):登记发货(CLAIMED→SHIPPED)、取消工单(CLAIMED→NOT_CLAIMED)
 */
@AllArgsConstructor
@Getter
public enum WinningClaimStatusEnum {

    NOT_CLAIMED("未领取"),
    CLAIMED("已申请待发货"),
    SHIPPED("已发货"),
    COMPLETED("已签收");

    private final String message;

    public static WinningClaimStatusEnum forName(String name) {
        for (WinningClaimStatusEnum item : WinningClaimStatusEnum.values()) {
            if (item.name().equals(name)) {
                return item;
            }
        }
        return null;
    }

    /** 兼容空值:老数据或字段缺失时视为「未领取」 */
    public static String normalize(String name) {
        return forName(name) == null ? NOT_CLAIMED.name() : name;
    }

    /** 展示文案(前端也可直接用,避免前后端各写一份) */
    public static String messageOf(String name) {
        WinningClaimStatusEnum item = forName(name);
        return item == null ? NOT_CLAIMED.getMessage() : item.getMessage();
    }
}
