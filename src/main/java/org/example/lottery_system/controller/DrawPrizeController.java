package org.example.lottery_system.controller;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.example.lottery_system.common.errorcode.GlobalErrorCodeConstants;
import org.example.lottery_system.common.pojo.CommonResult;
import org.example.lottery_system.common.utils.JacksonUtil;
import org.example.lottery_system.common.utils.JWTUtil;
import org.example.lottery_system.controller.param.DrawPrizeParam;
import org.example.lottery_system.controller.param.ShowWinningRecordsParam;
import org.example.lottery_system.controller.result.WinningRecordsResult;
import org.example.lottery_system.service.DrawPrizeService;
import org.example.lottery_system.service.dto.WinningRecordDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/draw")
public class DrawPrizeController {
    @Autowired
    private DrawPrizeService drawPrizeService;
    private static Logger logger= LoggerFactory.getLogger(DrawPrizeController.class);
    @PostMapping("/prize")
    public CommonResult<Boolean> drawPrize(@Validated @RequestBody DrawPrizeParam param){
        logger.info("drawPrize param:{}",param);
        drawPrizeService.drawPrize(param);
        return CommonResult.success(true);
    }
    @PostMapping("/winning-records/show")
    public CommonResult<List<WinningRecordsResult>> showWinningRecords(@Validated @RequestBody ShowWinningRecordsParam param){
        logger.info("showWinningRecords param:{}", JacksonUtil.writeValueAsString(param));
        List<WinningRecordDTO> winningRecordsDTOList=drawPrizeService.getRecords(param);
        return CommonResult.success(convertToWinningRecordResult(winningRecordsDTOList));
    }
    /*
     * 查询当前登录用户的中奖记录
     * */
    @GetMapping("/my-records")
    public CommonResult<List<WinningRecordsResult>> myRecords(HttpServletRequest request) {
        Claims claims = JWTUtil.parseJWT(request.getHeader("token"));
        if (claims == null) {
            return CommonResult.error(GlobalErrorCodeConstants.UNAUTHORIZED.getCode(),
                    GlobalErrorCodeConstants.UNAUTHORIZED.getMsg());
        }
        Object idObj = claims.get("id");
        Long userId = idObj instanceof Long ? (Long) idObj : Long.valueOf(idObj.toString());
        List<WinningRecordDTO> winningRecordsDTOList = drawPrizeService.getMyRecords(userId);
        return CommonResult.success(convertToWinningRecordResult(winningRecordsDTOList));
    }

    private List<WinningRecordsResult> convertToWinningRecordResult(List<WinningRecordDTO> winningRecordsDTOList) {
        if(CollectionUtils.isEmpty(winningRecordsDTOList)){
            return Arrays.asList();
        }
        return winningRecordsDTOList.stream()
                .map(winningRecordDTO -> {
                        WinningRecordsResult result=new WinningRecordsResult();
                        result.setWinnerId(winningRecordDTO.getWinnerId());
                        result.setWinnerName(winningRecordDTO.getWinnerName());
                        result.setPrizeName(winningRecordDTO.getPrizeName());
                        result.setPrizeTier(winningRecordDTO.getPrizeTier().getMessage());
                        result.setWinningTime(winningRecordDTO.getWinningTime());
                        // 领奖履约:前端据此渲染状态徽标与「我要领奖 / 确认收货」按钮
                        result.setRecordId(winningRecordDTO.getRecordId());
                        result.setActivityName(winningRecordDTO.getActivityName());
                        result.setClaimStatus(winningRecordDTO.getClaimStatus());
                        result.setClaimStatusText(winningRecordDTO.getClaimStatusText());
                        result.setShippingCompany(winningRecordDTO.getShippingCompany());
                        result.setShippingNo(winningRecordDTO.getShippingNo());
                        return result;
                }).collect(Collectors.toList());
    }
}
