package com.workbench.auth.mapper;

import com.workbench.auth.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class UserMapperTest {

    @Autowired
    private UserMapper userMapper;

    @Test
    void selectById_returnsSeededAdmin() {
        User user = userMapper.selectById(1L);

        assertThat(user).isNotNull();
        assertThat(user.getUsername()).isEqualTo("admin");
        assertThat(user.getPassword()).startsWith("$2a$");  // 存的是 BCrypt 哈希而非明文
        assertThat(user.getEnabled()).isTrue();
        // 多时区改造：timestamptz + Instant 往返（本测试同时验证 V3 迁移已在真实库执行成功）
        assertThat(user.getCreatedAt()).isNotNull();         // Instant = UTC 绝对时刻
        assertThat(user.getTimezone()).isEqualTo("Asia/Shanghai");  // V3 为种子用户标记的时区
    }
}
