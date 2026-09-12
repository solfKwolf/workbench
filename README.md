# Workbench — Spring Boot 用户认证学习项目

前端视角搭建的 Spring Boot 3 项目，核心功能为用户认证（注册 / 登录 / JWT 鉴权），目标是在一个小而全的项目里覆盖尽可能多的后端技术栈。后续可演进为 Spring Cloud 微服务架构。

## 技术栈

| 类别 | 技术 | 版本 | 用途 |
|---|---|---|---|
| 框架 | Spring Boot | 3.5.13 | 应用骨架 |
| 安全 | Spring Security + jjwt | 0.12.6 | 无状态 JWT 鉴权（HS384），BCrypt 密码哈希 |
| ORM | MyBatis-Plus | 3.5.17 | 自动 CRUD、下划线转驼峰 |
| 数据库 | PostgreSQL | 16 | 多时区支持（timestamptz + Instant） |
| 迁移 | Flyway | — | V1/V2/V3 版本化迁移，含种子数据 |
| 接口文档 | Springdoc OpenAPI | 2.8.17 | Swagger UI 自动生成 |
| 对象映射 | MapStruct | 1.6.3 | DTO 到 VO 编译期转换 |
| 切面 | Spring AOP | — | 请求日志（入参 / 出参 / 耗时） |
| 日志 | Logback | — | 控制台 + 按天滚动文件 |
| 监控 | Spring Boot Actuator | — | 健康检查 |
| 工具 | Lombok | — | 减少样板代码 |
| 测试 | JUnit 5 + Mockito + AssertJ | — | 单元测试 + 少量集成测试 |

## 项目结构

```
src/main/java/com/workbench/
  WorkbenchApplication.java          # 启动类 + JVM UTC + 启动 banner
  auth/                              # 认证模块
    config/                          # JWT 工具 / Security 过滤链 / JWT 过滤器
    controller/AuthController.java   # register / login / me
    convert/UserConvert.java         # MapStruct User -> UserVO
    dto/                             # 请求 / 响应结构
    entity/User.java                 # 数据库实体
    mapper/UserMapper.java           # MyBatis-Plus Mapper
    service/AuthService.java         # 业务逻辑
  common/                            # 通用组件
    aspect/RequestLogAspect.java     # AOP 请求日志
    config/                          # MyBatis-Plus / OpenAPI 配置
    exception/                       # 业务异常 + 全局异常处理器
    result/R.java                    # 统一响应封装

src/main/resources/
  application.yml                    # 公共配置（激活 dev）
  application-dev.yml                # 开发环境
  application-prod.yml               # 生产环境（环境变量注入 / 关闭 Swagger）
  logback-spring.xml                 # 日志配置
  db/migration/                      # Flyway 迁移脚本
    V1__init_schema.sql              # sys_user 表
    V2__seed_admin.sql               # admin 种子用户
    V3__multi_timezone.sql           # 多时区改造（timestamptz + timezone 列）
```

## API 速览

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| POST | /api/auth/register | 否 | 注册（可选 timezone 参数） |
| POST | /api/auth/login | 否 | 登录，返回 JWT + 用户信息 |
| GET  | /api/auth/me | 是 | 当前登录用户信息 |

统一响应格式：

```json
{ "code": 200, "message": "success", "data": { ... } }
```

## 快速开始

### 环境要求

- JDK 17
- Maven 3.9+
- PostgreSQL 15+（本机默认 localhost:5432/workbench）

### 数据库准备

```sql
CREATE DATABASE workbench;
```

密码默认 123456，如需修改编辑 application-dev.yml 的 spring.datasource.password。

### 启动

```bash
# 开发模式
mvn spring-boot:run

# 生产模式（需先设置环境变量 DB_PASSWORD / JWT_SECRET）
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

启动完成后日志自动打印可复制地址：

```
  服务已启动: http://localhost:8090
  API 文档:   http://localhost:8090/swagger-ui/index.html
  OpenAPI:    http://localhost:8090/v3/api-docs
```

Swagger UI 右上角 Authorize 输入 Bearer <token> 即可调试鉴权接口。

### 测试

```bash
mvn clean test   # 19 个测试
```

### 默认账号

| 用户名 | 密码 | 说明 |
|---|---|---|
| admin | 123456 | 种子用户（Flyway V2 插入） |

## 多时区设计要点

- 存储层：时间列用 PostgreSQL timestamptz，Java 实体用 java.time.Instant（UTC 绝对时刻）
- 传输层：Jackson 自动序列化为 2026-09-12T20:00:00Z，前端可直接 new Date(str) 解析
- 时区字段：用户表存 IANA 时区 ID（如 Asia/Shanghai），仅用于前端渲染换算
- 历史数据迁移：V3 显式用 AT TIME ZONE 解读旧数据，避免依赖会话时区
- JVM 默认时区：启动类固定 UTC，日志时间戳、JDBC 会话不随部署环境漂移

判定口诀：换算到另一时区还有意义 → 存 UTC；天生属于某个地点 → 存本地钟面 + IANA 时区 ID。

## 生产部署注意

- application-prod.yml 要求环境变量 DB_PASSWORD 和 JWT_SECRET，不设置则拒绝启动
- SpringDoc 文档端点默认关闭（springdoc.api-docs.enabled=false）
- SQL 日志默认关闭（NoLoggingImpl）
- JWT 密钥须 >= 256 bit，建议用 openssl rand -base64 48 生成

## 开发规范

- 统一响应：Controller 返回 R<T>，业务异常抛 BusinessException(code, message)
- 密码永不回传：UserVO 结构上没有 password 字段，类型层面杜绝泄露
- MapStruct：Lombok Binding 顺序必须是 lombok -> binding -> mapstruct-processor，否则生成类看不到 Lombok 字段
- 异常处理器：具体异常写前面，兜底 Exception.class 写最后
- VSCode：.vscode/settings.json 已含 target/generated-sources/annotations；如 MapStruct 红线，执行 Java: Clean Java Language Server Workspace
