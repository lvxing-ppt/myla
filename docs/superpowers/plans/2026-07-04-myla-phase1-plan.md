# MYLA 同类平台一期 MVP — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付一期 MVP —— 打通"仪器 → 中间件 → LIS"完整数据链路，包含 VITEK 2 适配器、样本全流程管理、结果审核、基础报告和 RBAC 权限。

**Architecture:** Spring Boot 3.x 模块化单体（Maven 多模块），Vue 3 前端，MySQL + Redis + RabbitMQ，仪器层 SPI 插件框架（Channel→Splitter→Parser 三层解耦），自有仪器通过 ProprietaryProtocolDriver 接入。

**Tech Stack:** Java 17, Spring Boot 3.x, MyBatis-Plus, MySQL 8.0, Redis, RabbitMQ 3.x, Vue 3, Element Plus, Maven

**Spec:** `docs/superpowers/specs/2026-07-04-myla-platform-design.md`

---

## 文件结构总览

```
myla/
├── pom.xml                                    # 根 POM (多模块)
├── myla-common/
│   ├── pom.xml
│   ├── myla-common-core/
│   │   └── src/main/java/com/myla/common/core/
│   │       ├── exception/                    # 业务异常体系
│   │       ├── constant/                     # 常量、枚举
│   │       └── util/                         # 通用工具
│   ├── myla-common-security/
│   │   └── src/main/java/com/myla/common/security/
│   │       ├── annotation/                   # @AuditLog, @DataScope
│   │       ├── aspect/                       # AOP 切面
│   │       └── config/                       # 自动配置
│   └── myla-common-api/
│       └── src/main/java/com/myla/common/api/
│           ├── dto/                          # 统一 DTO
│           ├── event/                        # 领域事件定义
│           └── enums/                        # 共享枚举
│
├── myla-platform/
│   ├── myla-platform-gateway/
│   │   ├── gateway-core/                     # SPI框架、InstrumentHub
│   │   ├── gateway-channel/                  # TcpChannel, FileChannel
│   │   ├── gateway-splitter/                 # AstmSplitter, Hl7Splitter
│   │   ├── gateway-protocol/                 # 自有协议SDK
│   │   ├── gateway-device-mgmt/              # 设备管理
│   │   └── gateway-drivers/
│   │       ├── driver-vitek2/                # VITEK 2 适配器
│   │       └── driver-proprietary/           # 自有仪器通用驱动
│   ├── myla-platform-sample/                 # 样本管理
│   ├── myla-platform-result/                 # 结果管理
│   ├── myla-platform-workflow/               # 工作流引擎
│   ├── myla-platform-lis/                    # LIS网关
│   ├── myla-platform-report/                 # 报告引擎
│   ├── myla-platform-notification/           # 消息通知
│   └── myla-platform-admin/                  # 系统管理(RBAC)
│
├── myla-server/                              # Spring Boot 启动模块
│   └── src/main/
│       ├── java/com/myla/server/
│       │   └── MylaApplication.java
│       └── resources/
│           ├── application.yml
│           ├── application-dev.yml
│           └── application-prod.yml
│
├── myla-web/                                 # 前端 (Vue 3)
│   └── src/
│       ├── views/{dashboard,sample,result,report,system}/
│       ├── api/
│       ├── router/
│       └── store/
│
└── myla-deploy/
    ├── docker/docker-compose.yml
    ├── sql/V1__init_schema.sql
    └── scripts/install.sh
```

---

## Task 1: 项目脚手架 — Maven 多模块 + Spring Boot

**Files:**
- Create: `pom.xml` (根)
- Create: `myla-common/pom.xml`, `myla-common/myla-common-core/pom.xml`
- Create: `myla-common/myla-common-security/pom.xml`
- Create: `myla-common/myla-common-api/pom.xml`
- Create: `myla-platform/pom.xml`
- Create: 各 `myla-platform/myla-platform-*/pom.xml`（10个子模块）
- Create: `myla-server/pom.xml`, `myla-server/src/main/java/com/myla/server/MylaApplication.java`
- Create: `myla-server/src/main/resources/application.yml`
- Create: `myla-server/src/main/resources/application-dev.yml`
- Create: `myla-deploy/docker/docker-compose.yml`
- Create: `myla-deploy/sql/V1__init_schema.sql`

