package org.example.lottery_system.controller;

import org.example.lottery_system.common.pojo.CommonResult;
import org.example.lottery_system.common.utils.JWTUtil;
import org.example.lottery_system.controller.result.WinningRecordsResult;
import org.example.lottery_system.dao.dataobject.WinningRecordDO;
import org.example.lottery_system.service.ClaimService;
import org.example.lottery_system.service.enums.ActivityPrizeTiersEnum;
import org.example.lottery_system.service.enums.WinningClaimStatusEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 中奖履约接口(客户端调用)
 *
 * POST /api/winning/claim   申请领奖(可同时提交收货信息)→ 生成领奖工单
 * POST /api/winning/confirm 确认收货
 *
 * 说明:发货由管理端在「工单管理」里登记(复用 /api/handover/process),
 * 客户端不能把状态改成"已发货",避免用户自行伪造发货状态。
 */
@RestController
@RequestMapping("/api/winning")
public class ClaimController {

    private static final Logger log = LoggerFactory.getLogger(ClaimController.class);

    @Autowired private ClaimService claimService;

    /**
     * 客户端:申请领奖
     * body: {"recordId":123,"receiverName":"张三","receiverPhone":"138...","receiverAddress":"..."}
     * 收货信息三项可省略,省略时使用个人中心已保存的默认地址。
     */
    @PostMapping("/claim")
    public CommonResult<Long> claim(@RequestBody Map<String, Object> body,
                                    @RequestHeader(value = "token", required = false) String token) {
        Long userId = currentUserId(token);
        if (userId == null) {
            return CommonResult.error(401, "未登录或Token已失效");
        }
        Long recordId = parseLong(body.get("recordId"));
        if (recordId == null) {
            return CommonResult.error(400, "recordId不能为空");
        }
        Long ticketId = claimService.claim(userId, recordId,
                str(body.get("receiverName")), str(body.get("receiverPhone")), str(body.get("receiverAddress")));
        return CommonResult.success(ticketId);
    }

    /**
     * 客户端:确认收货
     * body: {"recordId":123}
     */
    @PostMapping("/confirm")
    public CommonResult<Boolean> confirm(@RequestBody Map<String, Object> body,
                                         @RequestHeader(value = "token", required = false) String token) {
        Long userId = currentUserId(token);
        if (userId == null) {
            return CommonResult.error(401, "未登录或Token已失效");
        }
        Long recordId = parseLong(body.get("recordId"));
        if (recordId == null) {
            return CommonResult.error(400, "recordId不能为空");
        }
        return CommonResult.success(claimService.confirmReceived(userId, recordId));
    }

    /** 从 token 取当前登录用户 id(JWTUtil 返回 Integer,这里转 Long) */
    private Long currentUserId(String token) {
        Integer userId = JWTUtil.getUserIdFromToken(token);
        return userId == null ? null : userId.longValue();
    }

    // ==================== 管理端:中奖履约管理 ====================

    /**
     * 管理端:中奖记录列表(可按领奖状态筛选)
     * GET /api/winning/manage/list?claimStatus=NOT_CLAIMED&page=1&pageSize=10
     */
    @GetMapping("/manage/list")
    public CommonResult<Map<String, Object>> manageList(
            @RequestParam(value = "claimStatus", required = false) String claimStatus,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        int offset = (page - 1) * pageSize;
        List<WinningRecordDO> records = claimService.listForAdmin(claimStatus, offset, pageSize);
        List<WinningRecordsResult> list = new ArrayList<>();
        if (records != null) {
            for (WinningRecordDO record : records) {
                list.add(toResult(record));
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", claimService.countForAdmin(claimStatus));
        return CommonResult.success(result);
    }

    /**
     * 管理端:直接修改领奖状态(覆盖式,含标记已发货/已签收/重置未领取)
     * POST /api/winning/manage/status
     * body: {"recordId":123,"claimStatus":"SHIPPED","shippingCompany":"顺丰","shippingNo":"SF123"}
     */
    @PostMapping("/manage/status")
    public CommonResult<Boolean> manageStatus(@RequestBody Map<String, Object> body) {
        Long recordId = parseLong(body.get("recordId"));
        String claimStatus = str(body.get("claimStatus"));
        if (recordId == null || claimStatus == null) {
            return CommonResult.error(400, "recordId和claimStatus不能为空");
        }
        return CommonResult.success(claimService.adminUpdateStatus(
                recordId, claimStatus.toUpperCase(), str(body.get("shippingCompany")), str(body.get("shippingNo"))));
    }

    /** 中奖记录 → 返回结果(补齐状态文案与奖项文案) */
    private WinningRecordsResult toResult(WinningRecordDO record) {
        WinningRecordsResult result = new WinningRecordsResult();
        result.setRecordId(record.getId());
        result.setWinnerId(record.getWinnerId());
        result.setWinnerName(record.getWinnerName());
        result.setActivityName(record.getActivityName());
        result.setPrizeName(record.getPrizeName());
        ActivityPrizeTiersEnum tier = ActivityPrizeTiersEnum.forName(record.getPrizeTier());
        result.setPrizeTier(tier == null ? record.getPrizeTier() : tier.getMessage());
        result.setWinningTime(record.getWinningTime());
        result.setClaimStatus(WinningClaimStatusEnum.normalize(record.getClaimStatus()));
        result.setClaimStatusText(WinningClaimStatusEnum.messageOf(record.getClaimStatus()));
        result.setShippingCompany(record.getShippingCompany());
        result.setShippingNo(record.getShippingNo());
        return result;
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.valueOf(value.toString().trim());
        } catch (NumberFormatException e) {
            log.warn("参数不是合法数字: {}", value);
            return null;
        }
    }

    private String str(Object value) {
        return value == null ? null : value.toString();
    }
}
