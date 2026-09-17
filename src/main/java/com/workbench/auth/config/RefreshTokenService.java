package com.workbench.auth.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Refresh Token 的 Redis 存储封装。
 * <p>
 * Key 格式：refresh:{userId}，Value 是 refreshToken 字符串。
 * TTL 与 Refresh Token 的 JWT 过期时间一致（默认 7 天）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String KEY_PREFIX = "refresh:";

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;

    /** 存储 Refresh Token（登录 / refresh 轮转时调用） */
    public void save(Long userId, String refreshToken) {
        String key = KEY_PREFIX + userId;
        redisTemplate.opsForValue().set(key, refreshToken,
                jwtProperties.getRefreshExpiration(), TimeUnit.SECONDS);
        log.debug("Refresh Token 已存储: userId={}, ttl={}s", userId, jwtProperties.getRefreshExpiration());
    }

    /**
     * 验证 Refresh Token 是否与 Redis 中存储的一致。
     *
     * @return true = 匹配且未过期；false = Redis 中不存在或值不匹配（被吊销 / 已轮转）
     */
    public boolean verify(Long userId, String refreshToken) {
        String stored = redisTemplate.opsForValue().get(KEY_PREFIX + userId);
        return stored != null && stored.equals(refreshToken);
    }

    /** 删除某用户的 Refresh Token（logout / 改密码时调用） */
    public void delete(Long userId) {
        Boolean removed = redisTemplate.delete(KEY_PREFIX + userId);
        if (Boolean.TRUE.equals(removed)) {
            log.debug("Refresh Token 已删除: userId={}", userId);
        }
    }
}
