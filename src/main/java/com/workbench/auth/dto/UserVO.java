package com.workbench.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.ToString;

import java.time.Instant;

/** 对外用户信息——不含 password（安全边界靠类型约束，而非靠删除字段的自觉） */
@Data
@ToString(exclude = "email")  // AOP 打印出参时邮箱脱敏
@Schema(description = "用户信息（不含密码）")
public class UserVO {

    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "注册时间（UTC 绝对时刻，ISO-8601 带 Z 后缀，前端按用户时区渲染）", example = "2026-09-12T04:00:00Z")
    private Instant createdAt;

    @Schema(description = "用户偏好时区（IANA ID），前端渲染时间的依据", example = "Asia/Shanghai")
    private String timezone;
}
