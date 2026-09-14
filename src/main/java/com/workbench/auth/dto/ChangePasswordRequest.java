package com.workbench.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 当前用户改密码（需验旧密码） */
@Data
@Schema(description = "修改密码请求")
public class ChangePasswordRequest {

    @Schema(description = "当前密码（用于二次校验）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "旧密码不能为空")
    private String oldPassword;

    @Schema(description = "新密码", requiredMode = Schema.RequiredMode.REQUIRED, example = "newpass123")
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 32, message = "新密码长度须为6-32位")
    private String newPassword;
}
