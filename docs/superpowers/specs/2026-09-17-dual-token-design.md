# 双 Token + Redis 设计文档

**日期**: 2026-09-17
**状态**: 已批准
**前置**: 现有 JWT 单 Token 方案，用户认证 + 用户 CRUD 已完成

## 1. 为什么做

当前单 JWT 无状态，发出去收不回来：

- 用户改密码后旧 token 还能用 2 小时
- 退出登录无法真正吊销 token
- 无法实现"让某台设备下线"

双 Token 把"短期凭证"和"长期凭证"拆开，Refresh Token 存 Redis（有状态可吊销），解决以上痛点。

## 2. 架构总览

```
┌─────────────────────────────────────────────────┐
│                   前端                          │
│  Access Token (内存/短时 cookie, 2h)            │
│  Refresh Token (localStorage, 7d)               │
└──────────────┬──────────────────┬───────────────┘
               │ 1. 调业务接口     │ 3. Access 过期
               │    带 Access      │    用 Refresh 换新
               ▼                  ▼
┌─────────────────────────────────────────────────┐
│              Spring Boot 后端                   │
│  JwtAuthenticationFilter (验 Access JWT)        │
│  AuthController.login / refresh / logout        │
│  AuthService (业务逻辑)                         │
│  RefreshTokenService (Redis 操作封装)            │
└─────────────────────────────────────────────────┘
               │ 2. 查/存 Refresh Token
               ▼
┌─────────────────────────────────────────────────┐
│         Upstash Redis (免费)                    │
│  Key: refresh:<userId>                          │
│  Val: <refreshToken 字符串>                      │
│  TTL: 7天 (604800 秒)                            │
└─────────────────────────────────────────────────┘
```

## 3. Token 规格

| | Access Token | Refresh Token |
|---|---|---|
| 格式 | JWT (HS384) | JWT (HS384) |
| 有效期 | 2 小时 (7200s) | 7 天 (604800s) |
| 存 Redis | ❌ | ✅ |
| 用途 | 调业务接口 | 换 Access / 换自己 |
| 轮转 | 不轮转 | 每次 refresh 换新的 |

Refresh Token 也用 JWT 格式（和 Access 对称，方便验签），但**值存 Redis**。

## 4. 接口变更

### 4.1 login 接口

**改前**: 返回 `R<LoginVO>`（含 token + user）

**改后**: 返回 `R<LoginVO>`，LoginVO 新增 `refreshToken` 字段

```json
{
  "code": 200,
  "data": {
    "accessToken": "eyJhbGciOiJIUzM4NCJ9...",
    "refreshToken": "eyJhbGciOiJIUzM4NCJ9...",
    "user": { ... }
  }
}
```

后端同时：
- 签 Access Token（2h）
- 签 Refresh Token（7d）
- `Redis.set("refresh:" + userId, refreshToken, TTL=7d)`

### 4.2 新增 refresh 接口

**`POST /api/auth/refresh`** — 用 Refresh Token 换新 Access Token

请求体：`{ "refreshToken": "eyJ..." }`

后端逻辑：
1. 验签 + 解析 Refresh Token（JwtUtil.parseRefreshToken）
2. 查 Redis：`GET refresh:<userId>`
3. Redis 里存的值 和 传进来的 token 是否一致？
   - 一致 → 验证通过
   - 不一致 → 说明是旧 Refresh Token（已被轮转替换），返回 401 让用户重登
4. **轮转**：签新 Access Token + 新 Refresh Token，删旧 Refresh Token，存新 Refresh Token 到 Redis
5. 返回 `{ accessToken, refreshToken }`

### 4.3 新增 logout 接口

**`POST /api/auth/logout`** — 退出登录

鉴权：需要 Access Token（`@AuthenticationPrincipal Long userId`）

后端逻辑：
1. `Redis.del("refresh:" + userId)` — 删 Refresh Token
2. 返回 200

### 4.4 改密码时的联动

AuthService.changePassword 和 UserService.changePassword：成功后额外执行 `Redis.del("refresh:" + userId)`，强制所有设备重新登录。

### 4.5 register 接口

不变。注册成功不自动登录（符合 REST 惯例），用户需手动 login。

### 4.6 /api/auth/me 接口

不变，继续验 Access Token。

### 4.7 JwtAuthenticationFilter 不变

