package org.example.lottery_system.controller.param;

import lombok.Data;

import java.io.Serializable;

@Data
public class PageParam implements Serializable {
    //当前页
    private Integer currentPage=1;
    //每页数量
    private Integer pageSize=10;

    //偏移量
    public Integer offset(){
        return (currentPage-1)*pageSize;
    }
}
