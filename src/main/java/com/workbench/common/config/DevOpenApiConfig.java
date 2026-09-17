package com.workbench.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Dev 环境 OpenAPI 增强：自动给受保护接口注入 x-mock-user-id header。
 * Knife4j 调试页面会直接显示这个 header 参数，填值即可绕过 JWT 鉴权。
 *
 * <p>prod 环境不加载（@Profile("dev")），不影响线上 OpenAPI specs。
 */
@Configuration
@Profile("dev")
public class DevOpenApiConfig {

    private static final Parameter MOCK_USER_HEADER = new Parameter()
            .name("x-mock-user-id")
            .in("header")
            .description("Dev Mock 登录：指定用户 ID 跳过 JWT 鉴权")
            .required(false)
            .example("1");

    @Bean
    public GlobalOpenApiCustomizer mockUserHeaderInjector() {
        return (OpenAPI openApi) -> {
            openApi.getPaths().forEach((path, item) -> {
                item.readOperations().forEach(op -> {
                    // 公开接口不加——它们本身就不需要认证
                    if (path.contains("/auth/login")
                            || path.contains("/auth/register")
                            || path.contains("/auth/refresh")) {
                        return;
                    }
                    // 已存在同名 parameter 不重复加
                    boolean alreadyHas = op.getParameters() != null
                            && op.getParameters().stream().anyMatch(p -> "x-mock-user-id".equals(p.getName()));
                    if (!alreadyHas) {
                        op.addParametersItem(MOCK_USER_HEADER);
                    }
                });
            });
        };
    }
}
