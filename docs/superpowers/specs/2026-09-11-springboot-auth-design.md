# Workbench — Spring Boot 用户认证学习项目设计文档

- 日期：2026-09-11
- 状态：待用户审阅
- 项目根目录：`d:\github\workbench`（即 git 仓库根目录）

## 1. 项目概述

### 1.1 目标

为一个前端开发者构建一个**学习型 Spring Boot 项目**，核心功能为用户认证（注册 / 登录 / JWT 鉴权）。项目刻意采用工业界主流技术栈组合，覆盖后端开发典型横切关注点（参数校验、异常处理、日志、接口文档、数据库迁移、监控、测试），作为系统学习 Spring Boot 生态的载体。

### 1.2 演进路径

| 阶段 | 内容 | 状态 |
|---|---|---|
| 第一阶段 | 模块化单体，完整实现用户认证 | **本期** |
| 第二阶段 | 引入 Redis 做 token 刷新 / 黑名单 | 预留 |
| 第三阶段 | 按模块拆分 Spring Cloud 微服务 | 预留（包结构已为此设计） |

## 2. 技术栈选型（已与用户逐项确认）

| 项目 | 选型 | 版本 | 说明 |
|---|---|---|---|
| JDK | Temurin 17 | 17.0.20.1 | 本机已装 |
| 构建工具 | Maven | 3.6.3 | 本机已装 |
| Spring Boot | spring-boot-starter-parent | **3.5.13** | Java 17 可用，生态最新 |
| 认证 | JWT（jjwt，HS256） | 0.12.6 | 无状态；后续 Redis 做刷新 |
| 密码加密 | BCrypt | Spring Security 内置 | 不引入额外依赖 |
| ORM | mybatis-plus-spring-boot3-starter | **3.5.17** | 需配套 mybatis-plus-jsqlparser（见 2.1） |
| 数据库 | 本地 PostgreSQL | localhost:5432/workbench | postgres / 123456 |
| 数据库迁移 | flyway-core + flyway-database-postgresql | Boot 管理版本 | Flyway 10+ 方言独立成模块 |
| 接口文档 | springdoc-openapi-starter-webmvc-ui | **2.8.17** | 官方按 Boot 3.5.x 适配测试 |
| 对象映射 | MapStruct + lombok-mapstruct-binding | 1.6.3 / 0.2.0 | 与 Lombok 协作需 binding |
| 参数校验 | spring-boot-starter-validation | Boot 管理 | |
| AOP | spring-boot-starter-aop | Boot 管理 | 请求日志切面 |
| 监控 | spring-boot-starter-actuator | Boot 管理 | 仅暴露 health |
| 日志 | Logback（logback-spring.xml） | Boot 内置 | 按天滚动 + 分环境级别 |
| 简化代码 | Lombok | Boot 管理 | |
| 测试 | JUnit 5 + Mockito + AssertJ | spring-boot-starter-test | |

### 2.1 版本核实记录（2026-09-11 检索确认）

1. **springdoc 2.5.0 → 2.8.17**：早期讨论提过 2.5.0，其仅适配 Boot 3.2；锁定 Boot 3.5 后必须用 2.8.x。
2. **MyBatis-Plus 3.5.9+ 拆分**：分页插件（jsqlparser 相关）变为可选依赖，使用 `PaginationInnerInterceptor` 需额外引入 `com.baomidou:mybatis-plus-jsqlparser`（JDK 11+ 版本，本项目 JDK 17 适用）。
3. **Flyway 数据库方言模块化**：Flyway 10 起将数据库支持拆分为独立 artifact，PostgreSQL 需 `flyway-database-postgresql`，两者版本均由 Boot dependency management 管理。

## 3. 架构设计

### 3.1 模块化单体

按**业务模块**分包（而非按技术层分包），每个模块内部自带 controller/service/mapper/entity。模块高内聚，将来拆微服务时可直接将模块迁出为独立服务。

### 3.2 项目结构

