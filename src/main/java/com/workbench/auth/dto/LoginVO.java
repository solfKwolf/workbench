package com.workbench.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"accessToken", "refreshToken"})  // 两个 token 都不落日志
@Schema(description = "登录 / 刷新响应（双 Token）")
public class LoginVO {

    @Schema(description = "Access Token（JWT，2 小时有效，放 Authorization: Bearer <token>）")
    private String accessToken;

    @Schema(description = "Refresh Token（JWT，7 天有效，仅用于换新 Access Token）")
    private String refreshToken;

    @Schema(description = "用户信息")
    private UserVO user;

    /** 仅返回 token 时用（refresh 接口不返回 user） */
    public static LoginVO tokensOnly(String accessToken, String refreshToken) {
        LoginVO vo = new LoginVO();
        vo.setAccessToken(accessToken);
        vo.setRefreshToken(refreshToken);
        return vo;
    }
}