- [ ] **Step 1: 创建根 POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.myla</groupId>
    <artifactId>myla</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>
    <name>MYLA Platform</name>

    <modules>
        <module>myla-common</module>
        <module>myla-platform</module>
        <module>myla-server</module>
    </modules>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.1</version>
    </parent>

    <properties>
        <java.version>17</java.version>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <mybatis-plus.version>3.5.5</mybatis-plus.version>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>com.myla</groupId>
                <artifactId>myla-common-core</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.myla</groupId>
                <artifactId>myla-common-api</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.myla</groupId>
                <artifactId>myla-common-security</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.baomidou</groupId>
                <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
                <version>${mybatis-plus.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

- [ ] **Step 2: 创建 myla-common 聚合 POM**

```xml
<!-- myla-common/pom.xml -->
<project xmlns="http://maven.apache.org/POM/4.0.0" ...>
    <modelVersion>4.0.0</modelVersion>
    <parent><groupId>com.myla</groupId><artifactId>myla</artifactId><version>1.0.0-SNAPSHOT</version></parent>
    <artifactId>myla-common</artifactId>
    <packaging>pom</packaging>
    <modules>
        <module>myla-common-core</module>
        <module>myla-common-api</module>
        <module>myla-common-security</module>
    </modules>
</project>
```

- [ ] **Step 3: 创建 myla-common-core POM**

```xml
<!-- myla-common/myla-common-core/pom.xml -->
<project xmlns="http://maven.apache.org/POM/4.0.0" ...>
    <modelVersion>4.0.0</modelVersion>
    <parent><groupId>com.myla</groupId><artifactId>myla-common</artifactId><version>1.0.0-SNAPSHOT</version></parent>
    <artifactId>myla-common-core</artifactId>
    <dependencies>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter</artifactId></dependency>
        <dependency><groupId>com.fasterxml.jackson.core</groupId><artifactId>jackson-databind</artifactId></dependency>
    </dependencies>
</project>
```

- [ ] **Step 4: 创建 myla-common-api POM**

```xml
<!-- myla-common/myla-common-api/pom.xml -->
<project xmlns="http://maven.apache.org/POM/4.0.0" ...>
    <modelVersion>4.0.0</modelVersion>
    <parent><groupId>com.myla</groupId><artifactId>myla-common</artifactId><version>1.0.0-SNAPSHOT</version></parent>
    <artifactId>myla-common-api</artifactId>
    <dependencies>
        <dependency><groupId>com.myla</groupId><artifactId>myla-common-core</artifactId></dependency>
    </dependencies>
</project>
```

- [ ] **Step 5: 创建 myla-common-security POM**

```xml
<!-- myla-common/myla-common-security/pom.xml -->
<project xmlns="http://maven.apache.org/POM/4.0.0" ...>
    <modelVersion>4.0.0</modelVersion>
    <parent><groupId>com.myla</groupId><artifactId>myla-common</artifactId><version>1.0.0-SNAPSHOT</version></parent>
    <artifactId>myla-common-security</artifactId>
    <dependencies>
        <dependency><groupId>com.myla</groupId><artifactId>myla-common-core</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-aop</artifactId></dependency>
    </dependencies>
</project>
```

- [ ] **Step 6: 创建 myla-platform 聚合 POM 及所有子模块 POM**

```xml
<!-- myla-platform/pom.xml -->
<project xmlns="http://maven.apache.org/POM/4.0.0" ...>
    <modelVersion>4.0.0</modelVersion>
    <parent><groupId>com.myla</groupId><artifactId>myla</artifactId><version>1.0.0-SNAPSHOT</version></parent>
    <artifactId>myla-platform</artifactId>
    <packaging>pom</packaging>
    <modules>
        <module>myla-platform-gateway</module>
        <module>myla-platform-sample</module>
        <module>myla-platform-result</module>
        <module>myla-platform-workflow</module>
        <module>myla-platform-lis</module>
        <module>myla-platform-report</module>
        <module>myla-platform-notification</module>
        <module>myla-platform-admin</module>
    </modules>
</project>
```

平台子模块 POM 模板（每个 `myla-platform-*/pom.xml` 均类似）：

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0" ...>
    <modelVersion>4.0.0</modelVersion>
    <parent><groupId>com.myla</groupId><artifactId>myla-platform</artifactId><version>1.0.0-SNAPSHOT</version></parent>
    <artifactId>myla-platform-sample</artifactId> <!-- 按模块替换 -->
    <dependencies>
        <dependency><groupId>com.myla</groupId><artifactId>myla-common-api</artifactId></dependency>
        <dependency><groupId>com.myla</groupId><artifactId>myla-common-security</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
        <dependency><groupId>com.baomidou</groupId><artifactId>mybatis-plus-spring-boot3-starter</artifactId></dependency>
    </dependencies>
</project>
```

Gateway 聚合 POM（`myla-platform/myla-platform-gateway/pom.xml`）需额外包含子模块：

```xml
<modules>
    <module>gateway-core</module>
    <module>gateway-channel</module>
    <module>gateway-splitter</module>
    <module>gateway-protocol</module>
    <module>gateway-device-mgmt</module>
    <module>gateway-drivers</module>
</modules>
```

- [ ] **Step 7: 创建 myla-server 模块**

```xml
<!-- myla-server/pom.xml -->
<project xmlns="http://maven.apache.org/POM/4.0.0" ...>
    <modelVersion>4.0.0</modelVersion>
    <parent><groupId>com.myla</groupId><artifactId>myla</artifactId><version>1.0.0-SNAPSHOT</version></parent>
    <artifactId>myla-server</artifactId>
    <dependencies>
        <dependency><groupId>com.myla</groupId><artifactId>myla-platform-gateway</artifactId><version>${project.version}</version></dependency>
        <dependency><groupId>com.myla</groupId><artifactId>myla-platform-sample</artifactId><version>${project.version}</version></dependency>
        <dependency><groupId>com.myla</groupId><artifactId>myla-platform-result</artifactId><version>${project.version}</version></dependency>
        <dependency><groupId>com.myla</groupId><artifactId>myla-platform-workflow</artifactId><version>${project.version}</version></dependency>
        <dependency><groupId>com.myla</groupId><artifactId>myla-platform-lis</artifactId><version>${project.version}</version></dependency>
        <dependency><groupId>com.myla</groupId><artifactId>myla-platform-report</artifactId><version>${project.version}</version></dependency>
        <dependency><groupId>com.myla</groupId><artifactId>myla-platform-notification</artifactId><version>${project.version}</version></dependency>
        <dependency><groupId>com.myla</groupId><artifactId>myla-platform-admin</artifactId><version>${project.version}</version></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-amqp</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-redis</artifactId></dependency>
    </dependencies>
    <build><plugins><plugin><groupId>org.springframework.boot</groupId><artifactId>spring-boot-maven-plugin</artifactId></plugin></plugins></build>
</project>
```

- [ ] **Step 8: 创建 Spring Boot 启动类**

```java
// myla-server/src/main/java/com/myla/server/MylaApplication.java
package com.myla.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.myla")
public class MylaApplication {
    public static void main(String[] args) {
        SpringApplication.run(MylaApplication.class, args);
    }
}
```

- [ ] **Step 9: 创建 application.yml（开发环境）**

```yaml
# myla-server/src/main/resources/application.yml
spring:
  profiles:
    active: dev
  application:
    name: myla-platform

server:
  port: 8080

mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
  global-config:
    db-config:
      id-type: auto
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0

myla:
  gateway:
    driver-dir: ./drivers
  security:
    jwt-secret: ${JWT_SECRET:change-me-in-production}
    jwt-expiration: 7200
```

```yaml
# myla-server/src/main/resources/application-dev.yml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/myla?useUnicode=true&characterEncoding=utf8mb4
    username: root
    password: root
    driver-class-name: com.mysql.cj.jdbc.Driver
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
    publisher-confirm-type: correlated
    listener:
      simple:
        acknowledge-mode: manual
        retry:
          enabled: true
          max-attempts: 3
  redis:
    host: localhost
    port: 6379

logging:
  level:
    com.myla: DEBUG
```

- [ ] **Step 10: 创建 Docker Compose 开发环境**

```yaml
# myla-deploy/docker/docker-compose.yml
version: '3.8'
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: myla
      MYSQL_CHARSET: utf8mb4
      MYSQL_COLLATION: utf8mb4_unicode_ci
    ports:
      - "3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql
      - ../sql:/docker-entrypoint-initdb.d

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  rabbitmq:
    image: rabbitmq:3-management-alpine
    ports:
      - "5672:5672"
      - "15672:15672"
    environment:
      RABBITMQ_DEFAULT_USER: guest
      RABBITMQ_DEFAULT_PASS: guest

volumes:
  mysql-data:
```

- [ ] **Step 11: 创建空 SQL 初始化脚本**

```sql
-- myla-deploy/sql/V1__init_schema.sql
-- 一期 MVP 数据库初始化脚本
-- 具体建表语句在后续 Task 中逐步补充

CREATE DATABASE IF NOT EXISTS myla DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE myla;
```

- [ ] **Step 12: 编译验证整个项目**

Run: `cd g:/myla && mvn compile -q`
Expected: BUILD SUCCESS, 所有模块编译通过（均为空包，无实际类）

- [ ] **Step 13: Commit**

```bash
git add -A
git commit -m "feat: scaffold Maven multi-module project with Spring Boot

- Root POM with dependency management (Spring Boot 3.2, MyBatis-Plus 3.5, JDK 17)
- myla-common: core, api, security submodules
- myla-platform: gateway, sample, result, workflow, lis, report, notification, admin
- myla-server: Spring Boot bootstrap with application.yml
- Docker Compose for MySQL + Redis + RabbitMQ dev environment

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 2: 公共模块 — 异常体系、常量、工具类

**Files:**
- Create: `myla-common/myla-common-core/src/main/java/com/myla/common/core/exception/BusinessException.java`
- Create: `myla-common/myla-common-core/src/main/java/com/myla/common/core/exception/InstrumentException.java`
- Create: `myla-common/myla-common-core/src/main/java/com/myla/common/core/exception/ParseException.java`
- Create: `myla-common/myla-common-core/src/main/java/com/myla/common/core/exception/GlobalExceptionHandler.java`
- Create: `myla-common/myla-common-core/src/main/java/com/myla/common/core/constant/ResultCode.java`
- Create: `myla-common/myla-common-core/src/main/java/com/myla/common/core/util/R.java` (统一响应体)
- Create: `myla-common/myla-common-api/src/main/java/com/myla/common/api/enums/ResultType.java`
- Create: `myla-common/myla-common-api/src/main/java/com/myla/common/api/enums/SampleStatus.java`
- Create: `myla-common/myla-common-api/src/main/java/com/myla/common/api/enums/CommunicationMode.java`
- Create: `myla-common/myla-common-api/src/main/java/com/myla/common/api/dto/UnifiedResult.java`
- Create: `myla-common/myla-common-api/src/main/java/com/myla/common/api/dto/AstResultDTO.java`

- [ ] **Step 1: 创建业务异常基类**

```java
// myla-common-core/src/main/java/com/myla/common/core/exception/BusinessException.java
package com.myla.common.core.exception;

import com.myla.common.core.constant.ResultCode;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final int code;
    private final String message;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
        this.message = resultCode.getMessage();
    }

    public BusinessException(ResultCode resultCode, String detail) {
        super(detail);
        this.code = resultCode.getCode();
        this.message = detail;
    }
}
```

- [ ] **Step 2: 创建仪器异常**

```java
// myla-common-core/src/main/java/com/myla/common/core/exception/InstrumentException.java
package com.myla.common.core.exception;

public class InstrumentException extends BusinessException {
    private final String instrumentId;
    private final int consecutiveFailures;

    public InstrumentException(String instrumentId, String message, int consecutiveFailures) {
        super(ResultCode.INSTRUMENT_ERROR, message);
        this.instrumentId = instrumentId;
        this.consecutiveFailures = consecutiveFailures;
    }
}
```

- [ ] **Step 3: 创建解析异常**

```java
// myla-common-core/src/main/java/com/myla/common/core/exception/ParseException.java
package com.myla.common.core.exception;

public class ParseException extends BusinessException {
    private final String rawText;

    public ParseException(String rawText, String errorDetail) {
        super(ResultCode.PARSE_ERROR, errorDetail);
        this.rawText = rawText;
    }
}
```

- [ ] **Step 4: 创建 ResultCode 枚举**

```java
// myla-common-core/src/main/java/com/myla/common/core/constant/ResultCode.java
package com.myla.common.core.constant;

import lombok.Getter;

@Getter
public enum ResultCode {
    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "系统内部错误"),

    // 仪器相关
    INSTRUMENT_CONNECTION_ERROR(1001, "仪器连接异常"),
    INSTRUMENT_ERROR(1002, "仪器错误"),
    PARSE_ERROR(1003, "数据解析失败"),
    DRIVER_LOAD_ERROR(1004, "驱动加载失败"),

    // 业务相关
    SAMPLE_NOT_FOUND(2001, "样本不存在"),
    RESULT_NOT_FOUND(2002, "结果不存在"),
    DUPLICATE_BARCODE(2003, "条码重复"),
    INVALID_SAMPLE_STATUS(2004, "样本状态异常不允许此操作"),

    // LIS相关
    LIS_SEND_FAILED(3001, "LIS发送失败"),
    LIS_ORDER_PARSE_ERROR(3002, "LIS医嘱解析失败");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
```

- [ ] **Step 5: 创建统一响应体 R**

```java
// myla-common-core/src/main/java/com/myla/common/core/util/R.java
package com.myla.common.core.util;

import com.myla.common.core.constant.ResultCode;
import lombok.Data;

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

    public static <T> R<T> ok(T data) { return new R<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data); }
    public static <T> R<T> ok() { return ok(null); }
    public static <T> R<T> fail(ResultCode code) { return new R<>(code.getCode(), code.getMessage(), null); }
    public static <T> R<T> fail(ResultCode code, String detail) { return new R<>(code.getCode(), detail, null); }
}
```

- [ ] **Step 6: 创建全局异常处理器**

```java
// myla-common-core/src/main/java/com/myla/common/core/exception/GlobalExceptionHandler.java
package com.myla.common.core.exception;

import com.myla.common.core.constant.ResultCode;
import com.myla.common.core.util.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public R<Void> handleBusinessException(BusinessException e) {
        log.warn("Business exception: code={}, message={}", e.getCode(), e.getMessage());
        return R.fail(ResultCode.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(ParseException.class)
    public R<Void> handleParseException(ParseException e) {
        log.error("Parse error: {}", e.getMessage());
        return R.fail(ResultCode.PARSE_ERROR, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e) {
        log.error("Unexpected error", e);
        return R.fail(ResultCode.INTERNAL_ERROR);
    }
}
```

- [ ] **Step 7: 创建共享枚举**

```java
// myla-common-api/src/main/java/com/myla/common/api/enums/SampleStatus.java
package com.myla.common.api.enums;

public enum SampleStatus {
    REGISTERED("已登记"),
    INOCULATED("已接种"),
    INCUBATING("培养中"),
    PENDING_REVIEW("待审核"),
    APPROVED("已审核"),
    REJECTED("已退回"),
    RELEASED("已发布");

    private final String label;
    SampleStatus(String label) { this.label = label; }
    public String getLabel() { return label; }
}
```

```java
// myla-common-api/src/main/java/com/myla/common/api/enums/ResultType.java
package com.myla.common.api.enums;

public enum ResultType {
    BLOOD_CULTURE_FLAG,  // 血培养报阳/阴
    ORGANISM_ID,         // 菌种鉴定
    AST,                 // 药敏结果
    QC                   // 质控
}
```

```java
// myla-common-api/src/main/java/com/myla/common/api/enums/CommunicationMode.java
package com.myla.common.api.enums;

public enum CommunicationMode {
    PASSIVE_LISTEN,  // 中间件开端口等待仪器连接
    ACTIVE_POLL,     // 中间件定时轮询文件/目录
    ACTIVE_CONNECT   // 中间件主动TCP连接仪器
}
```

- [ ] **Step 8: 创建共享 DTO**

```java
// myla-common-api/src/main/java/com/myla/common/api/dto/UnifiedResult.java
package com.myla.common.api.dto;

import com.myla.common.api.enums.ResultType;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class UnifiedResult {
    private String instrumentId;
    private String sampleBarcode;
    private String patientId;
    private String patientName;
    private String caseId;
    private ResultType resultType;
    private String organismCode;
    private String organismName;
    private Double identificationPercent;
    private List<AstResultDTO> astResults;
    private LocalDateTime testTime;
    private String rawMessage;
}
```

```java
// myla-common-api/src/main/java/com/myla/common/api/dto/AstResultDTO.java
package com.myla.common.api.dto;

import lombok.Data;

@Data
public class AstResultDTO {
    private String antibioticCode;
    private String antibioticName;
    private Double micValue;
    private String micUnit;
    private String sirInterpretation;  // S / I / R
    private String machineSIR;         // 仪器原始判定
    private String manualSIR;          // 人工修正后判定
    private String expertRuleComment;  // 专家规则备注
}
```

- [ ] **Step 9: 创建领域事件定义**

```java
// myla-common-api/src/main/java/com/myla/common/api/event/LabEvent.java
package com.myla.common.api.event;

public enum LabEvent {
    SAMPLE_REGISTERED,
    SAMPLE_RECEIVED,
    CULTURE_POSITIVE,
    CULTURE_NEGATIVE,
    ORGANISM_IDENTIFIED,
    AST_RESULT_RECEIVED,
    RESULT_APPROVED,
    RESULT_RELEASED_TO_LIS,
    CRITICAL_VALUE_DETECTED,
    TAT_THRESHOLD_EXCEEDED,
    SAMPLE_MISMATCH,
    QC_OUT_OF_RANGE
}
```

- [ ] **Step 10: 编译验证**

Run: `cd g:/myla && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 11: Commit**

```bash
git add -A
git commit -m "feat: add common modules - exceptions, constants, DTOs, enums

- BusinessException hierarchy (Business, Instrument, Parse)
- GlobalExceptionHandler with unified R<T> response
- Shared enums: SampleStatus, ResultType, CommunicationMode
- Shared DTOs: UnifiedResult, AstResultDTO
- Domain events: LabEvent enum

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 3: 数据库 Schema — 核心业务表

**Files:**
- Modify: `myla-deploy/sql/V1__init_schema.sql`
- Create: `myla-server/src/main/resources/mapper/` (预留 mapper XML 目录)

- [ ] **Step 1: 编写完整建表 SQL**

```sql
-- myla-deploy/sql/V1__init_schema.sql
CREATE DATABASE IF NOT EXISTS myla DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE myla;

-- ==================== 字典/码表 ====================

CREATE TABLE hospital (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    hospital_code VARCHAR(32) NOT NULL UNIQUE COMMENT '院区代码',
    hospital_name VARCHAR(128) NOT NULL COMMENT '医院名称',
    address VARCHAR(256),
    contact_phone VARCHAR(32),
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='院区';

CREATE TABLE organism_dict (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    organism_code VARCHAR(16) NOT NULL UNIQUE COMMENT '内部菌种编码',
    organism_name VARCHAR(128) NOT NULL COMMENT '菌种名称',
    whonet_code VARCHAR(16) COMMENT 'WHONET编码',
    snomed_code VARCHAR(16) COMMENT 'SNOMED编码',
    gram_stain VARCHAR(8) COMMENT '革兰氏染色:POS/NEG',
    category VARCHAR(32) COMMENT '分类:肠杆菌/非发酵菌/链球菌...',
    is_multidrug_candidate TINYINT NOT NULL DEFAULT 0 COMMENT '是否MDRO候选菌',
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='菌种字典';

CREATE TABLE antibiotic_dict (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    antibiotic_code VARCHAR(16) NOT NULL UNIQUE COMMENT '内部抗生素编码',
    antibiotic_name VARCHAR(64) NOT NULL COMMENT '抗生素名称',
    whonet_code VARCHAR(16) COMMENT 'WHONET编码',
    antibiotic_class VARCHAR(32) COMMENT '抗生素大类',
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='抗生素字典';

CREATE TABLE specimen_dict (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    specimen_code VARCHAR(16) NOT NULL UNIQUE COMMENT '标本类型编码',
    specimen_name VARCHAR(64) NOT NULL COMMENT '标本名称',
    is_sterile_site TINYINT NOT NULL DEFAULT 0 COMMENT '是否无菌部位标本',
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='标本类型字典';

-- ==================== 权限/用户 ====================

CREATE TABLE sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(256) NOT NULL,
    real_name VARCHAR(64),
    mobile VARCHAR(20),
    email VARCHAR(128),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
    hospital_id BIGINT,
    last_login_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0
) ENGINE=InnoDB COMMENT='系统用户';

CREATE TABLE sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_code VARCHAR(32) NOT NULL UNIQUE,
    role_name VARCHAR(64) NOT NULL,
    hospital_id BIGINT COMMENT 'NULL表示系统级角色',
    description VARCHAR(256),
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='角色';

CREATE TABLE sys_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    perm_code VARCHAR(64) NOT NULL UNIQUE COMMENT 'result:review, sample:create...',
    perm_name VARCHAR(64) NOT NULL,
    resource VARCHAR(64) COMMENT '资源类型',
    action VARCHAR(32) COMMENT 'CRUD操作',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='权限';

CREATE TABLE sys_user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    UNIQUE KEY uk_user_role (user_id, role_id)
) ENGINE=InnoDB COMMENT='用户-角色';

CREATE TABLE sys_role_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    perm_id BIGINT NOT NULL,
    UNIQUE KEY uk_role_perm (role_id, perm_id)
) ENGINE=InnoDB COMMENT='角色-权限';

-- ==================== 样本 ====================

CREATE TABLE sample (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sample_id VARCHAR(32) NOT NULL UNIQUE COMMENT '实验室内部编号(yyyyMMdd-xxxx)',
    barcode VARCHAR(64) COMMENT '样本条码',
    patient_id VARCHAR(32) COMMENT '患者ID(来自LIS)',
    patient_name VARCHAR(64) COMMENT '患者姓名',
    gender VARCHAR(4) COMMENT '性别',
    age INT COMMENT '年龄',
    specimen_type VARCHAR(32) COMMENT '标本类型编码',
    collect_time DATETIME COMMENT '采集时间',
    receive_time DATETIME COMMENT '签收时间',
    status VARCHAR(20) NOT NULL DEFAULT 'REGISTERED' COMMENT '样本状态',
    priority VARCHAR(10) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/URGENT',
    ward_code VARCHAR(32) COMMENT '病区',
    ward_name VARCHAR(64) COMMENT '病区名称',
    diagnosis VARCHAR(256) COMMENT '临床诊断',
    source_system VARCHAR(32) COMMENT '来源LIS名称',
    comment TEXT COMMENT '备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    INDEX idx_sample_id (sample_id),
    INDEX idx_barcode (barcode),
    INDEX idx_patient_id (patient_id),
    INDEX idx_status (status),
    INDEX idx_receive_time (receive_time),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB COMMENT='样本';

CREATE TABLE sample_test (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sample_id BIGINT NOT NULL COMMENT '关联sample.id',
    test_code VARCHAR(32) COMMENT '检验项目代码',
    test_name VARCHAR(64) COMMENT '检验项目名称',
    instrument_id VARCHAR(32) COMMENT '执行仪器编号',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/IN_PROGRESS/COMPLETED',
    started_at DATETIME,
    completed_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_sample (sample_id),
    INDEX idx_instrument (instrument_id)
) ENGINE=InnoDB COMMENT='样本检验明细';

CREATE TABLE sample_tracking (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sample_id BIGINT NOT NULL COMMENT '关联sample.id',
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    operator VARCHAR(64) COMMENT '操作人',
    comment VARCHAR(256),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_sample (sample_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB COMMENT='样本流转日志';

-- ==================== 结果 ====================

CREATE TABLE organism_result (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    result_id VARCHAR(32) NOT NULL UNIQUE COMMENT '结果唯一编号',
    sample_id BIGINT NOT NULL COMMENT '关联sample.id',
    instrument_id VARCHAR(32) COMMENT '检测仪器',
    organism_code VARCHAR(16) COMMENT '菌种编码',
    organism_name VARCHAR(128) COMMENT '菌种名称',
    identification_percent DECIMAL(5,2) COMMENT '鉴定置信度%',
    result_type VARCHAR(20) NOT NULL COMMENT 'ORGANISM_ID/AST/BLOOD_CULTURE_FLAG',
    test_time DATETIME COMMENT '检测时间',
    review_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/RELEASED',
    reviewed_by VARCHAR(64),
    reviewed_at DATETIME,
    raw_message TEXT COMMENT '原始报文(仅参考,完整报文在raw_message表)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_result_id (result_id),
    INDEX idx_sample (sample_id),
    INDEX idx_review_status (review_status),
    INDEX idx_organism (organism_code)
) ENGINE=InnoDB COMMENT='菌种鉴定/检测结果';

CREATE TABLE ast_result (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    organism_result_id BIGINT NOT NULL COMMENT '关联organism_result.id',
    antibiotic_code VARCHAR(16) COMMENT '抗生素编码',
    antibiotic_name VARCHAR(64) COMMENT '抗生素名称',
    mic_value DECIMAL(10,4) COMMENT 'MIC值',
    mic_unit VARCHAR(8) COMMENT '单位:ug/mL, mg/L',
    machine_sir VARCHAR(4) COMMENT '仪器原始SIR判定',
    manual_sir VARCHAR(4) COMMENT '人工修正SIR',
    final_sir VARCHAR(4) COMMENT '最终SIR(仪器或人工)',
    expert_rule_comment VARCHAR(256) COMMENT '专家规则备注',
    is_corrected TINYINT NOT NULL DEFAULT 0 COMMENT '是否人工修正',
    corrected_by VARCHAR(64),
    corrected_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_org_result (organism_result_id),
    INDEX idx_antibiotic (antibiotic_code)
) ENGINE=InnoDB COMMENT='药敏结果';

CREATE TABLE raw_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    instrument_id VARCHAR(32) NOT NULL COMMENT '仪器编号',
    message_direction VARCHAR(10) NOT NULL COMMENT 'IN/OUT',
    message_type VARCHAR(20) COMMENT 'ASTM/HL7/PROPRIETARY',
    raw_content TEXT NOT NULL COMMENT '原始报文',
    parse_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PARSED/PARSE_FAILED',
    parse_error TEXT COMMENT '解析错误信息',
    received_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_instrument_time (instrument_id, received_at),
    INDEX idx_parse_status (parse_status)
) ENGINE=InnoDB COMMENT='仪器原始报文存档';

-- ==================== 工作流/危急值 ====================

CREATE TABLE workflow_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_id VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL COMMENT '规则名称',
    trigger_event VARCHAR(64) NOT NULL COMMENT '触发事件(LabEvent)',
    condition_expr VARCHAR(512) COMMENT 'MVEL条件表达式',
    actions JSON COMMENT '动作列表',
    priority INT NOT NULL DEFAULT 0,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='工作流规则';

CREATE TABLE critical_value_alert (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    organism_result_id BIGINT NOT NULL COMMENT '关联结果',
    organism_name VARCHAR(128) COMMENT '菌种名称',
    alert_reason VARCHAR(64) COMMENT '危急原因',
    alert_level VARCHAR(10) NOT NULL DEFAULT 'CRITICAL' COMMENT 'CRITICAL/WARNING',
    notify_methods VARCHAR(128) COMMENT 'SMS,EMAIL,ONSCREEN',
    notify_targets TEXT COMMENT '通知对象列表JSON',
    notify_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENT/FAILED/CONFIRMED',
    confirm_time DATETIME,
    confirm_by VARCHAR(64),
    escalate_count INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_result (organism_result_id),
    INDEX idx_status (notify_status)
) ENGINE=InnoDB COMMENT='危急值告警';

-- ==================== LIS通信 ====================

CREATE TABLE lis_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    hospital_code VARCHAR(32) NOT NULL COMMENT '院区代码',
    channel_type VARCHAR(32) NOT NULL COMMENT 'HL7/ASTM/HTTP/FILE',
    channel_config JSON COMMENT '通信参数(IP/端口/目录)',
    order_mapping JSON COMMENT 'LIS→内部 字段映射',
    test_code_map JSON COMMENT 'LIS项目代码→内部代码',
    result_mapping JSON COMMENT '内部→LIS 字段映射',
    organism_code_map JSON COMMENT '菌种编码映射',
    antibiotic_code_map JSON COMMENT '抗生素编码映射',
    retry_policy JSON COMMENT '重试策略配置',
    ack_timeout_sec INT NOT NULL DEFAULT 30,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_hospital (hospital_code)
) ENGINE=InnoDB COMMENT='LIS配置';

CREATE TABLE outbound_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id VARCHAR(64) NOT NULL UNIQUE COMMENT '消息唯一ID',
    hospital_code VARCHAR(32) NOT NULL COMMENT '目标院区',
    message_type VARCHAR(20) NOT NULL COMMENT 'RESULT/ACK/STATUS_QUERY',
    message_content TEXT NOT NULL COMMENT '消息体',
    send_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENDING/SENT/FAILED/DEAD',
    retry_count INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL DEFAULT 3,
    last_error TEXT,
    next_retry_at DATETIME,
    sent_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_status (send_status),
    INDEX idx_hospital (hospital_code),
    INDEX idx_next_retry (next_retry_at)
) ENGINE=InnoDB COMMENT='LIS外发消息';

-- ==================== 报告 ====================

CREATE TABLE report_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_code VARCHAR(32) NOT NULL UNIQUE,
    template_name VARCHAR(64) NOT NULL COMMENT '报告模板名称',
    template_type VARCHAR(20) NOT NULL COMMENT 'JASPER/EXCEL',
    template_path VARCHAR(256) COMMENT '模板文件路径',
    output_format VARCHAR(20) NOT NULL DEFAULT 'PDF' COMMENT 'PDF/EXCEL',
    parameters JSON COMMENT '默认参数',
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='报告模板';

CREATE TABLE report_schedule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    schedule_name VARCHAR(64) NOT NULL,
    template_code VARCHAR(32) NOT NULL,
    cron_expr VARCHAR(32) NOT NULL COMMENT 'Quartz cron表达式',
    recipients TEXT COMMENT '接收人列表JSON',
    notify_method VARCHAR(20) NOT NULL DEFAULT 'EMAIL' COMMENT 'EMAIL/SMS',
    enabled TINYINT NOT NULL DEFAULT 1,
    last_run_at DATETIME,
    next_run_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='定时报告调度';

-- ==================== 仪器/设备管理 ====================

CREATE TABLE instrument_registry (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    instrument_id VARCHAR(32) NOT NULL UNIQUE COMMENT '系统内唯一编号',
    driver_id VARCHAR(32) NOT NULL COMMENT '关联驱动',
    manufacturer VARCHAR(64),
    model VARCHAR(64),
    serial_number VARCHAR(64),
    firmware_ver VARCHAR(32),
    hardware_rev VARCHAR(32),
    location VARCHAR(128) COMMENT '实验室位置',
    status VARCHAR(20) NOT NULL DEFAULT 'OFFLINE' COMMENT 'ONLINE/OFFLINE/MAINTENANCE',
    registered_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at DATETIME,
    INDEX idx_driver (driver_id),
    INDEX idx_status (status)
) ENGINE=InnoDB COMMENT='仪器注册表';

CREATE TABLE instrument_telemetry (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    instrument_id VARCHAR(32) NOT NULL,
    cpu_temp DECIMAL(5,2),
    ambient_temp DECIMAL(5,2),
    humidity DECIMAL(5,2),
    reagent_remain INT,
    uptime_seconds BIGINT,
    active_faults JSON,
    recorded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_instrument_time (instrument_id, recorded_at)
) ENGINE=InnoDB COMMENT='仪器遥测';

CREATE TABLE firmware_upgrade_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    instrument_id VARCHAR(32) NOT NULL,
    from_version VARCHAR(32),
    to_version VARCHAR(32) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/TRANSFERRING/FLASHING/SUCCESS/FAILED',
    started_at DATETIME,
    completed_at DATETIME,
    error_message TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_instrument (instrument_id)
) ENGINE=InnoDB COMMENT='固件升级记录';

-- ==================== 审计 ====================

CREATE TABLE audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    user_name VARCHAR(64),
    action VARCHAR(64) NOT NULL COMMENT 'LOGIN/VIEW/CREATE/EDIT/DELETE/APPROVE/EXPORT',
    resource_type VARCHAR(64) COMMENT 'SAMPLE/RESULT/REPORT/USER/CONFIG',
    resource_id VARCHAR(64),
    detail JSON COMMENT '变更前后值diff',
    client_ip VARCHAR(45),
    session_id VARCHAR(64),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_action (action),
    INDEX idx_resource (resource_type, resource_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB COMMENT='审计日志';
```

- [ ] **Step 2: 用 Docker 启动 MySQL 并执行初始化**

Run: `cd g:/myla/myla-deploy/docker && docker compose up -d mysql`
Expected: MySQL 容器启动成功，`myla` 数据库创建完成，所有表就绪

- [ ] **Step 3: 验证表结构**

Run: `docker exec myla-mysql mysql -uroot -proot myla -e "SHOW TABLES;"`
Expected: 列出 19 张表

- [ ] **Step 4: Commit**

```bash
git add myla-deploy/sql/V1__init_schema.sql myla-deploy/docker/docker-compose.yml
git commit -m "feat: add complete database schema for Phase 1

- Dictionary tables: hospital, organism_dict, antibiotic_dict, specimen_dict
- RBAC: sys_user, sys_role, sys_permission, sys_user_role, sys_role_permission
- Sample tracking: sample, sample_test, sample_tracking
- Results: organism_result, ast_result, raw_message
- Workflow: workflow_rule, critical_value_alert
- LIS: lis_config, outbound_message
- Reports: report_template, report_schedule
- Device mgmt: instrument_registry, instrument_telemetry, firmware_upgrade_log
- Audit: audit_log

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 4: 仪器网关 — SPI 核心框架

**Files:**
- Create: `myla-platform/myla-platform-gateway/gateway-core/src/main/java/com/myla/gateway/core/spi/InstrumentDriver.java`
- Create: `myla-platform/myla-platform-gateway/gateway-core/src/main/java/com/myla/gateway/core/spi/CommunicationChannel.java`
- Create: `myla-platform/myla-platform-gateway/gateway-core/src/main/java/com/myla/gateway/core/spi/FrameSplitter.java`
- Create: `myla-platform/myla-platform-gateway/gateway-core/src/main/java/com/myla/gateway/core/spi/DataParser.java`
- Create: `myla-platform/myla-platform-gateway/gateway-core/src/main/java/com/myla/gateway/core/spi/DataEventListener.java`
- Create: `myla-platform/myla-platform-gateway/gateway-core/src/main/java/com/myla/gateway/core/spi/TelemetryListener.java`
- Create: `myla-platform/myla-platform-gateway/gateway-core/src/main/java/com/myla/gateway/core/context/DriverContext.java`
- Create: `myla-platform/myla-platform-gateway/gateway-core/src/main/java/com/myla/gateway/core/context/DriverConfig.java`
- Create: `myla-platform/myla-platform-gateway/gateway-core/src/main/java/com/myla/gateway/core/hub/InstrumentHub.java`
- Create: `myla-platform/myla-platform-gateway/gateway-core/src/main/java/com/myla/gateway/core/hub/DriverContainer.java`
- Create: `myla-platform/myla-platform-gateway/gateway-core/src/main/java/com/myla/gateway/core/model/InstrumentCommand.java`
- Create: `myla-platform/myla-platform-gateway/gateway-core/src/main/java/com/myla/gateway/core/model/CommandResult.java`
- Create: `myla-platform/myla-platform-gateway/gateway-core/src/main/java/com/myla/gateway/core/model/DiscoveryInfo.java`
- Create: `myla-platform/myla-platform-gateway/gateway-core/src/main/java/com/myla/gateway/core/model/TelemetryData.java`
- Create: `myla-platform/myla-platform-gateway/gateway-core/src/main/java/com/myla/gateway/core/model/InstrumentStatus.java`

- [ ] **Step 1: 创建 CommunicationChannel 接口**

```java
// gateway-core/src/main/java/com/myla/gateway/core/spi/CommunicationChannel.java
package com.myla.gateway.core.spi;

import com.myla.gateway.core.context.DriverConfig;
import com.myla.gateway.core.model.ConnectionError;
import java.util.function.Consumer;

public interface CommunicationChannel {
    String getChannelType();
    void open(DriverConfig.ChannelConfig config);
    void close();
    boolean isOpen();
    void send(byte[] data);
    void setMessageListener(Consumer<byte[]> onMessage);
    void setErrorListener(Consumer<ConnectionError> onError);
}
```

- [ ] **Step 2: 创建 FrameSplitter 接口**

```java
// gateway-core/src/main/java/com/myla/gateway/core/spi/FrameSplitter.java
package com.myla.gateway.core.spi;
import java.util.List;

public interface FrameSplitter {
    String getSplitterType();
    List<byte[]> splitFrames(byte[] rawBytes, List<byte[]> incompleteFrames);
}
```

- [ ] **Step 3: 创建 DataParser 接口**

```java
// gateway-core/src/main/java/com/myla/gateway/core/spi/DataParser.java
package com.myla.gateway.core.spi;

import com.myla.common.api.dto.UnifiedResult;
import com.myla.common.core.exception.ParseException;
import java.util.List;

public interface DataParser {
    String getParserId();
    List<UnifiedResult> parse(byte[] frame) throws ParseException;
}
```

- [ ] **Step 4: 创建 DataEventListener 接口**

```java
// gateway-core/src/main/java/com/myla/gateway/core/spi/DataEventListener.java
package com.myla.gateway.core.spi;

import com.myla.common.api.dto.UnifiedResult;
import com.myla.gateway.core.model.InstrumentStatus;

public interface DataEventListener {
    void onResultReceived(UnifiedResult result);
    void onParseFailed(String rawText, String error);
    void onStatusChanged(InstrumentStatus status);
    void onConnectionError(String instrumentId, String error, int consecutiveFailures);
}
```

- [ ] **Step 5: 创建 TelemetryListener 接口**

```java
// gateway-core/src/main/java/com/myla/gateway/core/spi/TelemetryListener.java
package com.myla.gateway.core.spi;

import com.myla.gateway.core.model.TelemetryData;

public interface TelemetryListener {
    void onTelemetry(String instrumentId, TelemetryData data);
}
```

- [ ] **Step 6: 创建 InstrumentDriver 接口**

```java
// gateway-core/src/main/java/com/myla/gateway/core/spi/InstrumentDriver.java
package com.myla.gateway.core.spi;

import com.myla.common.api.enums.CommunicationMode;
import com.myla.gateway.core.context.DriverConfig;
import com.myla.gateway.core.context.DriverContext;
import com.myla.gateway.core.model.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface InstrumentDriver {
    String getDriverId();
    String getDisplayName();
    String getVersion();
    CommunicationMode getMode();
    void initialize(DriverConfig config);
    void start(DriverContext ctx);
    void stop();
    boolean testConnection();
    void registerListener(DataEventListener listener);

    // 双向指令
    default CompletableFuture<CommandResult> executeCommand(InstrumentCommand command) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Commands not supported"));
    }

    // 设备发现
    DiscoveryInfo getDiscoveryInfo();

    // 遥测
    default void registerTelemetryListener(TelemetryListener listener) {}

    // 维护
    default List<MaintenanceCapability> getMaintenanceCapabilities() { return List.of(); }
    default CompletableFuture<CommandResult> executeMaintenance(MaintenanceCommand cmd) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Maintenance not supported"));
    }
}
```

- [ ] **Step 7: 创建模型类**

```java
// gateway-core/src/main/java/com/myla/gateway/core/model/InstrumentCommand.java
package com.myla.gateway.core.model;

