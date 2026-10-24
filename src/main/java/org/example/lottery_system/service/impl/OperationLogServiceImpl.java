package org.example.lottery_system.service.impl;

import org.example.lottery_system.controller.param.OperationLogQueryParam;
import org.example.lottery_system.dao.dataobject.OperationLogDO;
import org.example.lottery_system.dao.mapper.OperationLogMapper;
import org.example.lottery_system.service.OperationLogService;
import org.example.lottery_system.service.dto.OperationLogDTO;
import org.example.lottery_system.service.dto.PageListDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class OperationLogServiceImpl implements OperationLogService {
    private static final Logger logger = LoggerFactory.getLogger(OperationLogServiceImpl.class);

    @Autowired
    private OperationLogMapper operationLogMapper;

    @Override
    public void save(OperationLogDO operationLogDO) {
        if (operationLogDO == null) {
            return;
        }
        try {
            operationLogMapper.insert(operationLogDO);
        } catch (Exception e) {
            //运行在异步线程上，异常无人接管，这里必须自行吞掉，绝不能让日志影响业务
            logger.warn("操作日志落库失败，已忽略: {} {}", operationLogDO.getHttpMethod(),
                    operationLogDO.getPath(), e);
        }
    }

    @Override
    public PageListDTO<OperationLogDTO> findList(OperationLogQueryParam param) {
        int total = operationLogMapper.count(param);

        List<OperationLogDTO> operationLogDTOList = new ArrayList<>();
        List<OperationLogDO> operationLogDOList =
                operationLogMapper.selectList(param, param.offset(), param.getPageSize());
        for (OperationLogDO operationLogDO : operationLogDOList) {
            operationLogDTOList.add(convertToOperationLogDTO(operationLogDO));
        }
        return new PageListDTO<>(total, operationLogDTOList);
    }

    private OperationLogDTO convertToOperationLogDTO(OperationLogDO operationLogDO) {
        OperationLogDTO operationLogDTO = new OperationLogDTO();
        operationLogDTO.setLogId(operationLogDO.getId());
        operationLogDTO.setUserId(operationLogDO.getUserId());
        operationLogDTO.setUserName(operationLogDO.getUserName());
        operationLogDTO.setIdentity(operationLogDO.getIdentity());
        operationLogDTO.setModule(operationLogDO.getModule());
        operationLogDTO.setOperation(operationLogDO.getOperation());
        operationLogDTO.setHttpMethod(operationLogDO.getHttpMethod());
        operationLogDTO.setPath(operationLogDO.getPath());
        operationLogDTO.setResultCode(operationLogDO.getResultCode());
        operationLogDTO.setResultMsg(operationLogDO.getResultMsg());
        operationLogDTO.setHttpStatus(operationLogDO.getHttpStatus());
        operationLogDTO.setCostMs(operationLogDO.getCostMs());
        operationLogDTO.setIp(operationLogDO.getIp());
        operationLogDTO.setGmtCreate(operationLogDO.getGmtCreate());
        return operationLogDTO;
    }
}
