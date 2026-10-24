package org.example.lottery_system.controller;

import io.jsonwebtoken.Claims;
import org.example.lottery_system.common.pojo.CommonResult;
import org.example.lottery_system.common.utils.JWTUtil;
import org.example.lottery_system.dao.dataobject.HumanHandoverDO;
import org.example.lottery_system.dao.mapper.HumanHandoverMapper;
import org.example.lottery_system.service.ClaimService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 转人工接口 + 领奖工单处理
 * 用户端:POST /api/handover/create — 主动发起转人工
 * 管理端:GET  /api/handover/list    — 查工单(按状态 + 类型筛选)
 * 管理端:POST /api/handover/process — 处理工单(改状态+备注)
 *
 * 领奖发货复用本接口(工单 type=CLAIM):
 *   置 RESOLVED → 登记发货(需带 shippingCompany / shippingNo),同步中奖记录为「已发货」并通知用户
 *   置 CLOSED   → 取消领奖,中奖记录回退为「未领取」
 */
@RestController
@RequestMapping("/api/handover")
public class HandoverController {

    private static final Logger log = LoggerFactory.getLogger(HandoverController.class);

    @Autowired private HumanHandoverMapper handoverMapper;
    @Autowired private ClaimService claimService;

    /**
     * 用户主动发起转人工(前端点"转人工"按钮)
     * header 里带 token(可选,带了就能记 userId)
     * body: {"sessionId":"xxx","question":"我想投诉"}
     */
    @PostMapping("/create")
    public CommonResult<Long> create(@RequestBody Map<String, String> body,
                                     @RequestHeader(value = "token", required = false) String token) {
        String sessionId = body.getOrDefault("sessionId", "");
        String question = body.getOrDefault("question", "");
        if (!StringUtils.hasText(sessionId)) {
            return CommonResult.error(400, "sessionId不能为空");
        }
        Long userId = parseUserId(token);
        HumanHandoverDO handover = new HumanHandoverDO();
        handover.setUserId(userId);
        handover.setSessionId(sessionId);
        handover.setQuestion(StringUtils.hasText(question) ? question : "用户主动请求人工服务");
        handover.setStatus("PENDING");
        handoverMapper.insert(handover);
        log.info("用户发起转人工, sessionId={}, userId={}", sessionId, userId);
        return CommonResult.success(handover.getId());
    }

    /**
     * 管理端:查工单列表
     * GET /api/handover/list?status=PENDING&type=CLAIM&page=1&pageSize=10
     */
    @GetMapping("/list")
    public CommonResult<Map<String, Object>> list(@RequestParam(value = "status", required = false) String status,
                                                  @RequestParam(value = "type", required = false) String type,
                                                  @RequestParam(value = "page", defaultValue = "1") Integer page,
                                                  @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        int offset = (page - 1) * pageSize;
        List<HumanHandoverDO> list = handoverMapper.selectPage(status, type, offset, pageSize);
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", handoverMapper.count(status, type));
        return CommonResult.success(result);
    }

    /**
     * 管理端:处理工单
     * POST /api/handover/process
     * body: {"id":1,"status":"PROCESSING|RESOLVED|CLOSED","remark":"已电话联系用户","handler":"张三"}
     *
     * 领奖工单(status=RESOLVED 时表示发货)还需带:
     *   {"shippingCompany":"顺丰","shippingNo":"SF123456"}
     */
    @PostMapping("/process")
    public CommonResult<Boolean> process(@RequestBody HumanHandoverDO handoverDO) {
        if (handoverDO.getId() == null || !StringUtils.hasText(handoverDO.getStatus())) {
            return CommonResult.error(400, "id和status不能为空");
        }

        // 领奖工单:走履约分支(同步中奖记录状态 + 发货通知)
        HumanHandoverDO ticket = handoverMapper.selectById(handoverDO.getId());
        if (ticket != null && HumanHandoverDO.TYPE_CLAIM.equals(ticket.getType())) {
            if ("RESOLVED".equalsIgnoreCase(handoverDO.getStatus())) {
                return CommonResult.success(claimService.shipByTicket(handoverDO.getId(),
                        handoverDO.getShippingCompany(), handoverDO.getShippingNo(),
                        handoverDO.getHandler(), handoverDO.getRemark()));
            }
            if ("CLOSED".equalsIgnoreCase(handoverDO.getStatus())) {
                return CommonResult.success(claimService.cancelTicket(handoverDO.getId(),
                        handoverDO.getHandler(), handoverDO.getRemark()));
            }
        }

        handoverMapper.updateStatus(handoverDO);
        log.info("工单[{}] 状态更新为 {}", handoverDO.getId(), handoverDO.getStatus());
        return CommonResult.success(true);
    }

    /**
     * 管理端:删除工单(任何阶段都可删)
     * DELETE /api/handover/delete?id=1
     *
     * 领奖工单若奖品尚未发货,删除时会同时把中奖记录回退为「未领取」。
     */
    @DeleteMapping("/delete")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        return CommonResult.success(claimService.deleteTicket(id));
    }

    /** 从 token 解析 userId,解析失败返回 null(不影响转人工) */
    private Long parseUserId(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        try {
            Claims claims = JWTUtil.parseJWT(token);
            Object id = claims.get("id");
            return id != null ? Long.valueOf(id.toString()) : null;
        } catch (Exception e) {
            log.warn("token解析失败: {}", e.getMessage());
            return null;
        }
    }
}