import lombok.Data;
import java.util.Map;
import java.util.UUID;

@Data
public class InstrumentCommand {
    private String commandId = UUID.randomUUID().toString();
    private String instrumentId;
    private CommandType type;
    private Map<String, Object> parameters;
    private int timeoutSeconds = 30;

    public enum CommandType { START_TEST, STOP_TEST, SELECT_CARD, QUERY_STATUS, SEND_ORDER }
}
```

```java
// gateway-core/src/main/java/com/myla/gateway/core/model/CommandResult.java
package com.myla.gateway.core.model;

import lombok.Data;
import java.util.Map;

@Data
public class CommandResult {
    private String commandId;
    private CommandStatus status;
    private Map<String, Object> output;
    private String errorMessage;
    private long elapsedMs;

    public enum CommandStatus { ACCEPTED, EXECUTING, COMPLETED, FAILED, TIMEOUT }
}
```

```java
// gateway-core/src/main/java/com/myla/gateway/core/model/DiscoveryInfo.java
package com.myla.gateway.core.model;

import lombok.Data;
import java.util.List;

@Data
public class DiscoveryInfo {
    private String manufacturer;
    private String model;
    private String serialNumber;
    private String firmwareVersion;
    private String hardwareRevision;
    private List<String> supportedCommands;
}
```

```java
// gateway-core/src/main/java/com/myla/gateway/core/model/TelemetryData.java
package com.myla.gateway.core.model;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class TelemetryData {
    private LocalDateTime timestamp = LocalDateTime.now();
    private Double cpuTemp;
    private Double ambientTemp;
    private Double humidity;
    private String powerStatus;
    private Integer reagentRemaining;
    private Long uptimeSeconds;
    private List<String> activeFaults;
}
```

```java
// gateway-core/src/main/java/com/myla/gateway/core/model/InstrumentStatus.java
package com.myla.gateway.core.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class InstrumentStatus {
    private String instrumentId;
    private Status status;
    private String message;
    private LocalDateTime timestamp = LocalDateTime.now();

    public enum Status { ONLINE, OFFLINE, BUSY, ERROR, MAINTENANCE }
}
```

```java
// gateway-core/src/main/java/com/myla/gateway/core/model/ConnectionError.java
package com.myla.gateway.core.model;

