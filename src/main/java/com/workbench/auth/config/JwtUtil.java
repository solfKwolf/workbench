package com.workbench.auth.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/** jjwt 0.12.x API（网上旧教程的 setSubject/parserBuilder 已废弃，注意区分） */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final JwtProperties jwtProperties;
    private SecretKey key;

    /** 启动时把字符串密钥转成 HMAC-SHA 算法要求的 SecretKey */
    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(
                jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Long userId, String username) {
        return buildToken(userId, username, "access", jwtProperties.getExpiration());
    }

    /** 签发 Refresh Token（JWT 格式，7 天有效期，额外带 type=refresh 声明） */
    public String signRefreshToken(Long userId, String username) {
        return buildToken(userId, username, "refresh", jwtProperties.getRefreshExpiration());
    }

    /** 统一构建 JWT（内部方法） */
    private String buildToken(Long userId, String username, String type, long ttlSeconds) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("type", type)                   // access / refresh
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlSeconds * 1000))
                .signWith(key)
                .compact();
    }

    /** 包级私有重载：供测试构造已过期 token */
    String generateToken(Long userId, String username, long ttlSeconds) {
        return buildToken(userId, username, "access", ttlSeconds);
    }

    /** 签名错误或过期抛 JwtException 子类 */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 解析 Refresh Token 并校验 type 声明 */
    public Claims parseRefreshToken(String token) {
        Claims claims = parseToken(token);
        String type = claims.get("type", String.class);
        if (!"refresh".equals(type)) {
            throw new io.jsonwebtoken.JwtException("Invalid token type: expected refresh");
        }
        return claims;
    }

    public Long getUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }
}
