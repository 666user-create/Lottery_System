package org.example.lottery_system.common.config.sms;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "smsbao")
public class SmsBaoConfig {
    private String username;
    private String apiKey;    // 配置文件中的 api-key
    private String signature;
    private String apiUrl;
}