import lombok.Data;

@Data
public class ConnectionError {
    private String channelType;
    private String message;
    private Throwable cause;
}
```

```java
// gateway-core/src/main/java/com/myla/gateway/core/model/MaintenanceCapability.java
package com.myla.gateway.core.model;

public enum MaintenanceCapability {
    FIRMWARE_UPGRADE, CALIBRATE, SELF_TEST, RESET, SHUTDOWN, RESTART
}
```

```java
// gateway-core/src/main/java/com/myla/gateway/core/model/MaintenanceCommand.java
package com.myla.gateway.core.model;

import lombok.Data;
import java.util.Map;
import java.util.UUID;

@Data
public class MaintenanceCommand {
    private String commandId = UUID.randomUUID().toString();
    private String instrumentId;
    private MaintenanceCapability capability;
    private Map<String, Object> parameters;
}
```

- [ ] **Step 8: 创建 DriverConfig**

```java
// gateway-core/src/main/java/com/myla/gateway/core/context/DriverConfig.java
package com.myla.gateway.core.context;

import lombok.Data;
import java.util.Map;

@Data
public class DriverConfig {
    private String driverId;
    private String instrumentId;
    private ChannelConfig channel;
    private String splitterType;
    private String parserType;
    private Map<String, Object> properties;

    @Data
    public static class ChannelConfig {
        private String type;        // TCP, FILE, SERIAL
        private String host;
        private int port;
        private String directory;   // file channel
        private String filePattern; // file channel: *.txt
        private int pollIntervalMs = 5000; // file channel
        private String serialPort;
        private int baudRate = 9600;
        private long reconnectDelayMs = 1000;
    }
}
```

- [ ] **Step 9: 创建 DriverContext**

```java
// gateway-core/src/main/java/com/myla/gateway/core/context/DriverContext.java
package com.myla.gateway.core.context;

