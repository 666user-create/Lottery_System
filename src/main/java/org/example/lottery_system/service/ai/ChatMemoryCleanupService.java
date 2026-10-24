package org.example.lottery_system.service.ai;

import org.example.lottery_system.dao.mapper.ChatMemoryCleanupMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * 对话记录定期清理。
 *
 * 背景:framework 模式的会话记忆落在 SPRING_AI_CHAT_MEMORY 表,只增不减;
 * 长期运行会让表持续膨胀(实测一次压测就积累了几十条/多个会话)。
 *
 * 策略:
 *   - 保留最近 {@code app.chat-memory.retention-days} 天(默认 3 天)
 *   - 按 {@code app.chat-memory.cleanup-cron} 定时清理(默认每天凌晨 3 点检查一次)
 *
 * 说明:
 *   1. simple 模式的记忆在 Redis,靠 Key 自身的 TTL 过期(ai.memory.expire-seconds),不需要这里清理。
 *   2. 清理频率默认"每天检查一次、删 3 天前的"——这样记录最长存活约 3 天,
 *      不会像"每 3 天清理一次"那样最多滞留到 6 天。想严格每 3 天跑一次可把 cron 改成
 *      {@code 0 0 3 *&#47;3 * ?}。
 */
@Service
public class ChatMemoryCleanupService {

    private static final Logger log = LoggerFactory.getLogger(ChatMemoryCleanupService.class);

    @Value("${app.chat-memory.retention-days:3}")
    private int retentionDays;

    @Autowired private ChatMemoryCleanupMapper chatMemoryCleanupMapper;

    /** 定时清理(默认每天 03:00) */
    @Scheduled(cron = "${app.chat-memory.cleanup-cron:0 0 3 * * ?}")
    public void scheduledCleanup() {
        try {
            cleanupExpired();
        } catch (Exception e) {
            // 清理失败不应影响其他任务/服务
            log.error("对话记录定时清理失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 删除超过保留天数的对话记录
     *
     * @return 删除行数
     */
    public int cleanupExpired() {
        if (retentionDays <= 0) {
            log.warn("保留天数配置为 {} 天,跳过清理(避免误删全部记录)", retentionDays);
            return 0;
        }
        Date before = Date.from(Instant.now().minus(retentionDays, ChronoUnit.DAYS));
        int deleted = chatMemoryCleanupMapper.deleteBefore(before);
        log.info("对话记录清理完成: 保留 {} 天,删除 {} 条(早于 {})", retentionDays, deleted, before);
        return deleted;
    }
}
