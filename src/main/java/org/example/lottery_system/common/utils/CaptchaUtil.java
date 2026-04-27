package org.example.lottery_system.common.utils;

import cn.hutool.captcha.LineCaptcha;
import cn.hutool.captcha.generator.RandomGenerator;

public class CaptchaUtil {
    /**
     * 生成数字验证码
     *
     * @return
     */
    public static String generateCaptchaCode(int length) {
        RandomGenerator randomGenerator = new RandomGenerator("0123456789", length);
        LineCaptcha lineCaptcha =
                cn.hutool.captcha.CaptchaUtil.createLineCaptcha(200, 100);
        lineCaptcha.setGenerator(randomGenerator);
        // 重新生成code
        lineCaptcha.createCode();
        return lineCaptcha.getCode();
    }
}
