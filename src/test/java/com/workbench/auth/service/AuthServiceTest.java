package com.workbench.auth.service;

import com.workbench.auth.config.JwtUtil;
import com.workbench.auth.config.RefreshTokenService;
import com.workbench.auth.convert.UserConvert;
import com.workbench.auth.dto.LoginRequest;
import com.workbench.auth.dto.LoginVO;
import com.workbench.auth.dto.RefreshRequest;
import com.workbench.auth.dto.RegisterRequest;
import com.workbench.auth.dto.UserVO;
import com.workbench.auth.entity.User;
import com.workbench.auth.mapper.UserMapper;
import com.workbench.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private UserConvert userConvert;
    @Mock private RefreshTokenService refreshTokenService;

    @InjectMocks private AuthService authService;

    private RegisterRequest registerReq() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("newuser");
        req.setPassword("123456");
        req.setEmail("new@test.com");
        return req;
    }

    private LoginRequest loginReq() {
        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("123456");
        return req;
    }

    // ---- register（不变） ----

    @Test
    void register_usernameExists_throws400() {
        when(userMapper.selectOne(any())).thenReturn(new User());
        assertThatThrownBy(() -> authService.register(registerReq()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(400))
                .hasMessage("用户名已存在");
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void register_success_encodesPasswordBeforeInsert() {
        when(userMapper.selectOne(any())).thenReturn(null);
        when(passwordEncoder.encode("123456")).thenReturn("$2a$10$encoded-hash");

        RegisterRequest req = registerReq();
        req.setTimezone("America/New_York");
        authService.register(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        assertThat(captor.getValue().getPassword())
                .isEqualTo("$2a$10$encoded-hash")
                .isNotEqualTo("123456");
        assertThat(captor.getValue().getTimezone()).isEqualTo("America/New_York");
    }

    @Test
    void register_blankTimezone_defaultsToUtc() {
        when(userMapper.selectOne(any())).thenReturn(null);
        when(passwordEncoder.encode("123456")).thenReturn("$2a$10$encoded-hash");

        RegisterRequest req = registerReq();
        req.setTimezone("  ");
        authService.register(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        assertThat(captor.getValue().getTimezone()).isEqualTo("UTC");
    }

    @Test
    void register_invalidTimezone_throws400() {
        when(userMapper.selectOne(any())).thenReturn(null);
        RegisterRequest req = registerReq();
        req.setTimezone("Mars/Olympus_Mons");

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(400))
                .hasMessageContaining("无效的时区标识");
        verify(userMapper, never()).insert(any(User.class));
    }

    // ---- login（更新：签发双 Token + 存 Redis） ----

    @Test
    void login_userNotFound_throws401() {
        when(userMapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> authService.login(loginReq()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(401));
    }

    @Test
    void login_passwordMismatch_throws401() {
        User user = new User();
        user.setPassword("$2a$10$stored-hash");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("123456", "$2a$10$stored-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(loginReq()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(401));
    }

    @Test
    void login_userDisabled_throws403() {
        User user = new User();
        user.setPassword("$2a$10$stored-hash");
        user.setEnabled(false);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("123456", "$2a$10$stored-hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(loginReq()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(403));
    }

    @Test
    void login_success_returnsBothTokensAndStoresRefresh() {
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        user.setPassword("$2a$10$stored-hash");
        user.setEnabled(true);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("123456", "$2a$10$stored-hash")).thenReturn(true);
        when(jwtUtil.generateToken(1L, "admin")).thenReturn("access-token");
        when(jwtUtil.signRefreshToken(1L, "admin")).thenReturn("refresh-token");
        UserVO vo = new UserVO();
        vo.setId(1L);
        vo.setUsername("admin");
        when(userConvert.toVO(user)).thenReturn(vo);

        LoginVO result = authService.login(loginReq());

        assertThat(result.getAccessToken()).isEqualTo("access-token");
        assertThat(result.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(result.getUser().getId()).isEqualTo(1L);
        verify(refreshTokenService).save(1L, "refresh-token");
    }

    // ---- refresh ----

    private Claims mockRefreshClaims(Long userId, String username) {
        return Jwts.claims().subject(String.valueOf(userId))
                .add("username", username)
                .add("type", "refresh")
                .build();
    }

    @Test
    void refresh_invalidJwt_throws401() {
        when(jwtUtil.parseRefreshToken("bad-token"))
                .thenThrow(new io.jsonwebtoken.JwtException("bad"));

        RefreshRequest req = new RefreshRequest();
        req.setRefreshToken("bad-token");

        assertThatThrownBy(() -> authService.refresh(req))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(401))
                .hasMessageContaining("Refresh Token 无效");
    }

    @Test
    void refresh_redisMismatch_throws401() {
        when(jwtUtil.parseRefreshToken("old-refresh"))
                .thenReturn(mockRefreshClaims(1L, "admin"));
        when(refreshTokenService.verify(1L, "old-refresh")).thenReturn(false);

        RefreshRequest req = new RefreshRequest();
        req.setRefreshToken("old-refresh");

        assertThatThrownBy(() -> authService.refresh(req))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(401))
                .hasMessageContaining("已被吊销");
    }

    @Test
    void refresh_userDisabled_throws401() {
        when(jwtUtil.parseRefreshToken("refresh-token"))
                .thenReturn(mockRefreshClaims(1L, "admin"));
        when(refreshTokenService.verify(1L, "refresh-token")).thenReturn(true);
        User disabled = new User();
        disabled.setEnabled(false);
        when(userMapper.selectById(1L)).thenReturn(disabled);

        RefreshRequest req = new RefreshRequest();
        req.setRefreshToken("refresh-token");

        assertThatThrownBy(() -> authService.refresh(req))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(401))
                .hasMessageContaining("账号不可用");
        verify(refreshTokenService).delete(1L);  // 清理残留
    }

    @Test
    void refresh_success_rotatesTokens() {
        when(jwtUtil.parseRefreshToken("old-refresh"))
                .thenReturn(mockRefreshClaims(1L, "admin"));
        when(refreshTokenService.verify(1L, "old-refresh")).thenReturn(true);
        User user = new User();
        user.setEnabled(true);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(jwtUtil.generateToken(1L, "admin")).thenReturn("new-access");
        when(jwtUtil.signRefreshToken(1L, "admin")).thenReturn("new-refresh");

        RefreshRequest req = new RefreshRequest();
        req.setRefreshToken("old-refresh");

        LoginVO result = authService.refresh(req);

        assertThat(result.getAccessToken()).isEqualTo("new-access");
        assertThat(result.getRefreshToken()).isEqualTo("new-refresh");
        assertThat(result.getUser()).isNull();  // refresh 不返回 user
        verify(refreshTokenService).save(1L, "new-refresh");  // 轮转
    }

    // ---- logout ----

    @Test
    void logout_deletesRefreshToken() {
        authService.logout(1L);
        verify(refreshTokenService).delete(1L);
    }
}
