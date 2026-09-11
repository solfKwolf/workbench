# Spring Boot 用户认证（Workbench）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按设计文档实现一个完整的 Spring Boot 用户认证服务（注册 / 登录 / JWT 鉴权），作为前端开发者的 Spring Boot 学习项目。

**Architecture:** 模块化单体（按业务分包：common + auth），Maven 单模块。Spring Security 无状态过滤链 + JWT（HS256）；MyBatis-Plus 访问 PostgreSQL；Flyway 管理数据库版本；统一响应 R\<T\> + 全局异常；springdoc 生成接口文档；AOP 请求日志 + Logback 滚动日志 + 多环境 Profile。

**Tech Stack:** JDK 17 / Maven 3.6.3 / Spring Boot 3.5.13 / MyBatis-Plus 3.5.17（+jsqlparser）/ PostgreSQL（本地，postgres/123456）/ Flyway（Boot 管版本）/ springdoc 2.8.17 / jjwt 0.12.6 / MapStruct 1.6.3 / Lombok / JUnit 5 + Mockito + AssertJ

**设计文档:** `docs/superpowers/specs/2026-09-11-springboot-auth-design.md`

---

## 环境前置条件（执行前检查）

- JDK 17 在 PATH（`java -version` 输出 17.x）
- Maven 3.6.3+ 在 PATH（`mvn -v`）
- 本地 PostgreSQL 运行中，超级用户 postgres 密码 123456，端口 5432
- `psql` 可用（若不在 PATH，用开始菜单的 "SQL Shell (psql)"，全部提示直接回车后输入密码 123456）
- 工作目录：`d:\github\workbench`（git 仓库已初始化，master 分支）

## 文件结构总览