```
workbench/                                        # git 仓库根目录
├── .gitignore
├── pom.xml
├── docs/superpowers/specs/                       # 设计文档
└── src/
    ├── main/java/com/workbench/
    │   ├── WorkbenchApplication.java             # 启动类
    │   ├── common/                               # 公共模块（跨模块共享）
    │   │   ├── result/R.java                     # 统一响应 {code, message, data}
    │   │   ├── exception/
    │   │   │   ├── BusinessException.java        # 业务异常（code + message）
    │   │   │   └── GlobalExceptionHandler.java   # 全局异常 + 校验异常处理
    │   │   ├── aspect/
    │   │   │   └── RequestLogAspect.java         # AOP 请求日志
    │   │   └── config/
    │   │       ├── MybatisPlusConfig.java        # 分页插件等
    │   │       └── OpenApiConfig.java            # Swagger 信息 + Bearer 鉴权方案
    │   └── auth/                                 # 认证模块（本期核心）
    │       ├── controller/AuthController.java    # /api/auth/**
    │       ├── service/AuthService.java
    │       ├── mapper/UserMapper.java            # extends BaseMapper<User>
    │       ├── entity/User.java
    │       ├── dto/                              # RegisterRequest / LoginRequest / LoginVO / UserVO
    │       ├── convert/UserConvert.java          # MapStruct，componentModel="spring"
    │       └── config/
    │           ├── SecurityConfig.java           # SecurityFilterChain
    │           ├── JwtProperties.java            # @ConfigurationProperties(prefix="jwt")
    │           ├── JwtUtil.java
    │           └── JwtAuthenticationFilter.java  # OncePerRequestFilter
    ├── main/resources/
    │   ├── application.yml                       # 公共配置，激活 dev
    │   ├── application-dev.yml                   # 开发环境（数据源、JWT 密钥、debug 日志）
    │   ├── application-prod.yml                  # 生产环境（info 日志、环境变量占位）
    │   ├── logback-spring.xml                    # 控制台 + 文件滚动日志
    │   └── db/migration/
    │       ├── V1__init_schema.sql               # 建 sys_user 表
    │       └── V2__seed_admin.sql                # 种子用户 admin
    └── test/java/com/workbench/auth/
        ├── JwtUtilTest.java
        └── AuthServiceTest.java
```

**说明**：`user/` 业务模块（查询个人信息、改密码等）本期不落盘——git 不跟踪空目录，待扩展功能时新增，届时以 `com.workbench.user` 包出现。

### 3.3 关键架构决策

| 决策 | 理由 |
|---|---|
| 表名用 `sys_user` 而非 `user` | `user` 是 PostgreSQL 保留字，直接使用需双引号转义，徒增心智负担 |
| JWT payload 只放 userId + username | 控制 token 在 ~200 字符；完整用户信息走 `/api/auth/me` 查库返回 |
| 统一响应 `R<T>` + HTTP 状态码语义化 | 前端拿到的 body 结构统一，HTTP 状态码与 body.code 一致（200/400/401/500） |
| Controller 直接抛 `BusinessException` | 由 `@RestControllerAdvice` 统一转响应，Controller 不写 try-catch |
| 包结构按业务模块切分 | 演进到 Spring Cloud 时模块即服务边界 |

## 4. 数据库设计

### 4.1 连接配置（dev 环境）

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/workbench
    username: postgres
    password: 123456
