package com.workbench.auth.config;

import com.workbench.auth.entity.User;
import com.workbench.auth.mapper.UserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * Dev 环境快捷登录：请求头带 x-mock-user-id 即可绕过 JWT，直接以该用户身份访问。
 * 仅 dev profile 加载，prod 打包时完全不存在。
 *
 * <p>用例：Knife4j 全局参数加 x-mock-user-id: 1，所有调试请求自动以 admin 身份通过鉴权。
 *
 * <p>Filter 顺序：插在 JwtAuthenticationFilter 之后。
 * 如果 JWT 已经塞了 Authentication（正常登录），本 Filter 跳过；
 * 如果 JWT 没塞（无 token），但带了 x-mock-user-id，就手动构造 Authentication。
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevMockUserFilter extends OncePerRequestFilter {

    public static final String MOCK_HEADER = "x-mock-user-id";

    private final UserMapper userMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        // 已有认证（正常 JWT 登录）或已经 mock 过了——跳过
        if (SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        String mockId = request.getHeader(MOCK_HEADER);
        if (mockId != null && !mockId.isBlank()) {
            try {
                User user = userMapper.selectById(Long.valueOf(mockId.trim()));
                if (user != null) {
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            user.getId(),       // principal（供 @AuthenticationPrincipal 取用）
                            user.getUsername(), // credentials
                            Collections.emptyList());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    log.debug("[DevMockUser] mock 登录成功 → userId={}, username={}", user.getId(), user.getUsername());
                } else {
                    log.warn("[DevMockUser] x-mock-user-id={} 对应的用户不存在", mockId);
                }
            } catch (NumberFormatException e) {
                log.warn("[DevMockUser] x-mock-user-id 格式错误：{}", mockId);
            }
        }

        filterChain.doFilter(request, response);
    }
}
