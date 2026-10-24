package org.example.lottery_system.controller;

import org.example.lottery_system.common.pojo.CommonResult;
import org.example.lottery_system.common.utils.JacksonUtil;
import org.example.lottery_system.controller.param.OperationLogQueryParam;
import org.example.lottery_system.controller.result.OperationLogListResult;
import org.example.lottery_system.service.OperationLogService;
import org.example.lottery_system.service.dto.OperationLogDTO;
import org.example.lottery_system.service.dto.PageListDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.stream.Collectors;

/*
* 操作日志查询接口
*
* 权限：/api/operation-log/** 已在 LoginInterceptor 中登记为 adminOnly，仅管理员可访问
* 说明：日志由 OperationLogInterceptor 旁路采集写入，本接口只负责查询
* */
@RestController
@RequestMapping("/api/operation-log")
public class OperationLogController {
    private static final Logger logger = LoggerFactory.getLogger(OperationLogController.class);

    @Autowired
    private OperationLogService operationLogService;

    /*
     * 分页查询操作日志
     * */
    @GetMapping("/list")
    public CommonResult<OperationLogListResult> list(@Validated OperationLogQueryParam param) {
        logger.info("查询操作日志:{}", JacksonUtil.writeValueAsString(param));
        PageListDTO<OperationLogDTO> pageListDTO = operationLogService.findList(param);
        return CommonResult.success(convertToOperationLogListResult(pageListDTO));
    }

    private OperationLogListResult convertToOperationLogListResult(
            PageListDTO<OperationLogDTO> pageListDTO) {
        OperationLogListResult result = new OperationLogListResult();
        if (pageListDTO == null) {
            result.setTotal(0);
            result.setRecords(Arrays.asList());
            return result;
        }
        result.setTotal(pageListDTO.getTotal());
        if (CollectionUtils.isEmpty(pageListDTO.getRecords())) {
            result.setRecords(Arrays.asList());
            return result;
        }
        result.setRecords(
                pageListDTO.getRecords().stream()
                        .map(operationLogDTO -> {
                            OperationLogListResult.LogInfo logInfo =
                                    new OperationLogListResult.LogInfo();
                            logInfo.setLogId(operationLogDTO.getLogId());
                            logInfo.setUserId(operationLogDTO.getUserId());
                            logInfo.setUserName(operationLogDTO.getUserName());
                            logInfo.setIdentity(operationLogDTO.getIdentity());
                            logInfo.setModule(operationLogDTO.getModule());
                            logInfo.setOperation(operationLogDTO.getOperation());
                            logInfo.setHttpMethod(operationLogDTO.getHttpMethod());
                            logInfo.setPath(operationLogDTO.getPath());
                            logInfo.setResultCode(operationLogDTO.getResultCode());
                            logInfo.setResultMsg(operationLogDTO.getResultMsg());
                            logInfo.setHttpStatus(operationLogDTO.getHttpStatus());
                            logInfo.setCostMs(operationLogDTO.getCostMs());
                            logInfo.setIp(operationLogDTO.getIp());
                            logInfo.setGmtCreate(operationLogDTO.getGmtCreate());
                            return logInfo;
                        }).collect(Collectors.toList())
        );
        return result;
    }
}
