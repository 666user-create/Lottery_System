package org.example.lottery_system.dao.dataobject;

import lombok.Data;

@Data
public class Encrypt {
    private String value;

    public Encrypt() {}
    public Encrypt(String value) {
        this.value = value;
    }
}
