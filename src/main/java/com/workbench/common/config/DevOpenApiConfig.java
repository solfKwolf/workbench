package com.workbench.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Dev 环境 OpenAPI 增强：自动注入 x-mock-user-id 全局 header。
 * 打开 Knife4j 页面 → 全局参数里直接有，每个调试请求自动带，不用手动加。
 *
 * <p>prod 环境不加载（@Profile("dev")），不影响线上 OpenAPI specs。
 */
@Configuration
@Profile("dev")
public class DevOpenApiConfig {

    @Bean
    public GlobalOpenApiCustomizer mockUserHeaderInjector() {
        return (OpenAPI openApi) -> {
            // 在 components/parameters 里定义可复用的 parameter
            Parameter mockUserIdHeader = new Parameter()
                    .name("x-mock-user-id")
                    .in("header")
                    .description("Dev Mock 登录：指定用户 ID 跳过 JWT")
                    .required(false)
                    .example("1");

            openApi.getComponents()
                    .addParameters("mockUserIdHeader", mockUserIdHeader);

            // 给所有受保护的接口（除 login/register/refresh）自动引用这个 header
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
                        op.addParametersItem(new Parameter()
                                .$ref("#/components/parameters/mockUserIdHeader"));
                    }
                });
            });
        };
    }
}
