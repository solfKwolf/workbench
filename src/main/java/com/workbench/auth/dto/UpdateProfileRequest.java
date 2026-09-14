package com.workbench.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 当前用户更新自己资料（邮箱、时区） */
@Data
@Schema(description = "更新个人资料请求")
public class UpdateProfileRequest {

    @Schema(description = "邮箱（可选，传了才校验格式）", example = "new@workbench.com")
    @Email(message = "邮箱格式不正确")
    private String email;

    @Schema(description = "用户时区（IANA ID，可选）", example = "America/New_York")
    @Size(max = 64, message = "时区标识过长")
    private String timezone;
}
