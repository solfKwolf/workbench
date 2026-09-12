package com.workbench.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

@Data
@TableName("sys_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** BCrypt 哈希（60 字符），永不存明文 */
    private String password;

    private String email;

    private Boolean enabled;

    /** 绝对时刻（UTC 时间线上的点），对应数据库 timestamptz，时区只影响展示 */
    private Instant createdAt;

    private Instant updatedAt;

    /** 用户偏好时区（IANA ID，如 Asia/Shanghai），仅用于前端渲染换算 */
    private String timezone;
}
