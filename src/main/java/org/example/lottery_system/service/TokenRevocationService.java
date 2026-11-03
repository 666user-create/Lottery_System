package org.example.lottery_system.service;

import org.example.lottery_system.common.utils.JWTUtil;
import org.example.lottery_system.common.utils.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 令牌吊销：改密后，此前签发的旧 token 立即失效，而不是继续用到自然过期（最长 1h）。
 *
 * <p>实现是「吊销时刻」而非「拉黑键」：Redis 记一条
 * token_revoked_before:{userId} = 吊销发生的 epoch 秒，登录态校验时比较
 * claims.getIssuedAt() &lt; 该时刻 → 拒绝。这样：
 * <ul>
 *   <li>改密前的旧 token（签发早于时刻）→ 失效；</li>
 *   <li>改密后重新登录拿到的新 token（签发晚于时刻）→ 正常放行，不会被误伤；
 *       若只记「键存在即拉黑」，用户改密后立刻重登也会被自己的吊销记录挡在门外。</li>
 * </ul>
 *
 * <p>TTL 与 JWT 有效期等长（共用 JWTUtil.EXPIRATION）：1 小时后所有旧 token
 * 本就已自然过期，这条记录失去意义，让 Redis 自动回收，无需清理任务。
 *
 * <p>Redis 不可用时按「无吊销」处理（fail-open）：宁可降级窗口内旧 token 仍可用，
 * 也不让 Redis 故障把所有登录用户挡在门外。吊销写入失败同理只记 WARN——
 * 它影响的是"提前失效"这一增强语义，不是改密本身。
 */
@Service
public class TokenRevocationService {

    private static final Logger log = LoggerFactory.getLogger(TokenRevocationService.class);

    private static final String KEY_PREFIX = "token_revoked_before:";

    @Autowired
    private RedisUtil redisUtil;

    /** 吊销该用户此刻之前签发的全部 token（在密码修改成功之后调用） */
    public void revokeTokensIssuedBefore(Long userId) {
        if (userId == null) {
            return;
        }
        long nowEpochSecond = System.currentTimeMillis() / 1000;
        boolean stored = redisUtil.set(KEY_PREFIX + userId,
                String.valueOf(nowEpochSecond), JWTUtil.EXPIRATION / 1000);
        if (!stored) {
            log.warn("写入令牌吊销记录失败，旧 token 将按原样存活至过期: userId={}", userId);
        }
    }

    /** 该用户的吊销时刻（epoch 秒）；无记录/解析失败返回 null（= 无吊销） */
    public Long getRevokedBeforeEpochSecond(Long userId) {
        if (userId == null) {
            return null;
        }
        String value = redisUtil.get(KEY_PREFIX + userId);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            log.warn("吊销记录值异常，按无吊销处理: userId={}, value={}", userId, value);
            return null;
        }
    }
}
