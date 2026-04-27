package org.example.lottery_system.service.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ActivityUserStatusEnum {
    RUNNING(1, "初始化"),
    COMPLETED(2, "已领取");
    private final Integer code;
    private final String message;

    public static ActivityUserStatusEnum forName(String name) {
        for (ActivityUserStatusEnum item : ActivityUserStatusEnum.values()) {
            if (item.name().equals(name)) {
                return item;
            }
        }
        return null;
    }
}
