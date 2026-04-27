package org.example.lottery_system.dao.dataobject;

import lombok.Data;

import java.util.Date;

@Data
public class BaseDO {
    //主键
    private Long id;
    //创建时间
    private Date gmtCreate;
    //修改时间
    private Date gmtModified;
}
