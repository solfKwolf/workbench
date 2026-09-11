package com.workbench.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

@Data
@ToString(exclude = "password")  // AOP 打印入参时密码脱敏
@Schema(description = "注册请求")
public class RegisterRequest {

    @Schema(description = "用户名（4-20位字母数字下划线）", example = "zhangsan")
    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_]{4,20}$", message = "用户名须为4-20位字母数字下划线")
    private String username;

    @Schema(description = "密码（6-32位）", example = "123456")
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度须为6-32位")
    private String password;

    @Schema(description = "邮箱（可选）", example = "zhangsan@test.com")
    @Email(message = "邮箱格式不正确")
    private String email;
}