import java.util.function.Consumer;

public interface DriverContext {
    String getDriverId();
    String getInstrumentId();
    void saveRawMessage(String instrumentId, String messageType, byte[] rawData);
    void publishResult(byte[] rawData);
    void reportHealth(String instrumentId, String status, String message);
    void registerRetryScheduler(String key, Runnable task, long initialDelayMs, long maxDelayMs);
    void cancelRetryScheduler(String key);
    void sendAlert(String instrumentId, String alertType, String message);
}
```

- [ ] **Step 10: 创建 InstrumentHub**

```java
// gateway-core/src/main/java/com/myla/gateway/core/hub/InstrumentHub.java
package com.myla.gateway.core.hub;

import com.myla.gateway.core.spi.InstrumentDriver;
import com.myla.gateway.core.context.DriverConfig;
import com.myla.gateway.core.context.DriverContext;
import com.myla.gateway.core.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class InstrumentHub {

    private final Map<String, DriverContainer> containers = new ConcurrentHashMap<>();
    private final Map<String, InstrumentDriver> drivers = new ConcurrentHashMap<>();

    public void loadDriver(InstrumentDriver driver, DriverConfig config, DriverContext context) {
        String instrumentId = config.getInstrumentId();
        DriverContainer container = new DriverContainer(driver, config, context);
        containers.put(instrumentId, container);
        drivers.put(instrumentId, driver);
        driver.initialize(config);
        log.info("Driver loaded: driverId={}, instrumentId={}", driver.getDriverId(), instrumentId);
    }

    public void startDriver(String instrumentId) {
        DriverContainer container = containers.get(instrumentId);
        if (container != null) {
            container.start();
            log.info("Driver started: instrumentId={}", instrumentId);
        }
    }

    public void stopDriver(String instrumentId) {
        DriverContainer container = containers.get(instrumentId);
        if (container != null) {
            container.stop();
            log.info("Driver stopped: instrumentId={}", instrumentId);
        }
    }

    public void unloadDriver(String instrumentId) {
        stopDriver(instrumentId);
        containers.remove(instrumentId);
        drivers.remove(instrumentId);
        log.info("Driver unloaded: instrumentId={}", instrumentId);
    }

    public CompletableFuture<CommandResult> sendCommand(String instrumentId, InstrumentCommand command) {
        InstrumentDriver driver = drivers.get(instrumentId);
        if (driver == null) throw new IllegalArgumentException("Driver not found: " + instrumentId);
        return driver.executeCommand(command);
    }

    public DiscoveryInfo getDiscoveryInfo(String instrumentId) {
        InstrumentDriver driver = drivers.get(instrumentId);
        if (driver == null) throw new IllegalArgumentException("Driver not found: " + instrumentId);
        return driver.getDiscoveryInfo();
    }

    public List<String> listInstruments() { return List.copyOf(containers.keySet()); }

    public Map<String, InstrumentStatus> getAllStatuses() {
        Map<String, InstrumentStatus> statuses = new ConcurrentHashMap<>();
        containers.forEach((id, c) -> statuses.put(id, c.getCurrentStatus()));
        return statuses;
    }
}
```

- [ ] **Step 11: 创建 DriverContainer**

```java
// gateway-core/src/main/java/com/myla/gateway/core/hub/DriverContainer.java
package com.myla.gateway.core.hub;

