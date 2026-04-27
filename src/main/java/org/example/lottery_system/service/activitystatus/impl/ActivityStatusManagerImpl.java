package org.example.lottery_system.service.activitystatus.impl;

import org.example.lottery_system.common.errorcode.ServiceErrorCodeConstants;
import org.example.lottery_system.common.exception.ServiceException;
import org.example.lottery_system.service.ActivityService;
import org.example.lottery_system.service.activitystatus.ActivityStatusManager;
import org.example.lottery_system.service.activitystatus.operator.AbstractActivityOperator;
import org.example.lottery_system.service.dto.ConvertActivityStatusDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Component
@Transactional(rollbackFor = Exception.class)
public class ActivityStatusManagerImpl implements ActivityStatusManager {
    @Autowired
    private ActivityService activityService;
    private static final Logger logger = LoggerFactory.getLogger(ActivityStatusManagerImpl.class);
    @Autowired
    private final Map<String, AbstractActivityOperator> operatorMap=new HashMap<>();
    @Override
    public void handlerEvent(ConvertActivityStatusDTO convertActivityStatusDTO) {
        //map<String,AbstractActivityOperator>
        if (CollectionUtils.isEmpty(operatorMap.values())){
            logger.warn("operatorMap为空");
            return;
        }
        Map<String,AbstractActivityOperator> currMap=new HashMap<>(operatorMap);
        Boolean isUpdated=false;
        //先处理:人员,奖品
        isUpdated=processConvertStatus(convertActivityStatusDTO,currMap,1);
        //再处理:活动状态
        isUpdated=processConvertStatus(convertActivityStatusDTO,currMap,2)||isUpdated;
        //最后处理:缓存
        if (isUpdated){
            activityService.cacheActivity(convertActivityStatusDTO.getActivityId());
        }
    }

    @Override
    public void rollbackHandleEvent(ConvertActivityStatusDTO convertActivityStatusDTO) {
        //operatorMap里的全回滚
        for(AbstractActivityOperator abstractActivityOperator:operatorMap.values()){
            abstractActivityOperator.convert(convertActivityStatusDTO);
        }
        //最后处理:缓存
        activityService.cacheActivity(convertActivityStatusDTO.getActivityId());
    }

    private Boolean processConvertStatus(ConvertActivityStatusDTO convertActivityStatusDTO,
                                         Map<String, AbstractActivityOperator> currMap,
                                         int sequence) {
        Boolean hasChanged = false;
        //遍历currMap
        Iterator<Map.Entry<String, AbstractActivityOperator>> iterator = currMap.entrySet().iterator();
        while (iterator.hasNext()){
            AbstractActivityOperator operator=iterator.next().getValue();
            //Operator是否要转换
            if (operator.sequence()!=sequence || !operator.needConvert(convertActivityStatusDTO)){
                continue;
            }
            //如果要转换,则更新currMap
            if (!operator.convert(convertActivityStatusDTO)){
                logger.error("转换失败,operator:{}",operator.getClass().getName());
                throw new ServiceException(ServiceErrorCodeConstants.ACTIVITY_STATUS_CONVERT_ERROR);
            }
            //currMap删除该Operator
            iterator.remove();
            hasChanged=true;
        }
        //返回
        return hasChanged;
    }
}
