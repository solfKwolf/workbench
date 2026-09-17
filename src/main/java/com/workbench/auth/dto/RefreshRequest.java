package com.workbench.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 用 Refresh Token 换新 Access Token */
@Data
@Schema(description = "刷新 Token 请求")
public class RefreshRequest {

    @Schema(description = "Refresh Token", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Refresh Token 不能为空")
    private String refreshToken;
}
