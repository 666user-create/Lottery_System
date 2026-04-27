package org.example.lottery_system.service.activitystatus.operator;

import cn.hutool.core.collection.CollectionUtil;
import org.example.lottery_system.dao.dataobject.ActivityUserDO;
import org.example.lottery_system.dao.mapper.ActivityUserMapper;
import org.example.lottery_system.service.dto.ConvertActivityStatusDTO;
import org.example.lottery_system.service.enums.ActivityStatusEnum;
import org.example.lottery_system.service.enums.ActivityUserStatusEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.List;

@Component
public class UserOperator extends AbstractActivityOperator {
    @Autowired
    private ActivityUserMapper activityUserMapper;
    @Override
    public Integer sequence() {
        return 1;
    }

    @Override
    public Boolean needConvert(ConvertActivityStatusDTO convertActivityStatusDTO) {
        Long activityId = convertActivityStatusDTO.getActivityId();
        List<Long> userIds = convertActivityStatusDTO.getUserIds();
        ActivityUserStatusEnum targetUserStatus = convertActivityStatusDTO.getTargetUserStatus();
        if (activityId==null|| CollectionUtil.isEmpty(userIds)|| targetUserStatus==null){
            return false;
        }
        List<ActivityUserDO> activityUserDOList = activityUserMapper.batchSelectByActivityIdAndUserIds(activityId, userIds);
        if (CollectionUtils.isEmpty(activityUserDOList)){
            return false;
        }
        for (ActivityUserDO acDO : activityUserDOList) {
            if (acDO.getStatus().equalsIgnoreCase(targetUserStatus.name())){
                return false;
            }
        }
        return true;
    }

    @Override
    public Boolean convert(ConvertActivityStatusDTO convertActivityStatusDTO) {
        Long activityId = convertActivityStatusDTO.getActivityId();
        List<Long> userIds = convertActivityStatusDTO.getUserIds();
        ActivityUserStatusEnum targetUserStatus = convertActivityStatusDTO.getTargetUserStatus();

        try{
            activityUserMapper.batchUpdateStatus(activityId, userIds, targetUserStatus.name());
            return true;
        }catch (Exception e){
            return false;
        }
    }
}
