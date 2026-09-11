package com.workbench.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "登录响应")
public class LoginVO {

    @Schema(description = "JWT token，后续请求放 Authorization: Bearer <token>")
    private String token;

    @Schema(description = "用户信息")
    private UserVO user;
}
