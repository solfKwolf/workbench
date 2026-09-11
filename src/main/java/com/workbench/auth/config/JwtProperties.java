package com.workbench.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** application-*.yml 中 jwt.* 前缀配置的绑定对象 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** HS256 密钥（>= 32 字节） */
    private String secret;

    /** token 有效期（秒） */
    private Long expiration;

    /** 携带 token 的请求头 */
    private String header;

    /** token 前缀 */
    private String prefix;
}
