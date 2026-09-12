package com.workbench.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.auth.config.JwtUtil;
import com.workbench.auth.convert.UserConvert;
import com.workbench.auth.dto.LoginRequest;
import com.workbench.auth.dto.LoginVO;
import com.workbench.auth.dto.RegisterRequest;
import com.workbench.auth.dto.UserVO;
import com.workbench.auth.entity.User;
import com.workbench.auth.mapper.UserMapper;
import com.workbench.common.exception.BusinessException;
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

    /** 时区解析：空白 -> UTC；非法 IANA ID -> 400。ZoneId.of 校验的是规则本身（含夏令时），比正则可靠 */
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

    /** 登录：查用户 -> matches 比对 -> 签发 token */
    public LoginVO login(LoginRequest req) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, req.getUsername()));
        // 用户不存在与密码错误统一提示，防止撞库探测
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return new LoginVO(token, userConvert.toVO(user));
    }

    /** 当前登录用户（userId 来自 JWT 过滤器写入的 SecurityContext） */
    public UserVO me(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return userConvert.toVO(user);
    }
}