| 文件 | 职责 |
|---|---|
| `pom.xml` | 依赖与构建配置（MapStruct+Lombok 处理器顺序） |
| `src/main/java/com/workbench/WorkbenchApplication.java` | 启动类 |
| `src/main/resources/application.yml` | 公共配置（profile 激活、MP、actuator） |
| `src/main/resources/application-dev.yml` | dev：数据源、Flyway、JWT、日志级别、SQL 日志 |
| `src/main/resources/application-prod.yml` | prod：环境变量占位 |
| `src/main/resources/logback-spring.xml` | 控制台 + 按天滚动文件日志 |
| `src/main/resources/db/migration/V1__init_schema.sql` | 建 sys_user 表 |
| `src/main/resources/db/migration/V2__seed_admin.sql` | 种子用户 admin |
| `common/result/R.java` | 统一响应 `{code, message, data}` |
| `common/exception/BusinessException.java` | 业务异常 |
| `common/exception/GlobalExceptionHandler.java` | 全局异常 + 校验异常处理 |
| `common/aspect/RequestLogAspect.java` | AOP 请求日志 |
| `common/config/MybatisPlusConfig.java` | MP 分页插件 |
| `common/config/OpenApiConfig.java` | Swagger + Bearer 鉴权方案 |
| `auth/entity/User.java` | 用户实体（@TableName sys_user） |
| `auth/mapper/UserMapper.java` | extends BaseMapper\<User\> |
| `auth/dto/RegisterRequest.java` `LoginRequest.java` `LoginVO.java` `UserVO.java` | 请求/响应对象 |
| `auth/convert/UserConvert.java` | MapStruct User→UserVO |
| `auth/config/JwtProperties.java` | jwt.* 配置绑定 |
| `auth/config/JwtUtil.java` | 生成/解析 token |
| `auth/config/JwtAuthenticationFilter.java` | JWT 过滤器（无 token 放行，坏 token 401） |
| `auth/config/SecurityConfig.java` | 过滤链 + BCrypt + 401 入口点 |
| `auth/service/AuthService.java` | 注册/登录/me 业务逻辑 |
| `auth/controller/AuthController.java` | /api/auth/** 接口 |
| `src/test/java/...` | 7 个测试类（见各任务） |

## 常见问题速查

- **Flyway checksum 不一致导致启动失败**：已执行的迁移脚本永不修改；改表只能新增 `V3__xxx.sql`
- **WeakKeyException**：jwt.secret 必须 ≥ 32 字节（dev yml 中已保证）
- **8080 被占用**：`netstat -ano | findstr :8080` 找 PID，`taskkill /PID <pid> /F`
- **PowerShell 中 curl 是别名**：命令一律写 `curl.exe`

---

### Task 1: Maven 项目骨架

**Files:**
- Create: `pom.xml`
- Create: `src/main/java/com/workbench/WorkbenchApplication.java`
- Create: `src/main/resources/application.yml`
- Create: `src/main/resources/application-dev.yml`

- [ ] **Step 1: 写 pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.13</version>
        <relativePath/>
    </parent>

    <groupId>com.workbench</groupId>
    <artifactId>workbench</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>workbench</name>
    <description>Spring Boot 用户认证学习项目</description>

    <properties>
        <java.version>17</java.version>
        <mybatis-plus.version>3.5.17</mybatis-plus.version>
        <springdoc.version>2.8.17</springdoc.version>
        <jjwt.version>0.12.6</jjwt.version>
        <mapstruct.version>1.6.3</mapstruct.version>
        <lombok-mapstruct-binding.version>0.2.0</lombok-mapstruct-binding.version>
    </properties>

    <dependencies>
        <!-- Web / 安全 / 校验 / AOP / 监控 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-aop</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <!-- ORM：MyBatis-Plus（Boot3 专用 starter；3.5.9+ 分页插件需额外引入 jsqlparser 模块） -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
            <version>${mybatis-plus.version}</version>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-jsqlparser</artifactId>
            <version>${mybatis-plus.version}</version>
        </dependency>

        <!-- 数据库驱动 / 迁移 -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
        </dependency>

        <!-- 接口文档 -->
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>${springdoc.version}</version>
        </dependency>

        <!-- JWT（api 编译期；impl/jackson 运行期） -->
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>${jjwt.version}</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>

        <!-- 工具 -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.mapstruct</groupId>
            <artifactId>mapstruct</artifactId>
            <version>${mapstruct.version}</version>
        </dependency>

        <!-- 测试 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <!-- 顺序关键：binding 必须在 lombok 和 mapstruct-processor 之间，
                         否则 MapStruct 生成实现类时看不到 Lombok 生成的 getter/setter -->
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                            <version>${lombok.version}</version>
                        </path>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok-mapstruct-binding</artifactId>
                            <version>${lombok-mapstruct-binding.version}</version>
                        </path>
                        <path>
                            <groupId>org.mapstruct</groupId>
                            <artifactId>mapstruct-processor</artifactId>
                            <version>${mapstruct.version}</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: 写启动类**

`src/main/java/com/workbench/WorkbenchApplication.java`：

```java
package com.workbench;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WorkbenchApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkbenchApplication.class, args);
    }
}
```

- [ ] **Step 3: 写 application.yml（公共配置）**

```yaml
spring:
  application:
    name: workbench
  profiles:
    active: dev

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true   # created_at <-> createdAt 自动映射

management:
  endpoints:
    web:
      exposure:
        include: health   # 最小暴露面，仅健康检查
```

- [ ] **Step 4: 写 application-dev.yml（dev 完整配置，一次到位）**

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/workbench
    username: postgres
    password: 123456
    driver-class-name: org.postgresql.Driver
  flyway:
    enabled: true
    locations: classpath:db/migration

mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl   # 控制台打印 SQL（学习期友好）

logging:
  level:
    com.workbench: debug

jwt:
  # HS256 要求密钥 >= 256 bit（32 字节），否则启动抛 WeakKeyException
  secret: "workbench-dev-jwt-secret-key-must-be-at-least-32-bytes-long!"
  expiration: 7200        # token 有效期（秒），2 小时
  header: Authorization   # 携带 token 的请求头
  prefix: "Bearer "       # token 前缀
```

- [ ] **Step 5: 编译验证**

Run: `mvn clean compile`
Expected: `BUILD SUCCESS`（首次会下载依赖，耗时数分钟属正常）

- [ ] **Step 6: 提交**

```bash
git add pom.xml src/
git commit -m "feat: Maven 项目骨架（Boot 3.5.13 全量依赖）"
```

---

### Task 2: 数据库与 Flyway 迁移

**Files:**
- Create: `src/main/resources/db/migration/V1__init_schema.sql`
- Create: `src/main/resources/db/migration/V2__seed_admin.sql`
- Create: `src/test/java/com/workbench/auth/BCryptHashGeneratorTest.java`

- [ ] **Step 1: 创建数据库**

```bash
psql -U postgres -c "CREATE DATABASE workbench;"
```
Expected: `CREATE DATABASE`（输入密码 123456；若报 already exists 说明已建过，跳过）

- [ ] **Step 2: 写 V1__init_schema.sql**

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

- [ ] **Step 3: 写哈希生成测试并运行（种子密码 123456 的 BCrypt 哈希每次随机，必须现场生成）**

`src/test/java/com/workbench/auth/BCryptHashGeneratorTest.java`：

```java
package com.workbench.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 生成种子用户密码的 BCrypt 哈希并打印。
 * 运行: mvn test -Dtest=BCryptHashGeneratorTest
 * 输出行 BCRYPT_HASH=... 即 V2__seed_admin.sql 所需哈希。
 * BCrypt 每次加密掺入随机盐——输出的哈希每次不同，但都能通过 matches("123456", hash) 校验。
 */
class BCryptHashGeneratorTest {