```

前置条件：用户需在本地 PostgreSQL 中创建 `workbench` 数据库（`CREATE DATABASE workbench;`），表结构由 Flyway 自动创建。

### 4.2 sys_user 表（V1__init_schema.sql）

```sql
CREATE TABLE sys_user (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(50)  NOT NULL UNIQUE,
    password    VARCHAR(100) NOT NULL,
    email       VARCHAR(100),
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  sys_user          IS '用户表';
COMMENT ON COLUMN sys_user.password IS 'BCrypt 哈希，固定 60 字符';
COMMENT ON COLUMN sys_user.enabled  IS '账号是否可用';
```

- `password` 存 BCrypt 哈希（60 字符），**永不存明文**
- `created_at` / `updated_at` 由数据库默认值维护（MP 插入时默认忽略 null 字段）；MyBatis-Plus 自动填充（MetaObjectHandler）作为替代方案，本期不用
- 实体类字段驼峰 ↔ 下划线由 MP `map-underscore-to-camel-case`（默认开启）映射

### 4.3 种子数据（V2__seed_admin.sql）

插入测试用户：`username=admin`，`password=123456`（BCrypt 哈希值）、`email=admin@workbench.com`。

BCrypt 哈希值在实施阶段通过 `new BCryptPasswordEncoder().encode("123456")` 生成后固化写入脚本——BCrypt 每次加密掺入随机盐，任何一次生成的合法哈希均可通过 `matches("123456", hash)` 校验。

### 4.4 Flyway 约定

- 脚本放 `src/main/resources/db/migration/`，命名 `V<版本号>__<描述>.sql`（双下划线）
- 应用启动时 Flyway 自动对比 `flyway_schema_history` 表，仅执行未执行的版本
- **已执行的脚本永不修改**（checksum 会不一致导致启动失败）；改表只能新增 `V3__xxx.sql`
- 后续所有表结构变更一律走 Flyway 脚本，禁止手工改库

## 5. 认证设计

### 5.1 JWT 配置（application-dev.yml）

```yaml
jwt:
  # HS256 要求密钥 >= 256 bit（32 字节），否则启动时抛 WeakKeyException
  secret: "workbench-dev-jwt-secret-key-must-be-at-least-32-bytes-long!"
  expiration: 7200       # token 有效期（秒），2 小时
  header: Authorization  # 携带 token 的请求头
  prefix: "Bearer "     # token 前缀
```

`JwtProperties`（`@ConfigurationProperties(prefix = "jwt")` + `@Data`）将上述配置绑定为对象注入。

### 5.2 JwtUtil（jjwt 0.12.x 新 API）

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final JwtProperties jwtProperties;
    private SecretKey key;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(
                jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Long userId, String username) {
        return generateToken(userId, username, jwtProperties.getExpiration());
    }

    /** 包级私有重载：供测试构造已过期 token */
    String generateToken(Long userId, String username, long ttlSeconds) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlSeconds * 1000))
                .signWith(key)
                .compact();
    }

    /** 签名错误或过期抛 JwtException 子类 */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }
}
```

注意：网上大量教程是 jjwt 0.9.x 旧 API（`setSubject()`、`parserBuilder()`），本项目统一 0.12.x 链式写法。

### 5.3 SecurityConfig

```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();   // 默认强度 10
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)   // 前后端分离 + 无 session，无需 CSRF
            .sessionManagement(s -> s
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html",
                                 "/v3/api-docs/**", "/actuator/health").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthenticationFilter,
                    UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

### 5.4 JwtAuthenticationFilter（OncePerRequestFilter）

流程：

1. 读取 `Authorization` 头；无 token 或前缀不匹配 → 直接放行（匿名请求交给授权规则判定）
2. 解析 token → 成功：以 `userId` 为 principal、`username` 为 credentials 构造 `UsernamePasswordAuthenticationToken`，放入 `SecurityContextHolder`，放行
3. 解析抛 `JwtException`（签名错 / 过期 / 格式错）→ **直接写 401 JSON 响应**（此时尚未进入 Controller，`@RestControllerAdvice` 捕获不到，必须在过滤器内响应）

401 响应体与 `R.fail(401, ...)` 结构一致，前端处理逻辑统一。

### 5.5 AuthService 核心逻辑

```java
// 注册
if (userMapper.selectOne(new LambdaQueryWrapper<User>()
        .eq(User::getUsername, req.getUsername())) != null) {
    throw new BusinessException(400, "用户名已存在");
}
User user = new User();
user.setUsername(req.getUsername());
user.setEmail(req.getEmail());
user.setPassword(passwordEncoder.encode(req.getPassword()));  // 随机盐哈希
userMapper.insert(user);

// 登录
User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
        .eq(User::getUsername, req.getUsername()));
if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
    throw new BusinessException(401, "用户名或密码错误");  // 统一提示，防撞库探测
}
String token = jwtUtil.generateToken(user.getId(), user.getUsername());
return new LoginVO(token, userConvert.toVO(user));   // MapStruct 转 UserVO（不含密码）
```

`/api/auth/me` 通过 `@AuthenticationPrincipal Long userId` 从 SecurityContext 取当前用户 ID，查库返回 `UserVO`。

### 5.6 BCrypt 要点（写给前端）

- **单向哈希**，不能"解密"，只能 `matches(明文, 哈希)` 比对
- 库中密文形如 `$2a$10$N9qo8...`：`$2a` 算法版本、`10` cost（2^10 轮），同一密码每次加密结果**都不同**（随机盐）
- JWT 的签名也**不是加密**：payload 仅 Base64 编码，任何人可解码查看（jwt.io 可验证），签名只保证不被篡改 → token 中绝不能放密码等敏感信息

### 5.7 Token 长度（已与用户确认无问题）

本设计 payload 精简（sub + username + iat + exp），token 约 150~250 字符：

- 头部段 ~23 字符 + 载荷段 ~90 字符 + HS256 签名段 43 字符
- localStorage（5~10MB）/ Cookie（4KB）/ Header（8KB）容量均富余 95% 以上
- token 不放 URL（会进访问日志、Referer 泄露）
- 若将来换 RS256 非对称签名，签名段膨胀至 344 字符——拆微服务多方验签时才值得

## 6. 接口设计

### 6.1 接口清单

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| POST | /api/auth/register | 注册 | 否 |
| POST | /api/auth/login | 登录，返回 token + 用户信息 | 否 |
| GET | /api/auth/me | 当前登录用户信息 | 是 |
| GET | /actuator/health | 健康检查 | 否 |

Swagger UI：`http://localhost:8090/swagger-ui.html`，配置 Bearer 鉴权方案后可在页面上点 Authorize 输入 token 调试受保护接口。

### 6.2 DTO 与校验规则

| DTO | 字段与校验 |
|---|---|
| RegisterRequest | username：`@NotBlank` + `@Pattern("^[a-zA-Z0-9_]{4,20}$")`；password：`@NotBlank` + `@Size(min=6, max=32)`；email：可选，`@Email` |
| LoginRequest | username、password：`@NotBlank` |
| LoginVO | token（String）、user（UserVO） |
| UserVO | id、username、email、createdAt —— **不含 password** |

