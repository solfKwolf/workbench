package com.workbench.auth.controller;

import com.workbench.auth.dto.LoginRequest;
import com.workbench.auth.dto.LoginVO;
import com.workbench.auth.dto.RefreshRequest;
import com.workbench.auth.dto.RegisterRequest;
import com.workbench.auth.dto.UserVO;
import com.workbench.auth.service.AuthService;
import com.workbench.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "认证", description = "注册 / 登录 / 刷新 / 登出 / 当前用户")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "注册")
    @PostMapping("/register")
    public R<Void> register(@Valid @RequestBody RegisterRequest req) {
        authService.register(req);
        return R.ok();
    }

    @Operation(summary = "登录（返回 Access Token + Refresh Token）")
    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginRequest req) {
        return R.ok(authService.login(req));
    }

    @Operation(summary = "刷新 Token（用 Refresh Token 换新 Access Token，Refresh Token 轮转）")
    @PostMapping("/refresh")
    public R<LoginVO> refresh(@Valid @RequestBody RefreshRequest req) {
        return R.ok(authService.refresh(req));
    }

    @Operation(summary = "退出登录（删除 Refresh Token，Access Token 自然过期）")
    @PostMapping("/logout")
    public R<Void> logout(@AuthenticationPrincipal Long userId) {
        authService.logout(userId);
        return R.ok();
    }

    @Operation(summary = "当前登录用户信息（需 Bearer Access Token）")
    @GetMapping("/me")
    public R<UserVO> me(@AuthenticationPrincipal Long userId) {
        return R.ok(authService.me(userId));
    }
}
