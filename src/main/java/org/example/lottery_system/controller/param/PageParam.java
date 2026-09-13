package org.example.lottery_system.controller.param;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serializable;

@Data
public class PageParam implements Serializable {
    //当前页
    //修复 C-2：原实现无任何边界校验 → currentPage=0/-1 时 offset 为负数，
    //生成 SQL `LIMIT -10,10` → MySQL 语法错误 → 接口返回 500
    @Min(value = 1, message = "当前页不能小于1")
    private Integer currentPage=1;
    //每页数量
    //修复 C-2：增加上下限，避免 pageSize<=0 造成 SQL 异常、过大造成资源耗尽
    @Min(value = 1, message = "每页数量不能小于1")
    @Max(value = 100, message = "每页数量不能大于100")
    private Integer pageSize=10;

    //偏移量
    public Integer offset(){
        return (currentPage-1)*pageSize;
    }
}
