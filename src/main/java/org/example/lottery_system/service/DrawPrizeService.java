package org.example.lottery_system.service;

import org.example.lottery_system.controller.param.DrawPrizeParam;
import org.example.lottery_system.controller.param.ShowWinningRecordsParam;
import org.example.lottery_system.controller.result.WinningRecordsResult;
import org.example.lottery_system.dao.dataobject.WinningRecordDO;
import org.example.lottery_system.service.dto.WinningRecordDTO;

import java.util.List;

public interface DrawPrizeService {
    //异步抽奖接口
    void drawPrize(DrawPrizeParam param);
    //检验抽奖请求是否有效
    Boolean checkDrawPrizeParam(DrawPrizeParam param);
    //保存中奖者名单
    List<WinningRecordDO> saveWinnerRecords(DrawPrizeParam param);
    //删除中奖名单
    void deleteWinnerRecords(Long activityId, Long prizeId);
    //查询中奖名单
    List<WinningRecordDTO> getRecords(ShowWinningRecordsParam param);
}
