package com.workbench.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 对外用户信息——不含 password（安全边界靠类型约束，而非靠删除字段的自觉） */
@Data
@Schema(description = "用户信息（不含密码）")
public class UserVO {

    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "注册时间")
    private LocalDateTime createdAt;
}