import com.myla.gateway.core.spi.InstrumentDriver;
import com.myla.gateway.core.context.DriverConfig;
import com.myla.gateway.core.context.DriverContext;
import com.myla.gateway.core.model.InstrumentStatus;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class DriverContainer {
    @Getter
    private final InstrumentDriver driver;
    private final DriverConfig config;
    private final DriverContext context;
    private final AtomicBoolean running = new AtomicBoolean(false);
    @Getter
    private volatile InstrumentStatus currentStatus;

    public DriverContainer(InstrumentDriver driver, DriverConfig config, DriverContext context) {
        this.driver = driver;
        this.config = config;
        this.context = context;
        this.currentStatus = new InstrumentStatus();
        this.currentStatus.setInstrumentId(config.getInstrumentId());
        this.currentStatus.setStatus(InstrumentStatus.Status.OFFLINE);
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            driver.start(context);
            currentStatus.setStatus(InstrumentStatus.Status.ONLINE);
            log.info("Driver {} started for instrument {}", driver.getDriverId(), config.getInstrumentId());
        }
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            driver.stop();
            currentStatus.setStatus(InstrumentStatus.Status.OFFLINE);
            log.info("Driver {} stopped for instrument {}", driver.getDriverId(), config.getInstrumentId());
        }
    }
}
```

- [ ] **Step 12: 编译验证**

Run: `cd g:/myla && mvn compile -q`
Expected: BUILD SUCCESS（gateway-core 编译通过）

- [ ] **Step 13: Commit**

```bash
git add -A
git commit -m "feat: add instrument gateway SPI core framework