继续只验 Access Token 的 JWT 签名。**不查 Redis**——Access Token 是无状态的，验证快。

## 5. Redis 配置

### 5.1 pom.xml 加依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

Spring Boot 自动装配 StringRedisTemplate。

### 5.2 application-*.yml

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      database: 0
      # Upstash 强制 TLS，本地不用
      ssl: ${REDIS_SSL:false}
      # 连接池（Upstash 免费版只允许少量连接）
      lettuce:
        pool:
          max-active: 8
          max-idle: 4
          min-idle: 1
```

dev 默认本地 localhost:6379（你可以本地装个 Redis，或者也用 Upstash）。prod/CI 用环境变量覆盖。

### 5.3 CI workflow 加 Redis service

```yaml
services:
  postgres: ...
  redis:
    image: redis:7
    ports:
      - 6379:6379
```

## 6. 新增文件

| 文件 | 职责 |
|---|---|
| `src/main/java/.../auth/config/RefreshTokenService.java` | Refresh Token 的 Redis 操作封装（set / get / del / 校验） |
| `src/main/java/.../auth/dto/RefreshRequest.java` | refresh 接口请求体（只有 refreshToken 字段） |
| `src/test/java/.../auth/config/RefreshTokenServiceTest.java` | 单元测试（mock StringRedisTemplate） |

## 7. 修改文件

| 文件 | 改动 |
|---|---|
| `pom.xml` | 加 spring-boot-starter-data-redis |
| `application.yml` | 加 redis 配置占位符 |
| `application-dev.yml` | 加 redis 配置（默认本地） |
| `application-prod.yml` | 加 redis 配置（环境变量） |
| `application-ci.yml`（新增） | CI 专用 redis 配置 |
| `JwtProperties.java` | 加 refreshExpiration 字段 |
| `JwtUtil.java` | 加 signRefreshToken / parseRefreshToken |
| `LoginVO.java` | 加 refreshToken 字段 |
| `AuthController.java` | 新增 refresh + logout 接口 |
| `AuthService.java` | login 存 Refresh Token；新增 refresh / logout 方法；changePassword 联动删 Redis |
| `UserService.java` | changePassword 联动删 Redis |
| `ci.yml` | 加 redis service + REDIS_* 环境变量 |

## 8. 错误处理

| 场景 | HTTP | 错误信息 |
|---|---|---|
| Refresh Token 过期 | 401 | Refresh Token 已过期，请重新登录 |
| Refresh Token 在 Redis 里找不到 | 401 | Refresh Token 无效或已被吊销 |
| Refresh Token 值不匹配（旧 token） | 401 | Refresh Token 已失效，请重新登录 |
| Access Token 过期 | 401（现有行为） | 未登录或 token 缺失 |
| Redis 不可用 | 500 | 服务暂时不可用（理论上 Upstash SLA 99.9%，但要兜底） |

## 9. Dev 环境 Redis

本地开发用 Docker 起 Redis 7：

```powershell
docker run -d --name workbench-redis -p 6379:6379 redis:7-alpine
```

无密码，默认端口 6379。dev profile 的 redis 配置就是 `localhost:6379`。

prod 用 Upstash（已配置好 TLS），CI 用 GitHub Actions redis service 容器。

## 10. 测试覆盖

| 测试 | 类型 | 覆盖 |
|---|---|---|
| RefreshTokenServiceTest | Mock 单元 | set / get / del / 校验匹配 |
| JwtUtilTest（扩展） | Mock 单元 | signRefreshToken / parseRefreshToken |
| AuthServiceTest（扩展） | Mock 单元 | login 存 Redis / refresh 轮转 / logout 删 Redis / changePassword 联动删 |
| 现有 32 个测试 | 全量回归 | 确保不破坏 |
| E2E | 手动 | login → access 调接口 → 等 access 过期 → refresh 换新 → logout → refresh 失败 → 改密码 → 旧 refresh 失效 |

## 11. 安全注意

1. **Refresh Token 只存 Redis，不落库**——和用户密码解耦
2. **Refresh Token 轮转**——每次换新，旧的立即删除，防止重放
3. **Redis key 用 `refresh:<userId>`**——可预见格式，方便排障
4. **不做 Refresh Token 黑名单**——轮转已经够了，黑名单是重复建设
5. **dev 环境可以不装 Redis**——RefreshTokenService 里 catch 连接异常降级
