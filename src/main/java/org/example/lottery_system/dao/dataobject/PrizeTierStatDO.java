package org.example.lottery_system.dao.dataobject;

import lombok.Data;

/*
* 奖品等级中奖数量统计结果
* 说明：这不是一张数据表，而是 group by 聚合查询的接收对象
* */
@Data
public class PrizeTierStatDO {
    //奖品等级（库中存的是枚举名，如 FIRST_PRIZE）
    private String prizeTier;
    //该等级中奖数量（SQL 中 alias 为 cnt）
    private Integer cnt;
}
