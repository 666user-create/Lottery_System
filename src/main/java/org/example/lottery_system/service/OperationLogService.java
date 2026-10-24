package org.example.lottery_system.service;

import org.example.lottery_system.controller.param.OperationLogQueryParam;
import org.example.lottery_system.dao.dataobject.OperationLogDO;
import org.example.lottery_system.service.dto.OperationLogDTO;
import org.example.lottery_system.service.dto.PageListDTO;

public interface OperationLogService {
    /*
     * 保存一条操作日志（由拦截器异步调用）
     * 实现内部必须自行吞掉所有异常：记日志失败不能影响业务
     * */
    void save(OperationLogDO operationLogDO);

    /*
     * 分页查询操作日志
     * */
    PageListDTO<OperationLogDTO> findList(OperationLogQueryParam param);
}
