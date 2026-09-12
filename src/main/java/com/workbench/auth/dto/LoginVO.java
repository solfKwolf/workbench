package com.workbench.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

@Data
@AllArgsConstructor
@ToString(exclude = "token")  // AOP 打印出参时 token 脱敏（凭证不可落日志）
@Schema(description = "登录响应")
public class LoginVO {

    @Schema(description = "JWT token，后续请求放 Authorization: Bearer <token>")
    private String token;

    @Schema(description = "用户信息")
    private UserVO user;
}
