package org.example.lottery_system.service.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ActivityPrizeStatusEnum {
    INIT(1, "初始化"),
    COMPLETED(2, "已领取");
    private final Integer code;
    private final String message;

    public static ActivityPrizeStatusEnum forName(String name) {
        for (ActivityPrizeStatusEnum item : ActivityPrizeStatusEnum.values()) {
            if (item.name().equals(name)) {
                return item;
            }
        }
        return null;
    }
}
