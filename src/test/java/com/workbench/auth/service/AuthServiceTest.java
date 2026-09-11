package com.workbench.auth.service;

import com.workbench.auth.config.JwtUtil;
import com.workbench.auth.convert.UserConvert;
import com.workbench.auth.dto.LoginRequest;
import com.workbench.auth.dto.LoginVO;
import com.workbench.auth.dto.RegisterRequest;
import com.workbench.auth.dto.UserVO;
import com.workbench.auth.entity.User;
import com.workbench.auth.mapper.UserMapper;
import com.workbench.common.exception.BusinessException;
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

        authService.register(registerReq());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        assertThat(captor.getValue().getPassword())
                .isEqualTo("$2a$10$encoded-hash")      // 入库的是哈希
                .isNotEqualTo("123456");               // 而非明文
    }

    @Test
    void login_userNotFound_throws401() {
        when(userMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> authService.login(loginReq()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(401))
                .hasMessage("用户名或密码错误");
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
    void login_success_returnsTokenAndVO() {
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        user.setPassword("$2a$10$stored-hash");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("123456", "$2a$10$stored-hash")).thenReturn(true);
        when(jwtUtil.generateToken(1L, "admin")).thenReturn("mock-token");
        UserVO vo = new UserVO();
        vo.setId(1L);
        vo.setUsername("admin");
        when(userConvert.toVO(user)).thenReturn(vo);

        LoginVO result = authService.login(loginReq());

        assertThat(result.getToken()).isEqualTo("mock-token");
        assertThat(result.getUser().getId()).isEqualTo(1L);
        assertThat(result.getUser().getUsername()).isEqualTo("admin");
    }
}
