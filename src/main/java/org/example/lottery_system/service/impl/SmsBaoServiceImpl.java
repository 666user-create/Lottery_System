package org.example.lottery_system.service.impl;

import org.example.lottery_system.common.config.sms.SmsBaoConfig;
import org.example.lottery_system.service.dto.WinnerRecordDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;

@Service
public class SmsBaoServiceImpl {

    private static final Logger logger = LoggerFactory.getLogger(SmsBaoServiceImpl.class);

    @Autowired
    private SmsBaoConfig smsBaoConfig;

    @Autowired
    private RestTemplate restTemplate;

    /**
     * 发送中奖通知短信
     * @param winner 中奖者信息
     */
    public void sendWinningNotification(WinnerRecordDTO winner) {
        String phone = getPhoneValue(winner);
        if ("未留存".equals(phone)) {
            logger.warn("用户 {} 手机号为空，跳过短信发送", winner.getWinnerName());
            return;
        }

        // 1. 构造短信内容
        // 模板：【抽奖系统】Hi，${name}。恭喜你在${activityName}活动中获得${prizeTiers}，奖品为${prizeName}。获奖时间为${winningTime}，请尽快领取您的奖励！
        String winningTimeStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String fullContent = String.format("%sHi，%s。恭喜你在%s活动中获得%s，奖品为%s。获奖时间为%s，请尽快领取您的奖励！",
                smsBaoConfig.getSignature(),
                winner.getWinnerName(),
                winner.getActivityName(),
                formatPrizeTier(winner.getPrizeTier()),
                winner.getPrizeName(),
                winningTimeStr
        );

        // 2. 构建请求 URI
        URI uri = UriComponentsBuilder.fromHttpUrl(smsBaoConfig.getApiUrl())
                .queryParam("u", smsBaoConfig.getUsername())
                .queryParam("p", smsBaoConfig.getApiKey())
                .queryParam("m", phone)
                .queryParam("c", fullContent)
                .build()
                .encode()
                .toUri();

        // 3. 执行异步请求
        try {
            String result = restTemplate.getForObject(uri, String.class);
            handleResult(result, phone);
        } catch (Exception e) {
            logger.error("短信宝服务通讯异常，发送至 {} 失败", phone, e);
        }
    }

    private void handleResult(String code, String phone) {
        if ("0".equals(code)) {
            logger.info("短信发送成功，目标手机号：{}", phone);
        } else {
            String errorMsg = switch (code) {
                case "30" -> "密码错误";
                case "40" -> "账号不存在";
                case "41" -> "余额不足";
                case "43" -> "IP地址限制";
                case "50" -> "内容含有敏感词";
                case "51" -> "手机号码不正确";
                default -> "未知错误 (" + code + ")";
            };
            logger.error("短信发送失败，手机号：{}，原因：{}", phone, errorMsg);
        }
    }

    private String getPhoneValue(WinnerRecordDTO winner) {
        if (winner.getWinnerPhoneNumber() != null && winner.getWinnerPhoneNumber().containsKey("value")) {
            return winner.getWinnerPhoneNumber().get("value");
        }
        return "未留存";
    }

    private String formatPrizeTier(String tier) {
        if ("FIRST_PRIZE".equals(tier)) return "一等奖";
        if ("SECOND_PRIZE".equals(tier)) return "二等奖";
        if ("THIRD_PRIZE".equals(tier)) return "三等奖";
        return "特别奖";
    }
}
