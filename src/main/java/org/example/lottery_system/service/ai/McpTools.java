package org.example.lottery_system.service.ai;

import org.example.lottery_system.dao.dataobject.ActivityDO;
import org.example.lottery_system.dao.dataobject.PrizeDO;
import org.example.lottery_system.dao.dataobject.WinningRecordDO;
import org.example.lottery_system.dao.mapper.ActivityMapper;
import org.example.lottery_system.dao.mapper.FaqMapper;
import org.example.lottery_system.dao.mapper.PrizeMapper;
import org.example.lottery_system.dao.mapper.WinningRecordMapper;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP 工具集:把抽奖系统的数据查询能力暴露为 MCP 标准工具。
 *
 * 说明:
 * 1. 每个 @Tool 方法都会自动注册为 MCP Server 的一个工具,任何支持 MCP 的 AI 客户端都能调用。
 * 2. 所有工具都是【只读查询】,绝不暴露写操作(防提示词注入)。
 * 3. 方法返回统一用"字段:值"文本格式,方便 AI 理解。
 *
 * ⚠️ 安全约束(2026-10-10 修复,勿回退):
 * 本工具集会被两类【无鉴权】入口调用:
 *   (a) POST /api/ai/chat —— 在 AppConfig 免登录白名单内
 *   (b) MCP 端点 /sse、/mcp/message —— 有意设计为任何 MCP 客户端可无鉴权调用
 * 因此这里【绝不能】返回任何个人信息或管理端数据。本次已移除三个工具:
 *   - queryUsersByIdentity : 会返回全量用户【邮箱】(邮箱在库中为明文)
 *   - queryUserInfo        : 会返回指定用户【邮箱】
 *   - queryHandoverTickets : 会返回用户转人工的【问题原文/处理备注】,属管理端数据
 * 如需查用户,请走 /user/base-user/find-list(受 adminOnly 保护);
 * 如需查工单,请走 /api/handover/list(受 adminOnly 保护)。
 *
 * 历史背景:本项目最早在 XiaoDongChatService 里手写了工具调用(TOOLS JSON + 解析循环),
 * 现在已迁移到 MCP 标准。手写版本代码保留在 XiaoDongChatService 中作为参考,未删除。
 */
@Service
public class McpTools {

    /**
     * 个人信息类工具的前缀约定:依赖"当前登录用户"(通过 ToolContext 传入),【不对外暴露】给 MCP 客户端。
     * 见 McpConfig(对外工具集)与 InternalToolCallbacks(站内全量工具集)。
     */
    public static final String PERSONAL_TOOL_PREFIX = "queryMy";

    /** 是否为个人信息类工具 */
    public static boolean isPersonalTool(String toolName) {
        return toolName != null && toolName.startsWith(PERSONAL_TOOL_PREFIX);
    }

    @Autowired private ActivityMapper activityMapper;
    @Autowired private PrizeMapper prizeMapper;
    @Autowired private WinningRecordMapper winningRecordMapper;
    @Autowired private FaqMapper faqMapper;

    // ==================== 1. 活动相关 ====================

    /**
     * 查询所有活动列表
     */
    @Tool(description = "查询抽奖系统所有活动列表,包含活动ID、名称、描述、状态(INIT未开始/RUNNING进行中/COMPLETED已完成)")
    public String queryAllActivities() {
        int total = activityMapper.count();
        if (total == 0) {
            return "暂无任何活动";
        }
        return activityMapper.selectActivityList(0, total).stream()
                .map(a -> String.format("活动ID:%d | 名称:%s | 描述:%s | 状态:%s",
                        a.getId(), a.getActivityName(), a.getDescription(), a.getStatus()))
                .collect(Collectors.joining("\n"));
    }

    /**
     * 按活动ID查活动详情
     */
    @Tool(description = "按活动ID查询活动详情")
    public String queryActivityDetail(
            @ToolParam(description = "活动ID") Long activityId) {
        ActivityDO activity = activityMapper.selectById(activityId);
        if (activity == null) {
            return "活动不存在: ID=" + activityId;
        }
        return String.format("活动ID:%d | 名称:%s | 描述:%s | 状态:%s",
                activity.getId(), activity.getActivityName(), activity.getDescription(), activity.getStatus());
    }

    /**
     * 按状态查活动列表
     */
    @Tool(description = "按状态查询活动列表,状态可选: INIT(未开始)/RUNNING(进行中)/COMPLETED(已完成),不传则查全部")
    public String queryActivityByStatus(
            @ToolParam(description = "活动状态,可选 INIT/RUNNING/COMPLETED", required = false) String status) {
        List<ActivityDO> all = activityMapper.selectActivityList(0, activityMapper.count());
        List<ActivityDO> filtered = all.stream()
                .filter(a -> status == null || status.isBlank() || status.equalsIgnoreCase(a.getStatus()))
                .collect(Collectors.toList());
        if (filtered.isEmpty()) {
            return "没有状态为 " + status + " 的活动";
        }
        return filtered.stream()
                .map(a -> String.format("活动ID:%d | 名称:%s | 描述:%s | 状态:%s",
                        a.getId(), a.getActivityName(), a.getDescription(), a.getStatus()))
                .collect(Collectors.joining("\n"));
    }

    // ==================== 2. 奖品相关 ====================

