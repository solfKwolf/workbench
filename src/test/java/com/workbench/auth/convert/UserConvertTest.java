package com.workbench.auth.convert;

import com.workbench.auth.dto.UserVO;
import com.workbench.auth.entity.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UserConvertTest {

    private final UserConvert userConvert = new UserConvertImpl();

    @Test
    void toVO_mapsSameNameFields() {
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        user.setPassword("$2a$10$secret-hash");
        user.setEmail("admin@workbench.com");
        user.setEnabled(true);
        user.setCreatedAt(Instant.parse("2026-09-11T02:00:00Z"));
        user.setTimezone("Asia/Shanghai");

        UserVO vo = userConvert.toVO(user);

        assertThat(vo.getId()).isEqualTo(1L);
        assertThat(vo.getUsername()).isEqualTo("admin");
        assertThat(vo.getEmail()).isEqualTo("admin@workbench.com");
        assertThat(vo.getCreatedAt()).isEqualTo(Instant.parse("2026-09-11T02:00:00Z"));
        assertThat(vo.getTimezone()).isEqualTo("Asia/Shanghai");
        // UserVO 结构上没有 password 字段——从类型层面杜绝密码泄露
    }
}
