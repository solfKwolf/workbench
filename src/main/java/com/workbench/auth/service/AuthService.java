package com.workbench.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.auth.config.JwtUtil;
import com.workbench.auth.config.RefreshTokenService;
import com.workbench.auth.convert.UserConvert;
import com.workbench.auth.dto.LoginRequest;
import com.workbench.auth.dto.LoginVO;
import com.workbench.auth.dto.RefreshRequest;
import com.workbench.auth.dto.RegisterRequest;
import com.workbench.auth.dto.UserVO;
import com.workbench.auth.entity.User;
import com.workbench.auth.mapper.UserMapper;
import com.workbench.common.exception.BusinessException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final UserConvert userConvert;
    private final RefreshTokenService refreshTokenService;

    /** 注册：用户名查重 -> BCrypt 加密 -> 入库 */
    public void register(RegisterRequest req) {
        User existing = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, req.getUsername()));
        if (existing != null) {
            throw new BusinessException(400, "用户名已存在");
        }
        User user = new User();
        user.setUsername(req.getUsername());
        user.setEmail(req.getEmail());
        user.setPassword(passwordEncoder.encode(req.getPassword()));  // 随机盐哈希
        user.setTimezone(resolveTimezone(req.getTimezone()));
        userMapper.insert(user);
        log.info("用户注册成功: {}", req.getUsername());
    }

    /** 时区解析：空白 -> UTC；非法 IANA ID -> 400 */
    private String resolveTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return "UTC";
        }
        try {
            return ZoneId.of(timezone.trim()).getId();
        } catch (DateTimeException e) {
            throw new BusinessException(400, "无效的时区标识: " + timezone);
        }
    }

    /** 登录：验证 -> 签发 Access + Refresh Token -> Refresh 存 Redis */
    public LoginVO login(LoginRequest req) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, req.getUsername()));
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new BusinessException(403, "账号已被禁用，请联系管理员");
        }
        String accessToken = jwtUtil.generateToken(user.getId(), user.getUsername());
        String refreshToken = jwtUtil.signRefreshToken(user.getId(), user.getUsername());
        refreshTokenService.save(user.getId(), refreshToken);
        log.info("用户登录成功: {}", user.getUsername());
        return new LoginVO(accessToken, refreshToken, userConvert.toVO(user));
    }

    /** 用 Refresh Token 换新 Access Token（Refresh Token 轮转） */
    public LoginVO refresh(RefreshRequest req) {
        // 1. 验签 + 解析 Refresh Token 并校验 type=refresh
        Long userId;
        String username;
        try {
            var claims = jwtUtil.parseRefreshToken(req.getRefreshToken());
            userId = Long.valueOf(claims.getSubject());
            username = claims.get("username", String.class);
        } catch (JwtException e) {
            throw new BusinessException(401, "Refresh Token 无效或已过期");
        }

        // 2. 查 Redis：Refresh Token 是否还在、值是否匹配
        if (!refreshTokenService.verify(userId, req.getRefreshToken())) {
            throw new BusinessException(401, "Refresh Token 无效或已被吊销，请重新登录");
        }

        // 3. 用户可能已被禁用
        User user = userMapper.selectById(userId);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            refreshTokenService.delete(userId);  // 清理残留
            throw new BusinessException(401, "账号不可用");
        }

        // 4. 轮转：签新 Access + 新 Refresh，删旧 Refresh，存新 Refresh
        String newAccess = jwtUtil.generateToken(userId, username);
        String newRefresh = jwtUtil.signRefreshToken(userId, username);
        refreshTokenService.save(userId, newRefresh);  // save 覆盖旧值 + 重置 TTL
        return LoginVO.tokensOnly(newAccess, newRefresh);
    }

    /** 退出登录：删 Redis 里的 Refresh Token */
    public void logout(Long userId) {
        refreshTokenService.delete(userId);
        log.info("用户退出登录: userId={}", userId);
    }

    /** 当前登录用户 */
    public UserVO me(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return userConvert.toVO(user);
    }

    /** 改密码联动：删 Redis 里的 Refresh Token，强制所有设备重新登录 */
    public void revokeAllSessions(Long userId) {
        refreshTokenService.delete(userId);
        log.info("用户所有会话已撤销: userId={}", userId);
    }
}