    @Test
    void generateAndPrintBcryptHash() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = encoder.encode("123456");
        System.out.println("BCRYPT_HASH=" + hash);
        assertThat(hash).startsWith("$2a$10$").hasSize(60);
        assertThat(encoder.matches("123456", hash)).isTrue();
    }
}
```

Run: `mvn test -Dtest=BCryptHashGeneratorTest`
Expected: `Tests run: 1, Failures: 0`，控制台输出 `BCRYPT_HASH=$2a$10$xxxxx...`（60 字符）。**复制这个值。**

- [ ] **Step 4: 写 V2__seed_admin.sql（粘贴上一步输出的哈希）**

```sql
-- 种子用户：admin / 123456（密码为 BCrypt 哈希）
-- 注意：把下方哈希替换为 BCryptHashGeneratorTest 实际输出的值（60 字符，$2a$10$ 开头）
INSERT INTO sys_user (username, password, email, enabled)
VALUES ('admin', '$2a$10$此处粘贴BCryptHashGeneratorTest输出的哈希', 'admin@workbench.com', TRUE);
```

- [ ] **Step 5: 启动应用验证 Flyway 迁移**

Run: `mvn spring-boot:run`
Expected 日志（顺序出现）：
- `Migrating schema "public" to version "1 - init schema"`
- `Migrating schema "public" to version "2 - seed admin"`
- `Successfully applied 2 migrations`
- `Tomcat started on port 8080`

看到 Tomcat started 后 `Ctrl+C` 停止。

- [ ] **Step 6: 数据库核验**

```bash
psql -U postgres -d workbench -c "SELECT version, description, success FROM flyway_schema_history;"
psql -U postgres -d workbench -c "SELECT id, username, password, enabled FROM sys_user;"
```
Expected: history 表 2 行且 success = t；sys_user 1 行 admin，password 以 `$2a$10$` 开头。

- [ ] **Step 7: 提交**

```bash
git add src/
git commit -m "feat: Flyway 数据库迁移（sys_user 表 + admin 种子用户）"
```

---

### Task 3: 统一响应与全局异常（TDD）

**Files:**
- Create: `src/main/java/com/workbench/common/result/R.java`
- Create: `src/main/java/com/workbench/common/exception/BusinessException.java`
- Create: `src/main/java/com/workbench/common/exception/GlobalExceptionHandler.java`
- Test: `src/test/java/com/workbench/common/result/RTest.java`
- Test: `src/test/java/com/workbench/common/exception/GlobalExceptionHandlerTest.java`

- [ ] **Step 1: 写失败测试**

`src/test/java/com/workbench/common/result/RTest.java`：

```java
package com.workbench.common.result;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RTest {

    @Test
    void ok_carriesData() {
        R<String> r = R.ok("hello");
        assertThat(r.getCode()).isEqualTo(200);
        assertThat(r.getMessage()).isEqualTo("success");
        assertThat(r.getData()).isEqualTo("hello");
    }

    @Test
    void fail_carriesCodeAndMessage() {
        R<Void> r = R.fail(401, "未登录");
        assertThat(r.getCode()).isEqualTo(401);
        assertThat(r.getMessage()).isEqualTo("未登录");
        assertThat(r.getData()).isNull();
    }
}
```

`src/test/java/com/workbench/common/exception/GlobalExceptionHandlerTest.java`：

```java
package com.workbench.common.exception;

