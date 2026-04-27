package org.example.lottery_system;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.lottery_system.common.errorcode.ServiceErrorCodeConstants;
import org.example.lottery_system.common.pojo.CommonResult;
import org.example.lottery_system.common.utils.JacksonUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@SpringBootTest
public class JacksonTest {
    @Test
    void test() {
        ObjectMapper mapper = new ObjectMapper();
        CommonResult<String> result = CommonResult.error(999, "未知错误");
        String str;
        //序列化
        try {
            str = mapper.writeValueAsString(result);
            System.out.println(str);
        } catch (Exception e) {
            throw new RuntimeException();
        }
        //反序列化
        try {
            CommonResult<String> commonResult = mapper.readValue(str,
                    CommonResult.class);
            System.out.println(commonResult);
        } catch (Exception e) {
            throw new RuntimeException();
        }
        //List序列化
        List<CommonResult<String>> commonResults = Arrays.asList(
                CommonResult.success("success1"),
                CommonResult.success("success2")
        );
        try {
            str = mapper.writeValueAsString(commonResults);
            System.out.println(str);
        } catch (JsonProcessingException e) {
            throw new RuntimeException();
        }
        //List反序列化
        JavaType javaType=mapper.getTypeFactory()
                .constructParametricType(List.class,CommonResult.class);
        try{
            commonResults=mapper.readValue(str,javaType);
            System.out.println(commonResults);
        } catch (JsonMappingException e) {
            throw new RuntimeException(e);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void JacksonUtilTest(){
        CommonResult<String> result = CommonResult.error(999, "未知错误");
        String str;
        //序列化
        str= JacksonUtil.writeValueAsString(result);
        System.out.println(str);
        //反序列化
        result=JacksonUtil.readValue(str,CommonResult.class);
        System.out.println(result);
        //List序列化
        List<CommonResult<String>> commonResults = Arrays.asList(
                CommonResult.success("success1"),
                CommonResult.success("success2")
        );
        str=JacksonUtil.writeValueAsString(commonResults);
        System.out.println(str);
        //List反序列化
        commonResults=JacksonUtil.readListValue(str, CommonResult.class);
        for (CommonResult<String> s:commonResults){
            System.out.println(s);
        }
    }
}
