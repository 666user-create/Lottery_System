package org.example.lottery_system.service;

import org.example.lottery_system.controller.param.CreatePrizeParam;
import org.example.lottery_system.controller.param.PageParam;
import org.example.lottery_system.service.dto.PageListDTO;
import org.example.lottery_system.service.dto.PrizeDTO;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

public interface PrizeService {
    //创建奖品
    //@param param 奖品参数
    //@param picFile 奖品图片
    //@return 奖品id
    Long createPrize(CreatePrizeParam param, MultipartFile picFile);

    //查询奖品列表,翻页查询
    PageListDTO<PrizeDTO> findPrizeList(PageParam param);
}
