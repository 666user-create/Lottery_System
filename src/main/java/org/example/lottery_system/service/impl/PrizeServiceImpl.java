package org.example.lottery_system.service.impl;

import org.example.lottery_system.controller.param.CreatePrizeParam;
import org.example.lottery_system.controller.param.PageParam;
import org.example.lottery_system.dao.dataobject.PrizeDO;
import org.example.lottery_system.dao.mapper.PrizeMapper;
import org.example.lottery_system.service.PictureService;
import org.example.lottery_system.service.PrizeService;
import org.example.lottery_system.service.dto.PageListDTO;
import org.example.lottery_system.service.dto.PrizeDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
public class PrizeServiceImpl implements PrizeService {
    @Autowired
    private PictureService pictureService;
    @Autowired
    private PrizeMapper prizeMapper;
    @Override
    public Long createPrize(CreatePrizeParam param, MultipartFile picFile) {
        //上传奖品图片
        String fileName = pictureService.savePicture(picFile);
        //存库
        PrizeDO prizeDo = new PrizeDO();
        prizeDo.setName(param.getPrizeName());
        prizeDo.setDescription(param.getDescription());
        prizeDo.setPrice(param.getPrice());
        prizeDo.setImageUrl(fileName);
        prizeMapper.insert(prizeDo);
        return prizeDo.getId();
    }

    @Override
    public PageListDTO<PrizeDTO> findPrizeList(PageParam param) {
        //总量
        int total= prizeMapper.count();

        //分页查询
        List<PrizeDTO> prizeDTOList=new ArrayList<>();
        List<PrizeDO> prizeDOList=prizeMapper.selectPrizeList(param.offset(),param.getPageSize());
        for(PrizeDO prizeDO:prizeDOList){
            PrizeDTO prizeDTO=new PrizeDTO();
            prizeDTO.setPrizeId(prizeDO.getId());
            prizeDTO.setName(prizeDO.getName());
            prizeDTO.setDescription(prizeDO.getDescription());
            prizeDTO.setPrice(prizeDO.getPrice());
            prizeDTO.setImageUrl(prizeDO.getImageUrl());
            prizeDTOList.add(prizeDTO);
        }
        return new PageListDTO<>(total,prizeDTOList);
    }

}
