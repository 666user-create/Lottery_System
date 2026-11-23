package org.example.lottery_system.common.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParserBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;

public class JWTUtil {
    private static final Logger logger =
            LoggerFactory.getLogger(JWTUtil.class);
    /**
     * 密钥：Base64编码的密钥
     */
    private static final String SECRET =
            "kjgBRpTsXnuU/RMT0uXcpJF/+PmsVcqRa8DhAZswjhY=";
    /**
     * 生成安全密钥：将一个Base64编码的密钥解码并创建一个HMAC SHA密钥。
     */
    private static final SecretKey SECRET_KEY =
            Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
    /**
     * 过期时间(单位: 毫秒)，1 小时。
     *
     * 注：早期版本另有一套「Redis 记录吊销时刻 + 比对 iat」的旧 token 失效方案，
     * 其记录 TTL 引用本常量维持「保留时长 ≥ token 最长存活」的不变式。
     * 该方案已由 token_version（令牌代数）取代并删除，现在本常量只决定 token 自身寿命。
     */
    public static final long EXPIRATION = 60 * 60 * 1000;

    /**
     * 生成密钥
     */
    public static String genJwt(Map<String, Object> claim) {
        //签名算法
        String jwt = Jwts.builder()
                .setClaims(claim) // 自定义内容(载荷)
                .setIssuedAt(new Date()) // 设置签发时间
                .setExpiration(new Date(System.currentTimeMillis() +
                        EXPIRATION)) // 设置过期时间
                .signWith(SECRET_KEY) // 签名算法
                .compact();
        return jwt;
    }

    /**
     * 验证密钥
     */
    public static Claims parseJWT(String jwt) {
        if (!StringUtils.hasLength(jwt)) {
            return null;
        }
        // 创建解析器, 设置签名密钥
        JwtParserBuilder jwtParserBuilder =
                Jwts.parserBuilder().setSigningKey(SECRET_KEY);
        Claims claims = null;
        try {
            //解析token
            claims = (Claims) jwtParserBuilder.build().parseClaimsJws(jwt).getBody();
        } catch (Exception e) {
            //签名验证失败（不打印完整 token，避免泄露）
            logger.error("解析令牌错误", e);
        }
        return claims;
    }

    /**
     * 从已解析的 Claims 取用户 id，与登录签发时的 claim 键名 {@code id} 一致。
     *
     * 只认 Number（JJWT 把签发的 Long 按数值大小还原为 Integer/Long/BigInteger），
     * 统一 longValue()——主键是 bigint，窄化成 Integer 会在超 21 亿时静默溢出。
     * 非 Number（含字符串）一律返回 null：认证 fail-closed，不兼容异常 token。
     */
    public static Long getUserId(Claims claims) {
        if (claims == null) {
            return null;
        }
        Object id = claims.get("id");
        return id instanceof Number ? ((Number) id).longValue() : null;
    }

    /**
     * 从 token 字符串直接取用户 id（含验签；token 无效/缺失返回 null）。
     */
    public static Long getUserIdFromToken(String jwtToken) {
        return getUserId(parseJWT(jwtToken));
    }

    /** claims 中令牌版本号的键名，签发与校验两处共用，避免字符串写错不同步 */
    public static final String CLAIM_TOKEN_VERSION = "ver";

    /**
     * 取令牌版本号（令牌代数）。
     *
     * <p>返回语义：
     * <ul>
     *   <li>{@code null} —— claims 本身为空（未登录/验签失败），调用方应直接拒绝</li>
     *   <li>{@code 0} —— token 里没有 ver claim。这是**版本号机制上线前签发的老 token**，
     *       按初始代数 0 处理，与 user.token_version 的列默认值一致，
     *       从而保证上线瞬间不会把所有在途会话踢掉</li>
     *   <li>其他整数 —— 该 token 签发时的代数</li>
     * </ul>
     *
     * <p>只认 Number（JJWT 会把整数还原成 Integer/Long），非数值一律按 0 处理：
     * 宁可让畸形 token 走"版本不符"被拒，也不抛异常打断请求。
     */
    public static Integer getTokenVersion(Claims claims) {
        if (claims == null) {
            return null;
        }
        Object ver = claims.get(CLAIM_TOKEN_VERSION);
        return ver instanceof Number ? ((Number) ver).intValue() : 0;
    }
}