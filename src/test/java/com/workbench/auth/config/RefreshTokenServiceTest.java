package com.workbench.auth.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private JwtProperties jwtProperties;

    @InjectMocks private RefreshTokenService service;

    @Test
    void save_storesWithTTL() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(jwtProperties.getRefreshExpiration()).thenReturn(604800L);

        service.save(1L, "token-abc");

        verify(valueOps).set("refresh:1", "token-abc", 604800L, TimeUnit.SECONDS);
    }

    @Test
    void verify_match_returnsTrue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("refresh:1")).thenReturn("token-abc");

        assertThat(service.verify(1L, "token-abc")).isTrue();
    }

    @Test
    void verify_mismatch_returnsFalse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("refresh:1")).thenReturn("token-old");

        assertThat(service.verify(1L, "token-new")).isFalse();
    }

    @Test
    void verify_notFound_returnsFalse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("refresh:1")).thenReturn(null);

        assertThat(service.verify(1L, "token-abc")).isFalse();
    }

    @Test
    void delete_callsRedisDelete() {
        when(redisTemplate.delete("refresh:1")).thenReturn(true);

        service.delete(1L);

        verify(redisTemplate).delete("refresh:1");
    }
}
