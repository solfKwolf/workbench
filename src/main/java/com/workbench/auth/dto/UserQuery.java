package com.workbench.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 用户列表查询条件（所有字段可选，组合过滤） */
@Data
@Schema(description = "用户列表查询条件")
public class UserQuery {

    @Schema(description = "页码，默认 1", example = "1")
    private Integer page = 1;

    @Schema(description = "每页条数，默认 10，最大 100", example = "10")
    private Integer size = 10;

    @Schema(description = "用户名模糊匹配（LIKE %xx%）", example = "admin")
    private String username;

    @Schema(description = "邮箱模糊匹配", example = "@workbench")
    private String email;

    @Schema(description = "启用状态筛选：true=启用, false=禁用, null=全部", example = "true")
    private Boolean enabled;
}
