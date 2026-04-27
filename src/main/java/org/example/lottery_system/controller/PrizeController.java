package org.example.lottery_system.controller;

import org.example.lottery_system.common.errorcode.ControllerErrorCodeConstants;
import org.example.lottery_system.common.exception.ControllerException;
import org.example.lottery_system.common.pojo.CommonResult;
import org.example.lottery_system.common.utils.JacksonUtil;
import org.example.lottery_system.controller.param.CreatePrizeParam;
import org.example.lottery_system.controller.param.PageParam;
import org.example.lottery_system.controller.result.FindPrizeListResult;
import org.example.lottery_system.dao.dataobject.PrizeDO;
import org.example.lottery_system.service.PictureService;
import org.example.lottery_system.service.PrizeService;
import org.example.lottery_system.service.dto.PageListDTO;
import org.example.lottery_system.service.dto.PrizeDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/prize")
public class PrizeController {
    private static final Logger logger = LoggerFactory.getLogger(PrizeController.class);
    @Autowired
    private PictureService pictureService;
    @Autowired
    private PrizeService prizeService;
    //上传奖品图片
    @PostMapping("/pic/upload")
    public String uploadPic(MultipartFile multipartFile) {
        return pictureService.savePicture(multipartFile);
    }
    //创建奖品
    //@RequestPart用于接收表单数据,表单支持文件上传
    @PostMapping("/create")
    public CommonResult<Long> createPrize(@Validated @RequestPart("param") CreatePrizeParam param,
                                          @RequestPart("prizePic") MultipartFile picFile) {
        logger.info("创建奖品:{}", JacksonUtil.writeValueAsString(param));
        return CommonResult.success(prizeService.createPrize(param, picFile));
    }
    //查询所有奖品
    @GetMapping("/find-list")
    public CommonResult<FindPrizeListResult> findPrizeListResult(PageParam param){
        logger.info("查询奖品:{}", JacksonUtil.writeValueAsString(param));
        PageListDTO<PrizeDTO> pageListDTO = prizeService.findPrizeList(param);
        return CommonResult.success(convertToFindPrizeListResult(pageListDTO));
    }

    private FindPrizeListResult convertToFindPrizeListResult(PageListDTO<PrizeDTO> pageListDTO) {
        if(pageListDTO==null){
            throw new ControllerException(ControllerErrorCodeConstants.PRIZE_NOT_FOUND_ERROR);
        }
        FindPrizeListResult result=new FindPrizeListResult();
        result.setTotal(pageListDTO.getTotal());
        result.setRecords(
                pageListDTO.getRecords().stream()
                        .map(prizeDTO -> {
                            FindPrizeListResult.PrizeInfo prizeInfo=new FindPrizeListResult.PrizeInfo();
                            prizeInfo.setPrizeId(prizeDTO.getPrizeId());
                            prizeInfo.setPrizeName(prizeDTO.getName());
                            prizeInfo.setDescription(prizeDTO.getDescription());
                            prizeInfo.setPrice(prizeDTO.getPrice());
                            prizeInfo.setImageUrl(prizeDTO.getImageUrl());
                            return prizeInfo;
                        }).collect(Collectors.toList())
        );
        return result;
    }
}
