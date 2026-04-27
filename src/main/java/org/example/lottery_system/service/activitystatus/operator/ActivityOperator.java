package org.example.lottery_system.service.activitystatus.operator;

import org.example.lottery_system.dao.dataobject.ActivityDO;
import org.example.lottery_system.dao.mapper.ActivityMapper;
import org.example.lottery_system.dao.mapper.ActivityPrizeMapper;
import org.example.lottery_system.service.dto.ConvertActivityStatusDTO;
import org.example.lottery_system.service.enums.ActivityPrizeStatusEnum;
import org.example.lottery_system.service.enums.ActivityStatusEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ActivityOperator extends AbstractActivityOperator {
    @Autowired
    private ActivityMapper activityMapper;
    @Autowired
    private ActivityPrizeMapper  activityPrizeMapper;
    @Override
    public Integer sequence() {
        return 2;
    }

    @Override
    public Boolean needConvert(ConvertActivityStatusDTO convertActivityStatusDTO) {
        Long activityId= convertActivityStatusDTO.getActivityId();
        ActivityStatusEnum targetStatus=convertActivityStatusDTO.getTargetActivityStatus();
        if(activityId==null||
        targetStatus==null){
            return false;
        }
        ActivityDO activityDo=activityMapper.selectById(activityId);
        if (activityDo==null){
            return false;
        }
        //当前活动状态与传入的状态一致,无需转换
        if (targetStatus.name().equalsIgnoreCase(activityDo.getStatus())){
            return false;
        }
        //判断奖品是否全抽完
        int count=activityPrizeMapper.countPrize(activityId, ActivityPrizeStatusEnum.INIT.name());
        if (count>0){
            return false;
        }
        return true;
    }

    @Override
    public Boolean convert(ConvertActivityStatusDTO convertActivityStatusDTO) {
        try{
            activityMapper.updateStatus(convertActivityStatusDTO.getActivityId(),
                    convertActivityStatusDTO.getTargetActivityStatus().name());
        }catch (Exception e){
            return false;
        }
        return true;
    }
}
