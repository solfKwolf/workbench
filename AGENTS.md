# AGENTS.md — AI 编码协作约定

> 本文件给 AI 编码助手看。README.md 给人看。

## 技术栈

- **Spring Boot 3.5.13 + Java 17**（pom.xml parent 锁定）
- **PostgreSQL 16** + **Redis 7**（dev 本地 Docker，prod Upstash TLS）
- **MyBatis-Plus 3.5.17** + **Flyway** 管理 schema
- **Springdoc OpenAPI 2.8.17 + Knife4j 4.5.0**
- **JJWT 0.12.6** 双 Token：Access (2h, 无状态) + Refresh (7d, Redis 可吊销)
- **MapStruct 1.6.3 + Lombok**（annotation processor 顺序：lombok → lombok-mapstruct-binding → mapstruct-processor，**不能乱**）

## 项目结构

```
com.workbench
├── auth/           # 认证 + 用户模块（目前全部业务）
│   ├── config/     # JwtUtil, JwtProperties, JwtAuthenticationFilter, SecurityConfig, RefreshTokenService
│   ├── controller/ # AuthController, UserController
│   ├── service/    # AuthService, UserService
│   ├── mapper/     # MyBatis-Plus BaseMapper
│   ├── entity/     # User（@TableName, @TableLogic）
│   ├── dto/        # 请求/响应 VO
│   └── convert/    # MapStruct 转换器
└── common/         # 通用
    ├── result/     # R<T> 统一响应 + PageResult<T>
    ├── exception/  # BusinessException + GlobalExceptionHandler
    ├── config/     # MybatisPlusConfig（分页插件）, OpenApiConfig
    └── aspect/     # RequestLogAspect（AOP 请求日志）
```

## 命令速查

| 操作 | 命令 |
|---|---|
| 本地启动 | `mvn spring-boot:run`（自动激活 dev profile） |
| 跑测试 | `mvn clean test` |
| 完整打包 | `mvn clean package -DskipTests` |
| 推代码 | `git push`（**不要**设 git http.proxy，直连 GitHub 更稳） |

## 本地依赖

Dev 环境需要两个 Docker 容器：

```bash
docker run -d --name workbench-pg -e POSTGRES_PASSWORD=123456 -e POSTGRES_DB=workbench -p 5432:5432 postgres:16-alpine
docker run -d --name workbench-redis -p 6379:6379 redis:7-alpine
```

数据库 schema 由 Flyway 自动迁移，无需手动建表。

## 必须遵守的约定

### DTO / VO
- **凭证和 PII 字段必须 `@ToString(exclude = {...})`**：`password`, `accessToken`, `refreshToken`, `email` 不能进日志（RequestLogAspect 会打 toString）
- LoginVO 字段名是 `accessToken` / `refreshToken`，不是 `token`
- RefreshRequest 只有一个 `refreshToken` 字段

### 双 Token 安全模型
- **Access Token**：纯 JWT（HS256），无 Redis，过期即失效，**不能被吊销**
- **Refresh Token**：JWT + Redis 存储（key = `refresh:{userId}`，TTL = refresh-expiration），**可吊销**
- 签发 Refresh Token 时必须加 `type=refresh` claim；JwtUtil.parseRefreshToken 会校验这个 claim
- **Refresh 轮转**：每次 refresh 签新 token，Redis save 覆盖旧值（TTL 重置），旧 Refresh 立即失效防重放
- **三个吊销触发点**：logout、changePassword、setEnabled(false) 都要调 `refreshTokenService.delete(userId)`

### Spring Boot 3.5 Redis 配置
- SSL 是**嵌套属性**：`spring.data.redis.ssl.enabled: true`，不是 `ssl: true`
- 这个坑踩过，yml 里已经是对的，别写错

### Security 放行规则
- `/api/auth/login`、`/api/auth/register`、`/api/auth/refresh` 不需要 Bearer Token
- `/api/auth/logout` 需要有效的 Access Token
- 其他所有 `/api/**` 默认需要鉴权

### 数据库
- MyBatis-Plus 逻辑删除：`deleted` 字段（0=未删，1=已删）
- 分页用 `Page<T>` + `IPage<T>`，**不要自己写 limit offset**
- Flyway 迁移文件命名：`V{version}__{desc}.sql`，放在 `src/main/resources/db/migration/`

### 异常处理
- 业务异常抛 `BusinessException(String message)`，GlobalExceptionHandler 统一转 `R.fail(code, message)`
- Controller 里**不要** try-catch 然后手动返回 R.fail，让全局处理器干

### 提交规范
Conventional Commits：`feat:` / `fix:` / `ci:` / `docs:` / `test:` / `refactor:`

## 踩过的坑（别再犯）

1. **gh auth login 默认没有 workflow scope**——第一次推 `.github/workflows/*.yml` 会被拒，需要 `gh auth refresh --scopes workflow`
2. **Spring Boot 3.5 把 `spring.data.redis.ssl` 从平铺布尔值改成嵌套对象**，yml 写 `ssl.enabled: true`
3. **UserMapperTest 改了 admin 的 timezone 会污染数据库**——测试前要准备好数据，别改 seed 数据后忘记还原
4. **MapStruct + Lombok 的 annotation processor 顺序不能乱**——pom.xml 里已经配好：lombok → binding → mapstruct-processor
5. **git 全局 proxy 127.0.0.1:7890 不稳定**——直连 GitHub 反而更稳，push 报错先检查是不是代理的锅
6. **Refresh Token 轮转防重放**——偷到一次的 token 只能用一次，用完就被覆盖
7. **Access Token 无状态**——JwtAuthenticationFilter 不查 Redis，别往里面加 Redis 校验
8. **GitHub Actions services 的 health-cmd 有空格必须加引号**——`"redis-cli ping"` 不然 Docker 会把 `ping` 当成镜像去 pull（本次踩的坑）
