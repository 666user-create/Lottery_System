package org.example.lottery_system.controller.param;

import lombok.Data;
import lombok.EqualsAndHashCode;

/*
* 操作日志查询参数
* 继承 PageParam 复用 currentPage/pageSize 与边界校验
* */
@Data
@EqualsAndHashCode(callSuper = true)
public class OperationLogQueryParam extends PageParam {
    //业务模块，空表示不过滤
    private String module;
    //身份，空表示不过滤
    private String identity;
    //起始日期，格式 yyyy-MM-dd
    private String startTime;
    //结束日期，格式 yyyy-MM-dd
    private String endTime;
}
