package com.workbench.auth.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workbench.auth.config.RefreshTokenService;
import com.workbench.auth.convert.UserConvert;
import com.workbench.auth.dto.ChangePasswordRequest;
import com.workbench.auth.dto.UpdateProfileRequest;
import com.workbench.auth.dto.UserQuery;
import com.workbench.auth.dto.UserVO;
import com.workbench.auth.entity.User;
import com.workbench.auth.mapper.UserMapper;
import com.workbench.common.exception.BusinessException;
import com.workbench.common.result.PageResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserMapper userMapper;
    @Mock private UserConvert userConvert;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RefreshTokenService refreshTokenService;

    @InjectMocks private UserService userService;

    private User user(Long id, String username) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setPassword("$2a$10$stored-hash");
        u.setEmail(username + "@test.com");
        u.setEnabled(true);
        u.setCreatedAt(Instant.now());
        u.setUpdatedAt(Instant.now());
        u.setTimezone("UTC");
        return u;
    }

    private UserVO vo(Long id, String username) {
        UserVO v = new UserVO();
        v.setId(id);
        v.setUsername(username);
        return v;
    }

    // ---- list ----

    @Test
    void list_defaults_page1_size10() {
        UserQuery q = new UserQuery();
        Page<User> fakePage = new Page<>(1, 10);
        fakePage.setRecords(List.of(user(1L, "admin")));
        fakePage.setTotal(1);
        when(userMapper.selectPage(any(Page.class), any())).thenReturn(fakePage);
        when(userConvert.toVO(any(User.class))).thenReturn(vo(1L, "admin"));

        PageResult<UserVO> result = userService.list(q);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(10);
        assertThat(result.getPages()).isEqualTo(1);
    }

    @Test
    void list_sizeClampedTo100() {
        UserQuery q = new UserQuery();
        q.setSize(500);   // 超限
        Page<User> fakePage = new Page<>(1, 100);
        fakePage.setRecords(List.of());
        fakePage.setTotal(0);
        when(userMapper.selectPage(any(Page.class), any())).thenReturn(fakePage);

        userService.list(q);

        ArgumentCaptor<Page> captor = ArgumentCaptor.forClass(Page.class);
        verify(userMapper).selectPage(captor.capture(), any());
        assertThat(captor.getValue().getSize()).isEqualTo(100);
    }

    // ---- getById ----

    @Test
    void getById_notFound_throws404() {
        when(userMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> userService.getById(99L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(404))
                .hasMessage("用户不存在");
    }

    @Test
    void getById_success_returnsVO() {
        User u = user(1L, "admin");
        when(userMapper.selectById(1L)).thenReturn(u);
        when(userConvert.toVO(u)).thenReturn(vo(1L, "admin"));

        UserVO result = userService.getById(1L);

        assertThat(result.getUsername()).isEqualTo("admin");
    }

    // ---- updateProfile ----

    @Test
    void updateProfile_notFound_throws404() {
        when(userMapper.selectById(99L)).thenReturn(null);
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setEmail("x@y.com");

        assertThatThrownBy(() -> userService.updateProfile(99L, req))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(404));
    }

    @Test
    void updateProfile_partialUpdate_onlyFieldsWithValueChange() {
        User u = user(1L, "admin");
        when(userMapper.selectById(1L)).thenReturn(u);
        when(userConvert.toVO(u)).thenReturn(vo(1L, "admin"));

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setTimezone("Asia/Shanghai");   // 只改 timezone，email 为 null

        userService.updateProfile(1L, req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        assertThat(captor.getValue().getTimezone()).isEqualTo("Asia/Shanghai");
        assertThat(captor.getValue().getEmail()).isEqualTo("admin@test.com");  // 未变
    }

    @Test
    void updateProfile_invalidTimezone_throws400() {
        User u = user(1L, "admin");
        when(userMapper.selectById(1L)).thenReturn(u);

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setTimezone("Mars/Base");

        assertThatThrownBy(() -> userService.updateProfile(1L, req))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(400))
                .hasMessageContaining("无效的时区");
        verify(userMapper, never()).updateById(any(User.class));
    }

    // ---- changePassword ----

    @Test
    void changePassword_oldPasswordMismatch_throws400() {
        User u = user(1L, "admin");
        when(userMapper.selectById(1L)).thenReturn(u);
        when(passwordEncoder.matches("wrong-old", u.getPassword())).thenReturn(false);

        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setOldPassword("wrong-old");
        req.setNewPassword("newpass123");

        assertThatThrownBy(() -> userService.changePassword(1L, req))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(400))
                .hasMessage("旧密码错误");
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    void changePassword_success_encodesNewPassword() {
        User u = user(1L, "admin");
        when(userMapper.selectById(1L)).thenReturn(u);
        when(passwordEncoder.matches("old-pass", u.getPassword())).thenReturn(true);
        when(passwordEncoder.encode("newpass123")).thenReturn("$2a$10$new-hash");

        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setOldPassword("old-pass");
        req.setNewPassword("newpass123");

        userService.changePassword(1L, req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("$2a$10$new-hash");
    }

    // ---- setEnabled / delete ----

    @Test
    void setEnabled_notFound_throws404() {
        when(userMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> userService.setEnabled(99L, false))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(404));
    }

    @Test
    void delete_softSetsEnabledFalse() {
        User u = user(1L, "admin");
        when(userMapper.selectById(1L)).thenReturn(u);

        userService.delete(1L);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        assertThat(captor.getValue().getEnabled()).isFalse();
    }
}