    /**
     * 查询所有奖品
     */
    @Tool(description = "查询所有奖品列表,包含奖品ID、名称、描述、价格(元)")
    public String queryAllPrizes() {
        int total = prizeMapper.count();
        if (total == 0) {
            return "暂无任何奖品";
        }
        return prizeMapper.selectPrizeList(0, total).stream()
                .map(p -> String.format("奖品ID:%d | 名称:%s | 描述:%s | 价格:%.2f元",
                        p.getId(), p.getName(), p.getDescription(), p.getPrice()))
                .collect(Collectors.joining("\n"));
    }

    /**
     * 统计奖品总数
     */
    @Tool(description = "统计当前奖品总数")
    public String countAllPrizes() {
        return "当前奖品总数: " + prizeMapper.count() + " 个";
    }

    // ==================== 3. 中奖记录相关 ====================

    /**
     * 按用户ID查中奖记录
     */
    @Tool(description = "按用户ID查询该用户的所有中奖记录(活动名、奖品名、奖品等级、中奖时间)")
    public String queryWinningRecordsByUser(
            @ToolParam(description = "用户ID") Long userId) {
        List<WinningRecordDO> list = winningRecordMapper.selectByWinnerId(userId);
        if (list == null || list.isEmpty()) {
            return "该用户暂无中奖记录";
        }
        return list.stream()
                .map(w -> String.format("活动:%s | 奖品:%s | 等级:%s | 时间:%s",
                        w.getActivityName(), w.getPrizeName(), w.getPrizeTier(), w.getWinningTime()))
                .collect(Collectors.joining("\n"));
    }

    /**
     * 查询【当前登录用户】自己的中奖记录
     *
     * 与 queryWinningRecordsByUser 的关键区别:
     * - 本工具不向模型暴露用户ID参数,而是从 ToolContext 取当前登录用户ID
     *   (由 FrameworkChatService 从登录 token 解析后注入),
     *   因此用户问"我中奖了吗"时不需要报自己的用户ID。
     * - 供外部 MCP 客户端调用时若未携带上下文,会提示改用按ID查询。
     */
    @Tool(description = "查询当前登录用户自己的中奖记录(无需任何参数,系统自动识别当前登录用户)。"
            + "当用户问『我中奖了吗』『我的中奖记录』等个人数据时,必须优先使用本工具,不要反问用户ID")
    public String queryMyWinningRecords(ToolContext toolContext) {
        Map<String, Object> ctx = (toolContext == null || toolContext.getContext() == null)
                ? Collections.emptyMap() : toolContext.getContext();

        // 匿名用户:FrameworkChatService 会注入 anonymous 标记(保证上下文非空,避免框架抛异常)
        if (Boolean.TRUE.equals(ctx.get("anonymous"))) {
            return "当前用户未登录:请提示用户先登录后再查询个人中奖记录;"
                    + "若用户希望现在就查,可请其提供用户ID,再调用 queryWinningRecordsByUser 查询。";
        }

        Object idObj = ctx.get("userId");
        if (idObj == null) {
            return "未获取到当前登录用户信息(用户可能未登录)。"
                    + "请提示用户先登录后再查询个人中奖记录;若用户坚持现在查询,可请其提供用户ID,"
                    + "再调用 queryWinningRecordsByUser 查询。";
        }
        Long currentUserId;
        try {
            currentUserId = idObj instanceof Long ? (Long) idObj : Long.valueOf(idObj.toString());
        } catch (Exception e) {
            return "登录用户ID格式异常,无法查询中奖记录";
        }
        List<WinningRecordDO> list = winningRecordMapper.selectByWinnerId(currentUserId);
        if (list == null || list.isEmpty()) {
            return "当前登录用户(用户ID:" + currentUserId + ")暂无中奖记录";
        }
        return list.stream()
                .map(w -> String.format("活动:%s | 奖品:%s | 等级:%s | 时间:%s",
                        w.getActivityName(), w.getPrizeName(), w.getPrizeTier(), w.getWinningTime()))
                .collect(Collectors.joining("\n"));
    }

    /**
     * 按活动ID查中奖名单
     */
    @Tool(description = "按活动ID查询该活动的所有中奖名单(中奖者姓名、奖品名、奖品等级)")
    public String queryWinnersByActivity(
            @ToolParam(description = "活动ID") Long activityId) {
        List<WinningRecordDO> list = winningRecordMapper.selectByActivityId(activityId);
        if (list == null || list.isEmpty()) {
            return "该活动暂无中奖记录";
        }
        return list.stream()
                .map(w -> String.format("中奖者:%s | 奖品:%s | 等级:%s | 时间:%s",
                        w.getWinnerName(), w.getPrizeName(), w.getPrizeTier(), w.getWinningTime()))
                .collect(Collectors.joining("\n"));
    }

    // ==================== 4. 知识库(FAQ) ====================

    /**
     * 查知识库FAQ
     */
    @Tool(description = "按关键词查询AI客服知识库(FAQ)条目,返回问题和答案")
    public String queryFaq(
            @ToolParam(description = "关键词,如:抽奖/领奖/账号") String keyword) {
        List<org.example.lottery_system.dao.dataobject.FaqDO> list = faqMapper.searchByKeyword(
                java.util.Collections.singletonList(keyword), 10);
        if (list == null || list.isEmpty()) {
            return "知识库中未找到与「" + keyword + "」相关的内容";
        }
        return list.stream()
                .map(f -> String.format("Q:%s\nA:%s", f.getQuestion(), f.getAnswer()))
                .collect(Collectors.joining("\n\n"));
    }
}