import com.workbench.common.result.R;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleBusiness_returnsMatchingStatusAndBody() {
        ResponseEntity<R<Void>> resp = handler.handleBusiness(new BusinessException(400, "用户名已存在"));

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(400);
        assertThat(resp.getBody().getMessage()).isEqualTo("用户名已存在");
        assertThat(resp.getBody().getData()).isNull();
    }

    @Test
    void handleBusiness_401MapsTo401() {
        ResponseEntity<R<Void>> resp =
                handler.handleBusiness(new BusinessException(401, "用户名或密码错误"));
        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void handleMessageNotReadable_returns400() {
        ResponseEntity<R<Void>> resp = handler
                .handleMessageNotReadable(new HttpMessageNotReadableException("bad", null));
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(resp.getBody().getMessage()).isEqualTo("请求体缺失或格式错误");
    }

    @Test
    void handleOther_returns500WithGenericMessage() {
        ResponseEntity<R<Void>> resp = handler.handleOther(new RuntimeException("boom"));
        assertThat(resp.getStatusCode().value()).isEqualTo(500);
        assertThat(resp.getBody().getMessage()).isEqualTo("系统繁忙，请稍后重试");
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn test -Dtest="RTest,GlobalExceptionHandlerTest"`
Expected: **编译错误**（R / BusinessException / GlobalExceptionHandler 不存在）——这就是失败状态。

- [ ] **Step 3: 写实现**

`src/main/java/com/workbench/common/result/R.java`：

```java
package com.workbench.common.result;

import lombok.Data;

/**
 * 统一响应：{code, message, data}
 * code 与 HTTP 状态码语义一致（200/400/401/404/500）
 */
@Data
public class R<T> {

    private int code;
    private String message;
    private T data;

    private R(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> R<T> ok() {
        return new R<>(200, "success", null);
    }

    public static <T> R<T> ok(T data) {
        return new R<>(200, "success", data);
    }

    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null);
    }
}
```

`src/main/java/com/workbench/common/exception/BusinessException.java`：

```java
package com.workbench.common.exception;

import lombok.Getter;

/** 业务异常：code 同时作为 HTTP 状态码 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
```

`src/main/java/com/workbench/common/exception/GlobalExceptionHandler.java`：

```java
package com.workbench.common.exception;

import com.workbench.common.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：状态码与 code 用异常携带值 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<R<Void>> handleBusiness(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return ResponseEntity.status(e.getCode()).body(R.fail(e.getCode(), e.getMessage()));
    }

    /** @Valid 参数校验失败：取第一条字段错误 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<Void>> handleValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse("参数校验失败");
        return ResponseEntity.badRequest().body(R.fail(400, message));
    }

    /** 请求体 JSON 格式错误（如漏写字段类型不对） */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<R<Void>> handleMessageNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(R.fail(400, "请求体缺失或格式错误"));
    }

    /** 兜底：堆栈只进日志，对外不泄露细节 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleOther(Exception e) {
        log.error("未预期异常", e);
        return ResponseEntity.internalServerError().body(R.fail(500, "系统繁忙，请稍后重试"));
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn test -Dtest="RTest,GlobalExceptionHandlerTest"`
Expected: `Tests run: 6, Failures: 0`

- [ ] **Step 5: 提交**

```bash
git add src/
git commit -m "feat: 统一响应 R 与全局异常处理（含校验异常）"
```

---

### Task 4: User 实体与 Mapper（TDD）

**Files:**
- Create: `src/main/java/com/workbench/auth/entity/User.java`
- Create: `src/main/java/com/workbench/auth/mapper/UserMapper.java`
- Create: `src/main/java/com/workbench/common/config/MybatisPlusConfig.java`
- Test: `src/test/java/com/workbench/auth/mapper/UserMapperTest.java`

- [ ] **Step 1: 写失败测试（集成测试，直连本地库验证实体映射与种子数据）**

`src/test/java/com/workbench/auth/mapper/UserMapperTest.java`：

```java
package com.workbench.auth.mapper;

import com.workbench.auth.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class UserMapperTest {

    @Autowired
    private UserMapper userMapper;

    @Test
    void selectById_returnsSeededAdmin() {
        User user = userMapper.selectById(1L);

        assertThat(user).isNotNull();
        assertThat(user.getUsername()).isEqualTo("admin");
        assertThat(user.getPassword()).startsWith("$2a$");  // 存的是 BCrypt 哈希而非明文
        assertThat(user.getEnabled()).isTrue();
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn test -Dtest=UserMapperTest`
Expected: **编译错误**（User / UserMapper 不存在）

- [ ] **Step 3: 写实现**

`src/main/java/com/workbench/auth/entity/User.java`：

```java
package com.workbench.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

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

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
```

`src/main/java/com/workbench/auth/mapper/UserMapper.java`：

```java
package com.workbench.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workbench.auth.entity.User;
import org.apache.ibatis.annotations.Mapper;

/** @Mapper 注解使 MyBatis-Plus starter 自动扫描注册（无需 @MapperScan） */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
```

`src/main/java/com/workbench/common/config/MybatisPlusConfig.java`：

```java
package com.workbench.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {

    /** 分页插件（后续列表接口的基础设施） */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        return interceptor;
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn test -Dtest=UserMapperTest`
Expected: `Tests run: 1, Failures: 0`（日志可见 `SELECT ... FROM sys_user WHERE id = ?`）

- [ ] **Step 5: 提交**

```bash
git add src/
git commit -m "feat: User 实体与 Mapper（MyBatis-Plus）"
```

---

### Task 5: JWT 工具类（TDD）

**Files:**
- Create: `src/main/java/com/workbench/auth/config/JwtProperties.java`
- Create: `src/main/java/com/workbench/auth/config/JwtUtil.java`
- Test: `src/test/java/com/workbench/auth/config/JwtUtilTest.java`

- [ ] **Step 1: 写失败测试**

`src/test/java/com/workbench/auth/config/JwtUtilTest.java`：

```java
package com.workbench.auth.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret("workbench-dev-jwt-secret-key-must-be-at-least-32-bytes-long!");
        props.setExpiration(7200L);
        jwtUtil = new JwtUtil(props);
        jwtUtil.init();
    }

    @Test
    void generateAndParse_roundTrip() {
        String token = jwtUtil.generateToken(1L, "zhangsan");

        Claims claims = jwtUtil.parseToken(token);

        assertThat(jwtUtil.getUserId(claims)).isEqualTo(1L);
        assertThat(claims.get("username", String.class)).isEqualTo("zhangsan");
    }

    @Test
    void parseToken_expired_throwsExpiredJwtException() {
        String token = jwtUtil.generateToken(1L, "zhangsan", -10L);  // 已过期 10 秒

        assertThatThrownBy(() -> jwtUtil.parseToken(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void parseToken_tampered_throwsJwtException() {
        String token = jwtUtil.generateToken(1L, "zhangsan");
        // 篡改 payload 段最后一个字符（签名必然校验失败）
        char[] chars = token.toCharArray();
        int i = token.lastIndexOf('.') - 1;
        chars[i] = chars[i] == 'A' ? 'B' : 'A';

        assertThatThrownBy(() -> jwtUtil.parseToken(new String(chars)))
                .isInstanceOf(JwtException.class);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn test -Dtest=JwtUtilTest`
Expected: **编译错误**（JwtProperties / JwtUtil 不存在）

- [ ] **Step 3: 写实现**

`src/main/java/com/workbench/auth/config/JwtProperties.java`：

```java
package com.workbench.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** application-*.yml 中 jwt.* 前缀配置的绑定对象 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** HS256 密钥（>= 32 字节） */
    private String secret;

    /** token 有效期（秒） */
    private Long expiration;

    /** 携带 token 的请求头 */
    private String header;

    /** token 前缀 */
    private String prefix;
}
```

`src/main/java/com/workbench/auth/config/JwtUtil.java`：

```java
package com.workbench.auth.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/** jjwt 0.12.x API（网上旧教程的 setSubject/parserBuilder 已废弃，注意区分） */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final JwtProperties jwtProperties;
    private SecretKey key;

    /** 启动时把字符串密钥转成 HMAC-SHA 算法要求的 SecretKey */
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
                .subject(String.valueOf(userId))       // sub：用户唯一标识
                .claim("username", username)           // 自定义声明
                .issuedAt(now)                         // iat
                .expiration(new Date(now.getTime() + ttlSeconds * 1000))  // exp
                .signWith(key)                         // 默认 HS256
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

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn test -Dtest=JwtUtilTest`
Expected: `Tests run: 3, Failures: 0`

- [ ] **Step 5: 提交**

```bash
git add src/
git commit -m "feat: JWT 工具类（jjwt 0.12.x，含过期/篡改测试）"
```

---

### Task 6: DTO 与 MapStruct 转换器（TDD）

**Files:**
- Create: `src/main/java/com/workbench/auth/dto/RegisterRequest.java`
- Create: `src/main/java/com/workbench/auth/dto/LoginRequest.java`
- Create: `src/main/java/com/workbench/auth/dto/UserVO.java`
- Create: `src/main/java/com/workbench/auth/dto/LoginVO.java`
- Create: `src/main/java/com/workbench/auth/convert/UserConvert.java`
- Test: `src/test/java/com/workbench/auth/convert/UserConvertTest.java`

- [ ] **Step 1: 写失败测试**

`src/test/java/com/workbench/auth/convert/UserConvertTest.java`：

```java
package com.workbench.auth.convert;

import com.workbench.auth.dto.UserVO;
import com.workbench.auth.entity.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserConvertTest {

    private final UserConvert userConvert = new UserConvertImpl();

    @Test
    void toVO_mapsSameNameFields() {
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        user.setPassword("$2a$10$secret-hash");
        user.setEmail("admin@workbench.com");
        user.setEnabled(true);
        user.setCreatedAt(LocalDateTime.of(2026, 9, 11, 10, 0));

        UserVO vo = userConvert.toVO(user);

        assertThat(vo.getId()).isEqualTo(1L);
        assertThat(vo.getUsername()).isEqualTo("admin");
        assertThat(vo.getEmail()).isEqualTo("admin@workbench.com");
        assertThat(vo.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 9, 11, 10, 0));
        // UserVO 结构上没有 password 字段——从类型层面杜绝密码泄露
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn test -Dtest=UserConvertTest`
Expected: **编译错误**（UserConvertImpl 未生成——接口还不存在）

- [ ] **Step 3: 写 DTO 与转换器**

`src/main/java/com/workbench/auth/dto/RegisterRequest.java`：

```java
package com.workbench.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

@Data
@ToString(exclude = "password")  // AOP 打印入参时密码脱敏
@Schema(description = "注册请求")
public class RegisterRequest {

    @Schema(description = "用户名（4-20位字母数字下划线）", example = "zhangsan")
    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_]{4,20}$", message = "用户名须为4-20位字母数字下划线")
    private String username;

    @Schema(description = "密码（6-32位）", example = "123456")
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度须为6-32位")
    private String password;

    @Schema(description = "邮箱（可选）", example = "zhangsan@test.com")
    @Email(message = "邮箱格式不正确")
    private String email;
}
```

`src/main/java/com/workbench/auth/dto/LoginRequest.java`：

```java
package com.workbench.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;

@Data
@ToString(exclude = "password")  // AOP 打印入参时密码脱敏
@Schema(description = "登录请求")
public class LoginRequest {

    @Schema(description = "用户名", example = "admin")
    @NotBlank(message = "用户名不能为空")
    private String username;

    @Schema(description = "密码", example = "123456")
    @NotBlank(message = "密码不能为空")
    private String password;
}
```

`src/main/java/com/workbench/auth/dto/UserVO.java`：

```java
package com.workbench.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 对外用户信息——不含 password（安全边界靠类型约束，而非靠删除字段的自觉） */
@Data
@Schema(description = "用户信息（不含密码）")
public class UserVO {

    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "注册时间")
    private LocalDateTime createdAt;
}
```

`src/main/java/com/workbench/auth/dto/LoginVO.java`：

```java
package com.workbench.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "登录响应")
public class LoginVO {

    @Schema(description = "JWT token，后续请求放 Authorization: Bearer <token>")
    private String token;

    @Schema(description = "用户信息")
    private UserVO user;
}
```

`src/main/java/com/workbench/auth/convert/UserConvert.java`：

```java
package com.workbench.auth.convert;

import com.workbench.auth.dto.UserVO;
import com.workbench.auth.entity.User;
import org.mapstruct.Mapper;

/** componentModel=spring：生成实现类并注册为 Bean，通过构造器注入使用（非 INSTANCE 静态方式） */
@Mapper(componentModel = "spring")
public interface UserConvert {

    /** 同名字段自动映射；password 不在 UserVO 中，天然不会带出 */
    UserVO toVO(User user);
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn test -Dtest=UserConvertTest`
Expected: `Tests run: 1, Failures: 0`（编译时 MapStruct 会在 `target/generated-sources/annotations/` 生成 `UserConvertImpl.java`）

- [ ] **Step 5: 提交**

```bash
git add src/
git commit -m "feat: 认证 DTO（含校验注解）与 MapStruct 转换器"
```

---

### Task 7: Security 过滤链与 JWT 过滤器

**Files:**
- Create: `src/main/java/com/workbench/auth/config/JwtAuthenticationFilter.java`
- Create: `src/main/java/com/workbench/auth/config/SecurityConfig.java`

> 顺序说明：Security 先于 AuthService 实现，因为 AuthService 需要 PasswordEncoder Bean；先建 Security 可保证每个任务结束时 `mvn test` 全绿（@SpringBootTest 能加载完整上下文）。

- [ ] **Step 1: 写 JwtAuthenticationFilter**

`src/main/java/com/workbench/auth/config/JwtAuthenticationFilter.java`：

```java
package com.workbench.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workbench.common.result.R;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * 每个请求过一遍：
 * 1. 无 token / 前缀不匹配 -> 放行（公开接口直接通过，受保护接口由授权规则拦下）
 * 2. token 有效 -> 构造 Authentication 放入 SecurityContext
 * 3. token 无效/过期 -> 过滤器内直接回 401（此时还没进 Controller，全局异常处理器捕获不到）
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProperties;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(jwtProperties.getHeader());

        if (header == null || !header.startsWith(jwtProperties.getPrefix())) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(jwtProperties.getPrefix().length());
        try {
            Claims claims = jwtUtil.parseToken(token);
            Long userId = jwtUtil.getUserId(claims);
            String username = claims.get("username", String.class);

            // principal=userId（供 @AuthenticationPrincipal 取用），credentials=username
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userId, username, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException e) {
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    objectMapper.writeValueAsString(R.fail(401, "token 无效或已过期")));
            return;
        }

        filterChain.doFilter(request, response);
    }
}
```

- [ ] **Step 2: 写 SecurityConfig**

`src/main/java/com/workbench/auth/config/SecurityConfig.java`：

```java
package com.workbench.auth.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /** BCrypt 默认强度 10（2^10 轮哈希），加密 encode / 比对 matches */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 前后端分离 + 无状态 token，不需要 CSRF（它依赖 session）
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html",
                                 "/v3/api-docs/**", "/actuator/health").permitAll()
                .anyRequest().authenticated())
            // 未认证（无 token）访问受保护接口 -> 401 JSON（Spring 默认是 403 空响应，不符合前端习惯）
            .exceptionHandling(e -> e.authenticationEntryPoint((request, response, ex) -> {
                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(
                        "{\"code\":401,\"message\":\"未登录或token缺失\",\"data\":null}");
            }))
            // JWT 过滤器插在用户名密码过滤器之前
            .addFilterBefore(jwtAuthenticationFilter,
                    UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

- [ ] **Step 3: 编译 + 全量测试**

Run: `mvn clean test`
Expected: `BUILD SUCCESS`，`Tests run: 11, Failures: 0`（含 @SpringBootTest 的 UserMapperTest——上下文已能加载 Security Bean）

- [ ] **Step 4: 启动冒烟验证 401 行为**

Run: `mvn spring-boot:run`（另开一个终端）

```bash
curl.exe http://localhost:8080/actuator/health
curl.exe http://localhost:8080/api/auth/me
curl.exe http://localhost:8080/api/auth/me -H "Authorization: Bearer invalid.token.here"
```
Expected:
- 第一条：`{"status":"UP"}`
- 第二条：`{"code":401,"message":"未登录或token缺失","data":null}`
- 第三条：`{"code":401,"message":"token 无效或已过期","data":null}`

`Ctrl+C` 停止应用。

- [ ] **Step 5: 提交**

```bash
git add src/
git commit -m "feat: Spring Security 无状态过滤链与 JWT 认证过滤器"
```

---

### Task 8: AuthService 业务逻辑（TDD）

**Files:**
- Create: `src/main/java/com/workbench/auth/service/AuthService.java`
- Test: `src/test/java/com/workbench/auth/service/AuthServiceTest.java`

- [ ] **Step 1: 写失败测试（纯 Mockito 单测，不起 Spring 上下文）**

`src/test/java/com/workbench/auth/service/AuthServiceTest.java`：

```java
package com.workbench.auth.service;

import com.workbench.auth.convert.UserConvert;
import com.workbench.auth.dto.LoginRequest;
import com.workbench.auth.dto.LoginVO;
import com.workbench.auth.dto.RegisterRequest;
import com.workbench.auth.dto.UserVO;
import com.workbench.auth.entity.User;
import com.workbench.auth.mapper.UserMapper;
import com.workbench.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private UserConvert userConvert;

    @InjectMocks private AuthService authService;

    private RegisterRequest registerReq() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("newuser");
        req.setPassword("123456");
        req.setEmail("new@test.com");
        return req;
    }

    private LoginRequest loginReq() {
        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("123456");
        return req;
    }

    @Test
    void register_usernameExists_throws400() {
        when(userMapper.selectOne(any())).thenReturn(new User());

        assertThatThrownBy(() -> authService.register(registerReq()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(400))
                .hasMessage("用户名已存在");
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void register_success_encodesPasswordBeforeInsert() {
        when(userMapper.selectOne(any())).thenReturn(null);
        when(passwordEncoder.encode("123456")).thenReturn("$2a$10$encoded-hash");

        authService.register(registerReq());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        assertThat(captor.getValue().getPassword())
                .isEqualTo("$2a$10$encoded-hash")      // 入库的是哈希
                .isNotEqualTo("123456");               // 而非明文
    }

    @Test
    void login_userNotFound_throws401() {
        when(userMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> authService.login(loginReq()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(401))
                .hasMessage("用户名或密码错误");
    }

    @Test
    void login_passwordMismatch_throws401() {
        User user = new User();
        user.setPassword("$2a$10$stored-hash");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("123456", "$2a$10$stored-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(loginReq()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(401));
    }

    @Test
    void login_success_returnsTokenAndVO() {
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        user.setPassword("$2a$10$stored-hash");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("123456", "$2a$10$stored-hash")).thenReturn(true);
        when(jwtUtil.generateToken(1L, "admin")).thenReturn("mock-token");
        UserVO vo = new UserVO();
        vo.setId(1L);
        vo.setUsername("admin");
        when(userConvert.toVO(user)).thenReturn(vo);

        LoginVO result = authService.login(loginReq());

        assertThat(result.getToken()).isEqualTo("mock-token");
        assertThat(result.getUser().getId()).isEqualTo(1L);
        assertThat(result.getUser().getUsername()).isEqualTo("admin");
    }
}
```

> 注意：`JwtUtil` 与 `AuthService` 同包（都在 `com.workbench.auth` 下不同子包），测试中 mock 的 `JwtUtil` 指 `com.workbench.auth.config.JwtUtil`，import 不要漏。

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn test -Dtest=AuthServiceTest`
Expected: **编译错误**（AuthService 不存在）

- [ ] **Step 3: 写实现**

`src/main/java/com/workbench/auth/service/AuthService.java`：

```java
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
        userMapper.insert(user);
        log.info("用户注册成功: {}", req.getUsername());
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
```

- [ ] **Step 4: 全量测试**

Run: `mvn clean test`
Expected: `Tests run: 16, Failures: 0`

- [ ] **Step 5: 提交**

```bash
git add src/
git commit -m "feat: 认证业务逻辑（注册/登录/me，Mockito 单测）"
```

---

### Task 9: AuthController 与 Swagger

**Files:**
- Create: `src/main/java/com/workbench/auth/controller/AuthController.java`
- Create: `src/main/java/com/workbench/common/config/OpenApiConfig.java`

- [ ] **Step 1: 写 AuthController**

`src/main/java/com/workbench/auth/controller/AuthController.java`：

```java
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
```

- [ ] **Step 2: 写 OpenApiConfig**

`src/main/java/com/workbench/common/config/OpenApiConfig.java`：

```java
package com.workbench.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        String schemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("Workbench API")
                        .version("v0.1")
                        .description("Spring Boot 用户认证学习项目接口文档"))
                // Swagger UI 右上角 Authorize 按钮：粘贴 token 后调试受保护接口
                .addSecurityItem(new SecurityRequirement().addList(schemeName))
                .components(new Components()
                        .addSecuritySchemes(schemeName,
                                new SecurityScheme()
                                        .name(schemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}
```

- [ ] **Step 3: 编译 + 全量测试**

Run: `mvn clean test`
Expected: `BUILD SUCCESS`，`Tests run: 16, Failures: 0`

- [ ] **Step 4: 启动并做接口冒烟**

Run: `mvn spring-boot:run`（另开终端执行；注意 PowerShell 下 JSON 要用单引号包裹、内部双引号转义）

```bash
curl.exe -X POST http://localhost:8080/api/auth/register -H "Content-Type: application/json" -d '{\"username\":\"zhangsan\",\"password\":\"123456\",\"email\":\"zhangsan@test.com\"}'
```
Expected: `{"code":200,"message":"success","data":null}`

```bash
curl.exe -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{\"username\":\"admin\",\"password\":\"123456\"}'
```
Expected: `{"code":200,...,"data":{"token":"eyJhbGciOiJIUzI1NiJ9...","user":{...}}}`——复制 token

```bash
curl.exe http://localhost:8080/api/auth/me -H "Authorization: Bearer <粘贴token>"
```
Expected: `{"code":200,...,"data":{"id":1,"username":"admin","email":"admin@workbench.com","createdAt":"..."}}`

浏览器打开 `http://localhost:8080/swagger-ui.html` 确认三个接口可见、Authorize 按钮可用。`Ctrl+C` 停止。

- [ ] **Step 5: 提交**

```bash
git add src/
git commit -m "feat: 认证接口与 Swagger 文档（Bearer 调试）"
```

---

### Task 10: AOP 日志、Logback 与 Profile

**Files:**
- Create: `src/main/java/com/workbench/common/aspect/RequestLogAspect.java`
- Create: `src/main/resources/logback-spring.xml`
- Create: `src/main/resources/application-prod.yml`

- [ ] **Step 1: 写 AOP 请求日志切面**

`src/main/java/com/workbench/common/aspect/RequestLogAspect.java`：

```java
package com.workbench.common.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 拦截所有模块 controller 包下的接口，统一记录入参/出参/耗时。
 * 密码脱敏靠 DTO 上的 @ToString(exclude = "password")——本切面打印的是 toString 结果。
 */
@Slf4j
@Aspect
@Component
public class RequestLogAspect {

    @Pointcut("execution(* com.workbench..controller..*(..))")
    public void controllerPointcut() {
    }

    @Around("controllerPointcut()")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        String method = pjp.getSignature().toShortString();
        long start = System.currentTimeMillis();
        log.info("--> {} 入参: {}", method, Arrays.toString(pjp.getArgs()));
        try {
            Object result = pjp.proceed();
            log.info("<-- {} 出参: {} 耗时: {}ms",
                    method, result, System.currentTimeMillis() - start);
            return result;
        } catch (Throwable e) {
            log.error("<!! {} 异常: {} 耗时: {}ms",
                    method, e.getMessage(), System.currentTimeMillis() - start);
            throw e;   // 原样抛出，交给全局异常处理器
        }
    }
}
```

- [ ] **Step 2: 写 logback-spring.xml（控制台 + 按天滚动文件）**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <property name="LOG_PATTERN"
              value="%d{yyyy-MM-dd HH:mm:ss.SSS} %highlight(%-5level) [%thread] %cyan(%logger{50}) - %msg%n"/>
    <property name="LOG_HOME" value="./logs"/>

    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>${LOG_PATTERN}</pattern>
            <charset>UTF-8</charset>
        </encoder>
    </appender>

    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_HOME}/workbench.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>${LOG_HOME}/workbench.%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>30</maxHistory>
            <totalSizeCap>1GB</totalSizeCap>
        </rollingPolicy>
        <encoder>
            <pattern>${LOG_PATTERN}</pattern>
            <charset>UTF-8</charset>
        </encoder>
    </appender>

    <!-- dev：控制台 + 文件，业务包 DEBUG -->
    <springProfile name="dev">
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
            <appender-ref ref="FILE"/>
        </root>
        <logger name="com.workbench" level="DEBUG"/>
    </springProfile>

    <!-- prod：控制台 + 文件，业务包 INFO -->
    <springProfile name="prod">
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
            <appender-ref ref="FILE"/>
        </root>
        <logger name="com.workbench" level="INFO"/>
    </springProfile>
</configuration>
```

- [ ] **Step 3: 写 application-prod.yml（环境变量占位）**

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/workbench}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD}        # 无默认值：不设置则拒绝启动（防误连）
  flyway:
    enabled: true

mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.nologging.NoLoggingImpl   # 生产关闭 SQL 日志

jwt:
  secret: ${JWT_SECRET}
  expiration: 7200
  header: Authorization
  prefix: "Bearer "
```

- [ ] **Step 4: 编译 + 全量测试**

Run: `mvn clean test`
Expected: `BUILD SUCCESS`，`Tests run: 16, Failures: 0`

- [ ] **Step 5: 启动验证 AOP 与文件日志**

Run: `mvn spring-boot:run`，另开终端登录一次：

```bash
curl.exe -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{\"username\":\"admin\",\"password\":\"123456\"}'
```

Expected：
- 控制台出现 `--> AuthController.login(..) 入参: [LoginRequest(username=admin, password=****)]`（密码已脱敏）
- 控制台出现 `<-- ... 耗时: xxms`
- 项目根目录生成 `logs/workbench.log` 且内容与控制台一致

`Ctrl+C` 停止。（`logs/` 已在 .gitignore，不会误提交）

- [ ] **Step 6: 提交**

```bash
git add src/
git commit -m "feat: AOP 请求日志、Logback 滚动日志与 prod Profile"
```

---

### Task 11: 全量验证与收尾

**Files:** 无新增（纯验证）

- [ ] **Step 1: 全量测试**

Run: `mvn clean test`
Expected: `BUILD SUCCESS`；`Tests run: 16, Failures: 0, Errors: 0`（7 个测试类：BCryptHashGeneratorTest / RTest / GlobalExceptionHandlerTest / UserMapperTest / JwtUtilTest / UserConvertTest / AuthServiceTest）

- [ ] **Step 2: 启动应用跑完整 E2E 清单**

Run: `mvn spring-boot:run`，逐项验证（curl.exe 或 Swagger UI 均可）：

| # | 操作 | 期望 |
|---|---|---|
| 1 | `GET /actuator/health` | `{"status":"UP"}` |
| 2 | `POST /register` 用户名 `ab`（过短） | 400，`username: 用户名须为4-20位字母数字下划线` |
| 3 | `POST /register` 正常数据 | 200 |
| 4 | `POST /register` 重复用户名 | 400，`用户名已存在` |
| 5 | `POST /login` 错误密码 | 401，`用户名或密码错误` |
| 6 | `POST /login` admin/123456 | 200，返回 token + user（无密码字段） |
| 7 | `GET /me` 不带 token | 401，`未登录或token缺失` |
| 8 | `GET /me` 带伪造 token | 401，`token 无效或已过期` |
| 9 | `GET /me` 带合法 token | 200，返回 admin 信息 |
| 10 | 浏览器 `http://localhost:8080/swagger-ui.html` | 三接口可见，Authorize 可用 |
| 11 | 查库 `SELECT username FROM sys_user;` | admin + zhangsan |

`Ctrl+C` 停止。

- [ ] **Step 3: git 收尾核验**

```bash
git status
git log --oneline
```
Expected: 工作区干净（logs/、target/ 被忽略）；提交历史从设计文档到本计划共约 9 个提交。

- [ ] **Step 4: 向用户汇报**

汇报内容：E2E 清单逐项结果、测试统计、如何用 Swagger 调试、后续可选学习方向（Redis 刷新 token / user 模块扩展 / Spring Cloud 拆分）。

---

## 计划自审记录

1. **规格覆盖**：设计文档 §2 依赖（Task 1）、§4 数据库/Flyway（Task 2）、§6.3/6.4 响应与异常（Task 3）、§3.2 实体 Mapper（Task 4）、§5.1/5.2 JWT（Task 5）、§6.2 DTO 校验与 MapStruct（Task 6）、§5.3/5.4 安全链（Task 7）、§5.5 业务逻辑（Task 8）、§6.1 接口+§7.5 Swagger（Task 9）、§7.1/7.2/7.3 AOP+Logback+Profile（Task 10）、§8 测试与手动 E2E（各任务+Task 11）。Actuator（§7.4）在 Task 1 yml + Task 7/11 验证。——**全覆盖**
2. **占位符扫描**：唯一非确定内容为 V2 的 BCrypt 哈希（随机盐决定无法预生成），已通过 BCryptHashGeneratorTest 现场生成 + Task 11 E2E #6 登录闭环验证；其余步骤均含完整代码/命令/期望输出。
3. **类型一致性**：`R.ok/R.fail`、`BusinessException(code,message)`、`generateToken(Long,String[,long])`、`getUserId(Claims)`、`toVO(User)`、`me(Long userId)` 在测试与实现中签名一致；UserConvert 统一 Spring 注入（无 INSTANCE 用法）。
