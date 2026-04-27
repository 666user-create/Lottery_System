package org.example.lottery_system.common.utils;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeRequest;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeResponse;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import com.google.gson.Gson;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SMSUtil {
    // 1. 将变量定义为 static
    private static String accessKeyId;
    private static String accessKeySecret;

    // 2. 通过非静态的 Setter 方法注入值
    @Value("${sms.access-key-id}")
    public void setAccessKeyId(String accessKeyId) {
        SMSUtil.accessKeyId = accessKeyId;
    }

    @Value("${sms.access-key-secret}")
    public void setAccessKeySecret(String accessKeySecret) {
        SMSUtil.accessKeySecret = accessKeySecret;
    }

    private static final String SIGN_NAME = "速通互联验证码";
    private static final String TEMPLATE_CODE = "100001";

    /**
     * 初始化客户端 (现在可以读取到静态变量了)
     */
    private static Client createClient() throws Exception {
        // 增加一个简单的校验，防止配置没加载就调用
        if (accessKeyId == null || accessKeySecret == null) {
            throw new RuntimeException("阿里云配置未加载，请检查 application.yml");
        }

        Config config = new Config()
                .setAccessKeyId(accessKeyId)
                .setAccessKeySecret(accessKeySecret);
        config.endpoint = "dypnsapi.aliyuncs.com";
        return new Client(config);
    }

    /**
     * 发送验证码静态方法
     * @param phoneNumber 目标手机号（从前端获取后传入）
     * @param code 动态生成的验证码内容
     * @return 阿里云返回的原始响应对象（包含Code, Message等）
     */
    public static SendSmsVerifyCodeResponse send(String phoneNumber, String code) throws Exception {
        // 1. 创建客户端
        Client client = createClient();

        // 2. 构造请求参数
        SendSmsVerifyCodeRequest request = new SendSmsVerifyCodeRequest()
                .setSignName(SIGN_NAME)
                .setTemplateCode(TEMPLATE_CODE)
                .setPhoneNumber(phoneNumber) // <--- 动态手机号
                // 注意：JSON 内部的变量名 "code" 必须与你在阿里云后台模板中定义的变量名一致
                .setTemplateParam("{\"code\":\"" + code + "\",\"min\":\"5\"}");

        // 3. 设置运行时参数
        RuntimeOptions runtime = new RuntimeOptions();

        // 4. 执行发送并返回结果
        return client.sendSmsVerifyCodeWithOptions(request, runtime);
    }
}