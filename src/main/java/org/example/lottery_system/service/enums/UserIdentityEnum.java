package org.example.lottery_system.service.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor // 生成全参数的构造器
public enum UserIdentityEnum {
    NORMAL("普通用户"),
    ADMIN("管理员");
    private final String message;

    public static UserIdentityEnum forName(String name) {
        for (UserIdentityEnum userIdentityEnum : UserIdentityEnum.values()) {
            if (userIdentityEnum.name().equalsIgnoreCase(name)) {
                return userIdentityEnum;
            }
        }
        return null;
    }
}
