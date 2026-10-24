package org.example.lottery_system.service.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/*
* 操作日志展示对象
* */
@Data
public class OperationLogDTO implements Serializable {
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
