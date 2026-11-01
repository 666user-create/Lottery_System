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

    /**
     * 仅清除中奖名单缓存，不动数据库记录（2026-11-02 新增）
     *
     * <p>用于「领奖履约状态变更」场景：claim / ship / confirm 只改 winning_record
     * 的 claim_status，记录本身必须保留，因此不能复用 deleteWinnerRecords
     * （那个会连数据库行一起删）。
     *
     * <p>背景缺陷：公示接口 {@code /draw/winning-records/show} 优先读 Redis
     * （键 WINNING_RECORDS_{activityId} / {activityId}_{prizeId}），
     * 而履约链路从不失效该缓存 → 实测出现「缓存里 NOT_CLAIMED、
     * 数据库已 SHIPPED」，公示信息最长滞后一个 TTL（7 天）。
     *
     * @param activityId 活动 id（必填）
     * @param recordId  中奖记录 id（用于判断是否需要连带清理奖品维度键；可为空）
     */
    void evictWinningRecordsCache(Long activityId, Long recordId);
    //查询中奖名单
    List<WinningRecordDTO> getRecords(ShowWinningRecordsParam param);
    //查询我的中奖记录
    List<WinningRecordDTO> getMyRecords(Long winnerId);
}