Controller 方法参数标注 `@Valid` 触发校验。

### 6.3 统一响应 R\<T\>

```json
{ "code": 200, "message": "success", "data": { ... } }
```

| code | HTTP 状态 | 场景 |
|---|---|---|
| 200 | 200 | 成功 |
| 400 | 400 | 参数校验失败 / 业务规则拒绝（如用户名已存在） |
| 401 | 401 | 未登录 / token 无效或过期 / 账号密码错误 |
| 404 | 404 | 资源不存在（本期预留） |
| 500 | 500 | 未预期异常（body 提示"系统繁忙"，堆栈只进日志） |

### 6.4 全局异常处理（GlobalExceptionHandler）

| 异常 | 处理 |
|---|---|
| `BusinessException` | HTTP 状态与 code 用异常携带值，message 透出 |
| `MethodArgumentNotValidException` | 400，拼接第一条字段校验错误信息 |
| `Exception`（兜底） | 500，log.error 记完整堆栈，对外仅提示"系统繁忙，请稍后重试" |

## 7. 横切关注点

### 7.1 AOP 请求日志（RequestLogAspect）

- 切点：`execution(* com.workbench..controller..*(..))`
- `@Around` 环绕：记录方法签名、入参、出参、耗时（ms）
- 异常分支记录 `log.error` 后原样抛出，不吞异常
- 敏感字段（password）在日志输出时脱敏

### 7.2 Logback（logback-spring.xml）

- 控制台 appender：彩色 pattern，开发友好
- 文件 appender：`logs/workbench.log`，按天滚动，保留 30 天，总量上限 1GB
- `<springProfile name="dev">`：root INFO，`com.workbench` DEBUG；`<springProfile name="prod">`：root INFO，`com.workbench` INFO

### 7.3 多环境 Profile

| 文件 | 内容 |
|---|---|
| application.yml | 公共配置：应用名、MP 配置、`spring.profiles.active: dev` |
| application-dev.yml | 数据源、JWT 密钥、SQL 日志（StdOutImpl）、debug 级别 |
| application-prod.yml | 数据源改环境变量占位 `${DB_URL}` 等、info 级别、关闭 SQL 日志 |

### 7.4 Actuator

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health    # 仅暴露 health，最小攻击面
```

### 7.5 Swagger（OpenApiConfig）

- `OpenAPI` Bean：标题、版本、描述 + Bearer（JWT）securityScheme + 全局 securityRequirement
- Controller 标注 `@Tag`、接口标注 `@Operation`、DTO 标注 `@Schema`，文档可读性好

## 8. 测试设计

不追求覆盖率数字，聚焦核心逻辑：

**JwtUtilTest**
- 生成 → 解析往返：sub / username / userId 一致
- 过期 token：用包级私有 `generateToken(uid, name, -10)` 生成，断言抛 `ExpiredJwtException`
- 篡改 token：修改 payload 后解析，断言抛异常

**AuthServiceTest（Mockito mock UserMapper / PasswordEncoder / JwtUtil）**
- 注册：用户名已存在 → BusinessException(400)
- 注册：成功时密码经 `encode()` 加密入库（ArgumentCaptor 捕获，断言不等于明文）
- 登录：用户不存在 → BusinessException(401)
- 登录：密码不匹配 → BusinessException(401)
- 登录：成功 → 返回 token 且 UserVO 不含密码

集成验证（手动）：`mvn spring-boot:run` 启动后走 Swagger UI 完整跑通注册 → 登录 → 带 token 访问 `/api/auth/me`。

## 9. 本期明确不做（YAGNI）

- Redis token 刷新 / 黑名单 / 单设备登录 —— 第二阶段
- 邮箱验证、找回密码、OAuth2 第三方登录
- RBAC 角色权限（当前只区分"登录 / 未登录"）
- Spring Cloud 注册中心 / 网关 —— 第三阶段
- docker-compose（用户本地已装 PostgreSQL）
- 前端页面（纯后端 API，Swagger 调试）

## 10. pom.xml 关键配置

MapStruct 与 Lombok 注解处理器共存必须在 `maven-compiler-plugin` 显式声明处理器顺序，否则 MapStruct 生成实现类时看不到 Lombok 生成的 getter/setter：

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>org.projectlombok:lombok</path>
            <path>org.projectlombok:lombok-mapstruct-binding:0.2.0</path>
            <path>org.mapstruct:mapstruct-processor:1.6.3</path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

jjwt 三件套（api 编译期 / impl、jackson 运行期）：

```xml
<dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-api</artifactId><version>0.12.6</version></dependency>
<dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-impl</artifactId><version>0.12.6</version><scope>runtime</scope></dependency>
<dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-jackson</artifactId><version>0.12.6</version><scope>runtime</scope></dependency>
```
