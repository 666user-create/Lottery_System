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
     * 过期时间(单位: 毫秒)
     */
    private static final long EXPIRATION = 60 * 60 * 1000;

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
     * 从 token 中取用户 id，与登录签发时的 claim 键名 {@code id} 一致。
     */
    public static Integer getUserIdFromToken(String jwtToken) {
        Claims claims = JWTUtil.parseJWT(jwtToken);
        if (claims == null) {
            return null;
        }
        Object id = claims.get("id");
        if (id instanceof Integer) {
            return (Integer) id;
        }
        if (id instanceof Long) {
            return ((Long) id).intValue();
        }
        if (id instanceof Number) {
            return ((Number) id).intValue();
        }
        return null;
    }
}