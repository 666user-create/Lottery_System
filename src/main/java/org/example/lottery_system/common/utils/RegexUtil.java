package org.example.lottery_system.common.utils;

import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

public class RegexUtil {
    /**
     * 邮箱：xxx@xx.xxx(形如：abc@qq.com)
     *
     * @param content
     * @return
     */
    public static boolean checkMail(String content) {
        if (!StringUtils.hasText(content)) {
            return false;
        }
        /**
         * 修复 BUG-007：原正则仅允许小写 [a-z0-9]，导致含大写字母的合法邮箱
         * （如 User@Example.COM）被拒绝注册。域名大小写不敏感、大写字母属合法字符，
         * 故 local-part 与域名部分均放开为大小写不限。
         */
        String regex = "^[a-zA-Z0-9]+([._\\-]*[a-zA-Z0-9])*@([a-zA-Z0-9]+[-a-zA-Z0-9]*[a-zA-Z0-9]+.){1,63}[a-zA-Z0-9]+$";
        return Pattern.matches(regex, content);
    }

    /**
     * 手机号码以1开头的11位数字
     *
     * @param content
     * @return
     */
    public static boolean checkMobile(String content) {
        if (!StringUtils.hasText(content)) {
            return false;
        }
        /**
         * ^ 表示匹配字符串的开始。
         * 1 表示手机号码以数字1开头。
         * [3|4|5|6|7|8|9] 表示接下来的数字是3到9之间的任意一个数字。这是中国大陆手机号
         码的第二位数字，通常用来区分不同的运营商。
         * [0-9]{9} 表示后面跟着9个0到9之间的任意数字，这代表手机号码的剩余部分。
         * $ 表示匹配字符串的结束。
         */
        String regex = "^1[3|4|5|6|7|8|9][0-9]{9}$";
        return Pattern.matches(regex, content);
    }

    /**
     * 密码强度正则，6到12位
     *
     * @param content
     * @return
     */
    public static boolean checkPassword(String content) {
        if (!StringUtils.hasText(content)) {
            return false;
        }
        /**
         * ^ 表示匹配字符串的开始。
         * [0-9A-Za-z] 表示匹配的字符可以是：
         * 0-9：任意一个数字（0到9）。
         * A-Z：任意一个大写字母（从A到Z）。
         * a-z：任意一个小写字母（从a到z）。
         * {6,12} 表示前面的字符集合（数字、大写字母和小写字母）可以重复出现6到12次。
         * $ 表示匹配字符串的结束。
         */
        String regex = "^[0-9A-Za-z]{6,12}$";
        return Pattern.matches(regex, content);
    }
}