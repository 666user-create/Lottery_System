package org.example.lottery_system;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.crypto.symmetric.AES;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class EncryptTest {
    //测试加密
    @Test
    void testEncrypt1(){
        //密码
        // hash sha256加密
        String s = DigestUtil.sha256Hex("123456");
        System.out.println(s);
        //8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92

    }
    @Test
    void testEncrypt2(){
        //手机号
        //对称加密
        //16位密钥
        byte[] key=new byte[]{1,2,3,4,5,6,7,8,9,0,1,2,3,4,5,6};
        //加密
        AES aes = SecureUtil.aes(key);
        String s = aes.encryptHex("123456");
        System.out.println(s);
        //ae175d2e09f89bf152ce4bc9f461396c
        //解密
        s=aes.decryptStr(s);
        System.out.println(s);
        //123456
    }
}
