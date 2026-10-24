package org.example.lottery_system.dao.dataobject;

import lombok.Data;
import lombok.EqualsAndHashCode;

/*
* 操作日志
*
* 设计说明：操作人姓名不落库，列表查询时连表 user 取，
* 这样写日志的路径上不需要任何额外查询。
* */
@Data
@EqualsAndHashCode(callSuper = true)
public class OperationLogDO extends BaseDO {
    //操作人id，未登录为空
    private Long userId;
    //身份：ADMIN/NORMAL/ANONYMOUS
    private String identity;
    //业务模块
    private String module;
    //操作描述
    private String operation;
    //请求方法
    private String httpMethod;
    //请求路径
    private String path;
    //业务返回码，200 表示成功
    private Integer resultCode;
    //结果描述/失败原因
    private String resultMsg;
    //HTTP 状态码
    private Integer httpStatus;
    //处理耗时（毫秒）
    private Long costMs;
    //来源IP
    private String ip;
    //操作人姓名（连表查询字段，不入库）
    private String userName;
}
