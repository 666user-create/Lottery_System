package org.example.lottery_system.dao.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;

/**
 * 会话记忆清理(定时任务用)。
 *
 * 表 SPRING_AI_CHAT_MEMORY 由 Spring AI 的 JdbcChatMemoryRepository 维护(framework 模式)。
 * 该表只增不减,长期运行会持续膨胀,所以按保留天数定期清理。
 */
@Mapper
public interface ChatMemoryCleanupMapper {

    /**
     * 删除指定时间之前的所有会话记忆
     *
     * @param before 时间界限(早于该时间的记录会被删除)
     * @return 删除行数
     */
    @Delete("delete from SPRING_AI_CHAT_MEMORY where `timestamp` < #{before}")
    int deleteBefore(@Param("before") Date before);
}
