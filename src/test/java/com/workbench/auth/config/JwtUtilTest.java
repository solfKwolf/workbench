package com.workbench.auth.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret("workbench-dev-jwt-secret-key-must-be-at-least-32-bytes-long!");
        props.setExpiration(7200L);
        jwtUtil = new JwtUtil(props);
        jwtUtil.init();
    }

    @Test
    void generateAndParse_roundTrip() {
        String token = jwtUtil.generateToken(1L, "zhangsan");

        Claims claims = jwtUtil.parseToken(token);

        assertThat(jwtUtil.getUserId(claims)).isEqualTo(1L);
        assertThat(claims.get("username", String.class)).isEqualTo("zhangsan");
    }

    @Test
    void parseToken_expired_throwsExpiredJwtException() {
        String token = jwtUtil.generateToken(1L, "zhangsan", -10L);  // 已过期 10 秒

        assertThatThrownBy(() -> jwtUtil.parseToken(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void parseToken_tampered_throwsJwtException() {
        String token = jwtUtil.generateToken(1L, "zhangsan");
        // 篡改 payload 段最后一个字符（签名必然校验失败）
        char[] chars = token.toCharArray();
        int i = token.lastIndexOf('.') - 1;
        chars[i] = chars[i] == 'A' ? 'B' : 'A';

        assertThatThrownBy(() -> jwtUtil.parseToken(new String(chars)))
                .isInstanceOf(JwtException.class);
    }
}