- Core SPI interfaces: InstrumentDriver, CommunicationChannel, FrameSplitter, DataParser
- Event listeners: DataEventListener, TelemetryListener
- Model objects: InstrumentCommand, CommandResult, DiscoveryInfo, TelemetryData, InstrumentStatus
- InstrumentHub: driver lifecycle management (load/start/stop/unload)
- DriverContainer: isolated driver runner with health status tracking
- DriverConfig & DriverContext for framework services injection

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 5: 仪器网关 — TCP + File Channel 实现

**Files:**
- Create: `myla-platform/myla-platform-gateway/gateway-channel/src/main/java/com/myla/gateway/channel/TcpChannel.java`
- Create: `myla-platform/myla-platform-gateway/gateway-channel/src/main/java/com/myla/gateway/channel/FileChannel.java`

- [ ] **Step 1: 实现 TcpChannel**

```java
// gateway-channel/src/main/java/com/myla/gateway/channel/TcpChannel.java
package com.myla.gateway.channel;

import com.myla.gateway.core.spi.CommunicationChannel;
import com.myla.gateway.core.context.DriverConfig;
import com.myla.gateway.core.model.ConnectionError;
import lombok.extern.slf4j.Slf4j;
import java.io.*;
import java.net.*;
import java.util.function.Consumer;

@Slf4j
public class TcpChannel implements CommunicationChannel {
    private ServerSocket serverSocket;
    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private Consumer<byte[]> messageListener;
    private Consumer<ConnectionError> errorListener;
    private volatile boolean running;

    @Override public String getChannelType() { return "TCP"; }

    @Override
    public void open(DriverConfig.ChannelConfig config) {
        try {
            serverSocket = new ServerSocket(config.getPort());
            running = true;
            log.info("TCP channel listening on port {}", config.getPort());
            new Thread(() -> {
                while (running) {
                    try {
                        socket = serverSocket.accept();
                        in = socket.getInputStream();
                        out = socket.getOutputStream();
                        byte[] buf = new byte[65536]; int n;
                        while (running && (n = in.read(buf)) > 0) {
                            byte[] data = new byte[n];
                            System.arraycopy(buf, 0, data, 0, n);
                            if (messageListener != null) messageListener.accept(data);
                        }
                    } catch (IOException e) {
                        if (running && errorListener != null) {
                            ConnectionError err = new ConnectionError();
                            err.setChannelType("TCP"); err.setMessage(e.getMessage());
                            errorListener.accept(err);
                        }
                    }
                }
            }, "tcp-chan").start();
        } catch (IOException e) {
            throw new RuntimeException("TCP channel open failed on port " + config.getPort(), e);
        }
    }

    @Override public void close() {
        running = false;
        try { if (in != null) in.close(); } catch (IOException ignored) {}
        try { if (out != null) out.close(); } catch (IOException ignored) {}
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
    }

    @Override public boolean isOpen() { return running; }
    @Override
    public void send(byte[] data) {
        try { out.write(data); out.flush(); }
        catch (IOException e) { throw new RuntimeException("Send failed", e); }
    }
    @Override public void setMessageListener(Consumer<byte[]> l) { this.messageListener = l; }
    @Override public void setErrorListener(Consumer<ConnectionError> l) { this.errorListener = l; }
}
```

- [ ] **Step 2: 实现 FileChannel**

```java
// gateway-channel/src/main/java/com/myla/gateway/channel/FileChannel.java
package com.myla.gateway.channel;

import com.myla.gateway.core.spi.CommunicationChannel;
import com.myla.gateway.core.context.DriverConfig;
import com.myla.gateway.core.model.ConnectionError;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.nio.file.*;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

@Slf4j
public class FileChannel implements CommunicationChannel {
    private Consumer<byte[]> messageListener;
    private Consumer<ConnectionError> errorListener;
    private volatile boolean running;
    private ScheduledExecutorService scheduler;
    private final Set<String> processed = ConcurrentHashMap.newKeySet();

    @Override public String getChannelType() { return "FILE"; }

    @Override
    public void open(DriverConfig.ChannelConfig c) {
        running = true; scheduler = Executors.newSingleThreadScheduledExecutor();
        Path dir = Paths.get(c.getDirectory());
        try { Files.createDirectories(dir); } catch (IOException e) { throw new RuntimeException(e); }
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                Pattern p = Pattern.compile(c.getFilePattern() != null ? c.getFilePattern() : ".*\\\\.txt");
                try (DirectoryStream<Path> s = Files.newDirectoryStream(dir)) {
                    for (Path f : s) {
                        String fn = f.getFileName().toString();
                        if (p.matcher(fn).matches() && processed.add(fn)) {
                            if (messageListener != null) messageListener.accept(Files.readAllBytes(f));
                            Path arch = Paths.get(c.getDirectory(), "archive", fn);
                            Files.createDirectories(arch.getParent());
                            Files.move(f, arch, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
            } catch (IOException e) {
                if (running && errorListener != null) {
                    ConnectionError err = new ConnectionError();
                    err.setChannelType("FILE"); err.setMessage(e.getMessage());
                    errorListener.accept(err);
                }
            }
        }, 0, c.getPollIntervalMs(), TimeUnit.MILLISECONDS);
    }

    @Override public void close() { running = false; if (scheduler != null) scheduler.shutdown(); }
    @Override public boolean isOpen() { return running; }
    @Override public void send(byte[] data) { throw new UnsupportedOperationException("FileChannel read-only"); }
    @Override public void setMessageListener(Consumer<byte[]> l) { this.messageListener = l; }
    @Override public void setErrorListener(Consumer<ConnectionError> l) { this.errorListener = l; }
}
```

