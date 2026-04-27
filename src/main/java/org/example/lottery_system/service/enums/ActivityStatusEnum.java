package org.example.lottery_system.service.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ActivityStatusEnum {
    RUNNING(1, "活动进行中"),
    COMPLETED(2, "活动已完成");
    private final Integer code;
    private final String message;

    public static ActivityStatusEnum forName(String name) {
        for (ActivityStatusEnum item : ActivityStatusEnum.values()) {
            if (item.name().equals(name)) {
                return item;
            }
        }
        return null;
    }
}
