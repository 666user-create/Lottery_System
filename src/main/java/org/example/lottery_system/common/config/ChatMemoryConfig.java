package org.example.lottery_system.common.config;

import org.example.lottery_system.service.ai.rag.RagProperties;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * 会话记忆配置:JdbcChatMemoryRepository + MessageWindowChatMemory
 *
 * 概念区分(容易混):
 * - ChatMemory          = 窗口策略(决定"带几条历史给模型")
 * - ChatMemoryRepository = 存储实现(决定"历史存哪")
 *
 * 为什么用 Jdbc 落库:
 * - 会话持久化到 MySQL,重启不丢、可审计分析、可直接 SQL 排查
 * - (Redis 手写 JSON):Jdbc 无自动过期,需要额外的清理任务(见 cleanupSql 注释)
 *
 */
@Configuration
public class ChatMemoryConfig {

    @Bean
    public ChatMemory jdbcChatMemory(DataSource dataSource, RagProperties props) {
        JdbcChatMemoryRepository repository = JdbcChatMemoryRepository.builder()
                .dataSource(dataSource)
                .build();

        // 窗口策略:只带最近 N 条消息给模型(控制 token 成本)
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(Math.max(2, props.getMemoryMaxMessages()))
                .build();
    }
}
