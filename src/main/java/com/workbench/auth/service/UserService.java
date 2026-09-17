package com.workbench.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workbench.auth.convert.UserConvert;
import com.workbench.auth.config.RefreshTokenService;
import com.workbench.auth.dto.ChangePasswordRequest;
import com.workbench.auth.dto.UpdateProfileRequest;
import com.workbench.auth.dto.UserQuery;
import com.workbench.auth.dto.UserVO;
import com.workbench.auth.entity.User;
import com.workbench.auth.mapper.UserMapper;
import com.workbench.common.exception.BusinessException;
import com.workbench.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 用户管理（面向管理员和当前登录用户的 CRUD，与 AuthService 的认证职责分离） */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final UserConvert userConvert;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    /** 用户列表（分页 + 动态条件） */
    public PageResult<UserVO> list(UserQuery query) {
        int size = Math.min(query.getSize() == null ? 10 : query.getSize(), 100);
        int page = Math.max(query.getPage() == null ? 1 : query.getPage(), 1);

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getUsername())) {
            wrapper.like(User::getUsername, query.getUsername().trim());
        }
        if (StringUtils.hasText(query.getEmail())) {
            wrapper.like(User::getEmail, query.getEmail().trim());
        }
        if (query.getEnabled() != null) {
            wrapper.eq(User::getEnabled, query.getEnabled());
        }
        wrapper.orderByDesc(User::getId);  // 新用户在前

        IPage<User> result = userMapper.selectPage(Page.of(page, size), wrapper);
        return PageResult.of(
                result.getRecords().stream().map(userConvert::toVO).toList(),
                result.getTotal(),
                result.getCurrent(),
                result.getSize());
    }

    /** 用户详情 */
    public UserVO getById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return userConvert.toVO(user);
    }

    /** 当前用户更新自己资料（邮箱 / 时区） */
    public UserVO updateProfile(Long userId, UpdateProfileRequest req) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        // 只更新非空字段（部分更新语义）
        if (StringUtils.hasText(req.getEmail())) {
            user.setEmail(req.getEmail().trim());
        }
        if (StringUtils.hasText(req.getTimezone())) {
            // 时区校验复用 AuthService.resolveTimezone 的逻辑，这里简单抛异常
            try {
                java.time.ZoneId.of(req.getTimezone().trim());
            } catch (java.time.DateTimeException e) {
                throw new BusinessException(400, "无效的时区标识: " + req.getTimezone());
            }
            user.setTimezone(req.getTimezone().trim());
        }
        userMapper.updateById(user);
        return userConvert.toVO(user);
    }

    /** 当前用户改密码（需验旧密码，防 CSRF / 盗号后乱改） */
    public void changePassword(Long userId, ChangePasswordRequest req) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        if (!passwordEncoder.matches(req.getOldPassword(), user.getPassword())) {
            throw new BusinessException(400, "旧密码错误");
        }
        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userMapper.updateById(user);
        // 改密码后吊销所有 Refresh Token，强制所有设备重新登录
        refreshTokenService.delete(userId);
        log.info("用户修改密码成功: {}", user.getUsername());
    }

    /** 启用 / 禁用用户 */
    public void setEnabled(Long id, boolean enabled) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        user.setEnabled(enabled);
        userMapper.updateById(user);
        // 禁用时吊销 Refresh Token，防止禁用后还能 refresh
        if (!enabled) {
            refreshTokenService.delete(id);
        }
        log.info("用户 {} 启用状态变更为: {}", user.getUsername(), enabled);
    }

    /** 删除用户（软删除：设 enabled=false，保留数据可恢复） */
    public void delete(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        user.setEnabled(false);
        userMapper.updateById(user);
        log.info("用户被删除（软删除）: {}", user.getUsername());
    }
}
