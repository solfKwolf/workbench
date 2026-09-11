package com.workbench.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 生成种子用户密码的 BCrypt 哈希并打印。
 * 运行: mvn test -Dtest=BCryptHashGeneratorTest
 * 输出行 BCRYPT_HASH=... 即 V2__seed_admin.sql 所需哈希。
 * BCrypt 每次加密掺入随机盐——输出的哈希每次不同，但都能通过 matches("123456", hash) 校验。
 */
class BCryptHashGeneratorTest {

    @Test
    void generateAndPrintBcryptHash() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = encoder.encode("123456");
        System.out.println("BCRYPT_HASH=" + hash);
        assertThat(hash).startsWith("$2a$10$").hasSize(60);
        assertThat(encoder.matches("123456", hash)).isTrue();
    }
}
