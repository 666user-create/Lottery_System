package org.example.lottery_system.service.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户账号状态。
 *
 * 背景：user 表原先没有状态列，管理员无法停用账号；只能硬删，
 * 而 user.id 被 activity_user / winning_record / operation_log / human_handover 引用
 * 且全库无外键，硬删必然留下孤儿数据、弄断审计链路。
 * 故改为软停用：登录与请求校验处拦截，历史记录全部保留。
 */
@AllArgsConstructor
@Getter
public enum UserStatusEnum {
    ACTIVE(1, "正常"),
    DISABLED(2, "已停用");

    private final Integer code;
    private final String message;

    public static UserStatusEnum forName(String name) {
        for (UserStatusEnum item : UserStatusEnum.values()) {
            if (item.name().equals(name)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 兼容历史数据：status 为空（如手工插入的行）一律视为正常，
     * 避免因为新增列把老账号全判成停用。
     */
    public static boolean isActive(String name) {
        return !DISABLED.name().equals(name);
    }
}
