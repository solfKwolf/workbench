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
        return generateToken(userId, username, jwtProperties.getExpiration());
    }

    /** 包级私有重载：供测试构造已过期 token */
    String generateToken(Long userId, String username, long ttlSeconds) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))       // sub：用户唯一标识
                .claim("username", username)           // 自定义声明
                .issuedAt(now)                         // iat
                .expiration(new Date(now.getTime() + ttlSeconds * 1000))  // exp
                .signWith(key)                         // 默认 HS256
                .compact();
    }

    /** 签名错误或过期抛 JwtException 子类 */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }
}
