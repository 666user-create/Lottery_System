package org.example.lottery_system.service.activitystatus.operator;

import org.example.lottery_system.service.dto.ConvertActivityStatusDTO;

public abstract class AbstractActivityOperator {
    //控制操作顺序
    public abstract Integer sequence();

    //是否要转换
    public abstract Boolean needConvert(ConvertActivityStatusDTO convertActivityStatusDTO);
    //转换操作
    public abstract Boolean convert(ConvertActivityStatusDTO convertActivityStatusDTO);
}
