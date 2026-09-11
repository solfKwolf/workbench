package com.workbench.auth.controller;

import com.workbench.auth.dto.LoginRequest;
import com.workbench.auth.dto.LoginVO;
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

@Tag(name = "认证", description = "注册 / 登录 / 当前用户")
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

    @Operation(summary = "登录（返回 JWT token）")
    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginRequest req) {
        return R.ok(authService.login(req));
    }

    @Operation(summary = "当前登录用户信息（需 Bearer token）")
    @GetMapping("/me")
    public R<UserVO> me(@AuthenticationPrincipal Long userId) {
        return R.ok(authService.me(userId));
    }
}
