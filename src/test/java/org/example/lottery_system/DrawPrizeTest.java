package org.example.lottery_system;

import org.example.lottery_system.controller.param.DrawPrizeParam;
import org.example.lottery_system.controller.param.ShowWinningRecordsParam;
import org.example.lottery_system.service.DrawPrizeService;
import org.example.lottery_system.service.activitystatus.ActivityStatusManager;
import org.example.lottery_system.service.dto.ConvertActivityStatusDTO;
import org.example.lottery_system.service.dto.WinningRecordDTO;
import org.example.lottery_system.service.enums.ActivityPrizeStatusEnum;
import org.example.lottery_system.service.enums.ActivityStatusEnum;
import org.example.lottery_system.service.enums.ActivityUserStatusEnum;
import org.example.lottery_system.service.impl.ActivityServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@SpringBootTest
public class DrawPrizeTest {
    @Autowired
    private DrawPrizeService drawPrizeService;
    @Autowired
    private ActivityServiceImpl activityServiceImpl;
    @Autowired
    private ActivityStatusManager activityStatusManager;
    @Test
    public void drawPrizeTest() {
        DrawPrizeParam param=new DrawPrizeParam();
        param.setActivityId(1L);
        param.setPrizeId(1L);
        param.setWinningTime(new Date());
        List<DrawPrizeParam.Winner> winnerList=new ArrayList<>();
        DrawPrizeParam.Winner winner=new DrawPrizeParam.Winner();
        winner.setUserId(1L);
        winner.setUserName("张三");
        winnerList.add(winner);
        param.setWinnerList(winnerList);
        drawPrizeService.drawPrize(param);
    }

    @Test
    public void statusConvert(){
        ConvertActivityStatusDTO convertActivityStatusDTO = new ConvertActivityStatusDTO();
        convertActivityStatusDTO.setActivityId(33L);
        convertActivityStatusDTO.setTargetActivityStatus(ActivityStatusEnum.COMPLETED);
        convertActivityStatusDTO.setPrizeId(19L);
        convertActivityStatusDTO.setTargetPrizeStatus(ActivityPrizeStatusEnum.COMPLETED);
        convertActivityStatusDTO.setUserIds(List.of(52L));
        convertActivityStatusDTO.setTargetUserStatus(ActivityUserStatusEnum.COMPLETED);
        activityStatusManager.handlerEvent(convertActivityStatusDTO);
    }

    @Test
    public void showWinnerRecords(){
        ShowWinningRecordsParam param=new ShowWinningRecordsParam();
        param.setActivityId(34L);
        param.setPrizeId(25L);
        List<WinningRecordDTO> winningRecordDTOList=drawPrizeService.getRecords(param);
        for (WinningRecordDTO dto:winningRecordDTOList){
            System.out.println(dto.getWinnerName()+" "+dto.getPrizeName()+" "+dto.getPrizeTier());
        }
    }
}
