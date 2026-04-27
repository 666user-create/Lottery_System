package org.example.lottery_system.service.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ActivityPrizeTiersEnum {
    FIRST_PRIZE(1, "一等奖品"),
    SECOND_PRIZE(2, "二等奖品"),
    THIRD_PRIZE(3, "三等奖品");
    private final Integer code;
    private final String message;

    public static ActivityPrizeTiersEnum forName(String name) {
        for (ActivityPrizeTiersEnum item : ActivityPrizeTiersEnum.values()) {
            if (item.name().equals(name)) {
                return item;
            }
        }
        return null;
    }
}
