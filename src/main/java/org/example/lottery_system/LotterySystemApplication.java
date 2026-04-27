package org.example.lottery_system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class LotterySystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(LotterySystemApplication.class, args);
    }

}
