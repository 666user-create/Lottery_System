package org.example.lottery_system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
// 开启定时任务:对话记录定期清理(ChatMemoryCleanupService)
@EnableScheduling
@SpringBootApplication
public class LotterySystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(LotterySystemApplication.class, args);
    }

}