- [ ] **Step 3: 编译 + Commit**

Run: `cd g:/myla && mvn compile -q && git add -A && git commit -m "feat: TcpChannel + FileChannel implementations"`


## Task 6: ASTM + HL7 Splitters

**Files:**
- Create: `myla-platform/myla-platform-gateway/gateway-splitter/src/main/java/com/myla/gateway/splitter/AstmSplitter.java`
- Create: `myla-platform/myla-platform-gateway/gateway-splitter/src/main/java/com/myla/gateway/splitter/Hl7Splitter.java`

- [ ] **Step 1: ASTM splitter (STX/ETX frame boundary detection)**

```java
// gateway-splitter/.../AstmSplitter.java
package com.myla.gateway.splitter;
import com.myla.gateway.core.spi.FrameSplitter;
import java.util.*;

public class AstmSplitter implements FrameSplitter {
    private static final byte STX = 0x02, ETX = 0x03, ETB = 0x17;
    @Override public String getSplitterType() { return "ASTM"; }
    @Override
    public List<byte[]> splitFrames(byte[] raw, List<byte[]> incomplete) {
        List<byte[]> frames = new ArrayList<>();
        List<Byte> cur = new ArrayList<>(); boolean in = false;
        for (byte b : raw) {
            if (b == STX) { if (in && incomplete != null) incomplete.add(toArr(cur)); cur.clear(); in = true; }
            if (in) cur.add(b);
            if (in && (b == ETX || b == ETB)) { frames.add(toArr(cur)); cur.clear(); in = false; }
        }
        if (in && !cur.isEmpty() && incomplete != null) incomplete.add(toArr(cur));
        return frames;
    }
    private byte[] toArr(List<Byte> list) { byte[] a = new byte[list.size()]; for (int i=0;i<a.length;i++) a[i]=list.get(i); return a; }
}
```

- [ ] **Step 2: HL7 MLLP splitter (VT/FS+CR framing)**

```java
// gateway-splitter/.../Hl7Splitter.java
package com.myla.gateway.splitter;
import com.myla.gateway.core.spi.FrameSplitter;
import java.util.*;

public class Hl7Splitter implements FrameSplitter {
    private static final byte VT = 0x0B, FS = 0x1C, CR = 0x0D;
    @Override public String getSplitterType() { return "HL7-MLLP"; }
    @Override
    public List<byte[]> splitFrames(byte[] raw, List<byte[]> incomplete) {
        List<byte[]> frames = new ArrayList<>();
        byte[] buf = (incomplete != null && !incomplete.isEmpty()) ? merge(incomplete, raw) : raw;
        int s = -1;
        for (int i = 0; i < buf.length - 1; i++) {
            if (buf[i] == VT) s = i;
            if (s >= 0 && buf[i] == FS && buf[i+1] == CR) {
                byte[] f = new byte[i+2-s]; System.arraycopy(buf, s, f, 0, f.length); frames.add(f); s = -1;
            }
        }
        if (incomplete != null) incomplete.clear();
        if (s >= 0 && s < buf.length && incomplete != null) {
            byte[] rem = new byte[buf.length-s]; System.arraycopy(buf, s, rem, 0, rem.length); incomplete.add(rem);
        }
        return frames;
    }
    private byte[] merge(List<byte[]> frags, byte[] n) {
        int t = n.length; for (byte[] f : frags) t += f.length;
        byte[] m = new byte[t]; int p = 0;
        for (byte[] f : frags) { System.arraycopy(f, 0, m, p, f.length); p += f.length; }
        System.arraycopy(n, 0, m, p, n.length); frags.clear(); return m;
    }
}
```

- [ ] **Step 3: 编译 + Commit**

Run: `cd g:/myla && mvn compile -q && git add -A && git commit -m "feat: ASTM + HL7-MLLP frame splitters"`


## Task 7: VITEK 2 Driver + Proprietary Protocol Driver + All Business Modules

- [ ] **Step 1: VITEK 2 driver (TCP + ASTM + Vitek2Parser)**

路径: `gateway-drivers/driver-vitek2/.../Vitek2Parser.java`, `Vitek2Driver.java`

Vitek2Parser 解析 ASTM 管式分隔 (`O|` / `R|`) 记录到 UnifiedResult。Vitek2Driver 组合 TcpChannel + AstmSplitter + Vitek2Parser，启动后监听仪器 TCP 连接。

- [ ] **Step 2: Proprietary Protocol SDK + Driver**

路径: `gateway-protocol/.../FrameType.java`, `ProprietaryFrameCodec.java`
路径: `gateway-drivers/driver-proprietary/.../ProprietaryProtocolDriver.java`

8种帧类型编解码，驱动自动分发 RESULT_PUSH/TELEMETRY/HEARTBEAT/DISCOVERY 到对应 Listener。

- [ ] **Step 3: RabbitMQ + Redis + MyBatis-Plus 全局配置**

路径: `myla-server/.../config/RabbitMqConfig.java`（6 Exchanges + DLQ）, `MybatisPlusConfig.java`, `RedisConfig.java`, `MylaProperties.java`

- [ ] **Step 4: 样本管理模块** (Entity → Mapper → Service → Controller)
- [ ] **Step 5: 结果管理模块** (ResultParsedConsumer + ResultService + Controller)
- [ ] **Step 6: 工作流引擎** (LabEventConsumer, WorkflowRule matching)
- [ ] **Step 7: LIS 网关** (OutboundMessageConsumer with DLQ retry)
- [ ] **Step 8: 通知 + 审计 + JWT 认证**

所有业务模块创建完毕后编译验证。

- [ ] **Step 9: 编译 + Commit**

Run: `cd g:/myla && mvn compile -q && git add -A && git commit -m "feat: complete Phase 1 MVP - all platform modules"`


## Task 8: 前端工程 (Vue 3 + Element Plus)

- [ ] **Step 1: 初始化项目**

```bash
cd g:/myla/myla-web
npm init -y
npm install vue@3 vue-router@4 pinia axios element-plus @element-plus/icons-vue
npm install -D vite @vitejs/plugin-vue
```

创建 `vite.config.js`, `src/main.js`, `src/App.vue`, `src/router/index.js`, `src/api/request.js`

- [ ] **Step 2: 结果审核页面** (ResultReviewView.vue: 待审核队列 + 详情 + 通过/退回)
- [ ] **Step 3: 样本管理页面** (SampleListView.vue: 登记表单 + 列表 + 流转日志)
- [ ] **Step 4: 仪器管理页面** (InstrumentView.vue: 在线状态 + 驱动启停)
- [ ] **Step 5: 登录 + 用户管理页面**
- [ ] **Step 6: 编译 + Commit**

Run: `cd g:/myla/myla-web && npm run build && cd ../.. && git add -A && git commit -m "feat: Vue 3 frontend with core pages"`


## Task 9: 部署 + 集成验证

- [ ] **Step 1: 部署脚本** `myla-deploy/scripts/install.sh`, `backup.sh`
- [ ] **Step 2: 生产配置** `application-prod.yml`
- [ ] **Step 3: 全链路集成测试**

```bash
cd g:/myla/myla-deploy/docker && docker compose up -d
sleep 15
cd g:/myla && mvn spring-boot:run -pl myla-server &
sleep 20
curl http://localhost:8080/actuator/health  # 期望 UP
curl -u guest:guest http://localhost:15672/api/queues  # 验证所有队列已创建
cd myla-web && npm run dev  # 前端在 localhost:3000 可访问
```

- [ ] **Step 4: 最终提交**

```bash
git add -A && git commit -m "feat: deploy scripts + production config + integration verification

Phase 1 MVP complete.

Co-Authored-By: Claude <noreply@anthropic.com>"
```
