package com.workbench.auth.controller;

import com.workbench.auth.dto.ChangePasswordRequest;
import com.workbench.auth.dto.UpdateProfileRequest;
import com.workbench.auth.dto.UserQuery;
import com.workbench.auth.dto.UserVO;
import com.workbench.auth.service.UserService;
import com.workbench.common.result.PageResult;
import com.workbench.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "用户管理", description = "用户 CRUD（需登录）")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "用户列表（分页 + 条件筛选）")
    @GetMapping
    public R<PageResult<UserVO>> list(UserQuery query) {
        return R.ok(userService.list(query));
    }

    @Operation(summary = "用户详情")
    @GetMapping("/{id}")
    public R<UserVO> getById(@Parameter(description = "用户 ID") @PathVariable Long id) {
        return R.ok(userService.getById(id));
    }

    @Operation(summary = "更新当前用户资料（邮箱、时区）")
    @PutMapping("/me")
    public R<UserVO> updateProfile(@AuthenticationPrincipal Long userId,
                                   @Valid @RequestBody UpdateProfileRequest req) {
        return R.ok(userService.updateProfile(userId, req));
    }

    @Operation(summary = "当前用户改密码（需验旧密码）")
    @PutMapping("/me/password")
    public R<Void> changePassword(@AuthenticationPrincipal Long userId,
                                  @Valid @RequestBody ChangePasswordRequest req) {
        userService.changePassword(userId, req);
        return R.ok();
    }

    @Operation(summary = "启用 / 禁用用户")
    @PutMapping("/{id}/enabled")
    public R<Void> setEnabled(@Parameter(description = "用户 ID") @PathVariable Long id,
                              @Parameter(description = "true=启用, false=禁用") @RequestParam boolean enabled) {
        userService.setEnabled(id, enabled);
        return R.ok();
    }

    @Operation(summary = "删除用户（软删除：设为禁用，保留数据可恢复）")
    @DeleteMapping("/{id}")
    public R<Void> delete(@Parameter(description = "用户 ID") @PathVariable Long id) {
        userService.delete(id);
        return R.ok();
    }
}
