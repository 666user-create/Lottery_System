package org.example.lottery_system.controller.result;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/*
* 操作日志分页查询返回结果
* */
@Data
public class OperationLogListResult implements Serializable {
    //总量
    private Integer total;
    //当前页列表
    private List<LogInfo> records;

    @Data
    public static class LogInfo implements Serializable {
        //日志id
        private Long logId;
        //操作人id
        private Long userId;
        //操作人姓名
        private String userName;
        //身份
        private String identity;
        //业务模块
        private String module;
        //操作描述
        private String operation;
        //请求方法
        private String httpMethod;
        //请求路径
        private String path;
        //业务返回码
        private Integer resultCode;
        //结果描述/失败原因
        private String resultMsg;
        //HTTP 状态码
        private Integer httpStatus;
        //处理耗时（毫秒）
        private Long costMs;
        //来源IP
        private String ip;
        //记录时间
        private Date gmtCreate;
    }
}
