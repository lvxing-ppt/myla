# MYLA 同类微生物实验室中间件平台 — 架构设计文档

> **状态：** 待评审  
> **日期：** 2026-07-04  
> **市场定位：** 中国临床微生物实验室中间件平台  
> **目标注册：** NMPA 医疗器械软件（三类）

---

## 1. 项目概述

参考 bioMérieux MYLA/MAESTRIA 平台，设计一套面向中国市场的微生物实验室专用数据管理中间件系统。核心使命：连接实验室仪器、医院 LIS 系统和临床用户，实现微生物检验数据的自动化采集、智能工作流编排、结果审核管理，以及数据分析和专业报告。

### 1.1 核心约束

| 维度 | 决策 |
|------|------|
| 功能范围 | 完整对标 MYLA，分三期交付 |
| 目标市场 | 中国为主，需通过 NMPA 医疗器械软件注册 |
| 技术栈 | Java (Spring Boot 3.x + JDK 17) |
| 数据库 | MySQL 8.0 |
| 部署模式 | 纯院内私有化部署（三级等保要求） |
| 仪器集成 | 通用 SPI 接口架构 + 适配器渐进开发 |
| 前端兼容 | 需支持 IE11+ / Chrome (医院终端现状) |

---

## 2. 整体分层架构

```
┌─────────────────────────────────────────────────────────────┐
│                    前端展示层 (Vue 3 + Element Plus)          │
│   实时看板  │  样本跟踪  │  结果审核  │  报告中心  │  系统管理   │
├─────────────────────────────────────────────────────────────┤
│                    业务服务层 (Spring Boot)                   │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐  │
│  │ 工作流引擎 │ │ 结果管理  │ │ 报告引擎  │ │ 质控与指标   │  │
│  └──────────┘ └──────────┘ └──────────┘ └──────────────┘  │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐  │
│  │ 用户权限  │ │ 审计追溯  │ │ 消息通知  │ │ 系统配置     │  │
│  └──────────┘ └──────────┘ └──────────┘ └──────────────┘  │
├─────────────────────────────────────────────────────────────┤
│              仪器集成层 (InstrumentHub - SPI 框架)           │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  驱动生命周期 │ 健康监控 │ 原始报文存档 │ 告警分发     │  │
│  │  ┌─────────────────────────────────────────────────┐ │  │
│  │  │ 第三方仪器: Driver Container → Chan→Split→Parse │ │  │
│  │  │ 自家仪器:   Proprietary Protocol Driver (通用)   │ │  │
│  │  │ 设备管理:   仪器注册 │ 遥测 │ 固件 │ 远程维护   │ │  │
│  │  └─────────────────────────────────────────────────┘ │  │
│  └───────────────────────────────────────────────────────┘  │
├─────────────────────────────────────────────────────────────┤
│              通信层 (Communication Engine)                    │
│   HL7 v2.x  │  ASTM E1381  │  TCP/Serial  │  FTP/File    │
├─────────────────────────────────────────────────────────────┤
│                    数据持久层                                 │
│        MySQL 8.0 (业务+原始数据) │ Redis (缓存/Session)     │
│                   RabbitMQ (异步消息中枢)                    │
└─────────────────────────────────────────────────────────────┘
```

**核心原则：**
- 上下单向依赖：上层依赖下层，下层不感知上层
- 仪器层隔离：新增适配器 = 开发一个 jar + 上传 + 配置，不触及核心代码
- 通信层复用：ASTM 解析器、HL7 解析器作为通用组件共享

---

## 3. 分期规划

### 一期 (MVP, ≈6-8个月) — "让数据跑通"

**目标：** 打通仪器 → 中间件 → LIS 完整数据链路，解决仪器数据孤岛和手工誊抄问题。

| 模块 | 范围 |
|------|------|
| 仪器连接框架 | SPI 插件机制 + 通信引擎（ASTM/HL7解析器），保证架构可扩展 |
| 首个仪器适配器 | VITEK 2 适配器（国内装机量最大），解析 ID/AST 结果 |
| LIS 对接 | HL7 v2.x 双向通信（医嘱下达、结果回传），同时支持 FTP/TCP/串口 |
| 样本管理 | 从样本登记(barcode) → 上机 → 出结果 → 审核的全流程状态跟踪 |
| 结果管理 | 统一的菌种鉴定 + 药敏结果查看界面，支持人工审核/修正/批准 |
| 基础报告 | 检验报告单（PDF/Excel导出）、日志查询 |
| 权限体系 | 用户-角色-权限 RBAC，满足三级等保基本要求 |

### 二期 (≈6-8个月) — "让流程智能起来 + 可试运行"

| 模块 | 范围 |
|------|------|
| **前端 7 页面** | 登录/工作台/样本管理/结果审核/仪器监控/报告中心/系统管理 |
| **LIS HL7 双向** | HL7 ORM 入站解析医嘱 + ORU 出站回传结果，先接 1 家标杆医院 |
| **更多仪器** | BACTEC FX 血培养仪、BD Phoenix M50、MALDI-TOF 质谱、2 种国产仪器 |
| **标本质量评估** | 签收时记录标本合格性判定（痰标本上皮细胞比例、血量、是否重复） + 不合格原因 + 拒收自动通知临床 |
| **涂片镜检** | gram_stain_result 表 + 镜检报告录入（WBC/上皮/细菌形态/G+/G-球菌杆菌）+ 状态机关联 |
| **培养管理** | 培养箱登记 + 培养基选择记录 + 定时观察提醒 + 到期通知 |
| **报告 PDF 打印** | JasperReports 模板 + PDF 检验报告单生成 + 批量打印 |
| **耗材管理** | 培养基/试剂/药敏卡效期管理，与质控共享批号追溯 |
| 质控模块 | 质控菌株管理、质控结果跟踪、Westgard 多规则失控判定 |
| 智能串联 | 血培养阳性→推荐 MALDI，MALDI 鉴定→推荐药敏卡片，多仪器同 barcode 结果汇总 |
| 通知网关 | 短信 SDK + 邮件 JavaMailSender + 危急值分级分时段多渠道通知 |
| 审计追溯 | @AuditLog AOP 自动记录全部操作，变更前后 diff，不可删除 |
| 数据库备份恢复 | MySQL 定时全量备份 + binlog 实时备份 + 一键恢复脚本 |
| **NMPA 注册准备** | 软件需求规格书(SRS) + 设计说明书(SDD) + 风险管理报告 + 测试文档 |

### 三期 (≈4-6个月) — "数据的价值挖掘"

| 模块 | 范围 |
|------|------|
| Antibiogram | 累积药敏报告（%S/%I/%R 趋势）、MIC50/MIC90 分布 |
| 监测报告 | MDRO、血培养污染率、阳性率、HAI 院感监测 |
| TAT 分析 | 各环节周转时间统计、瓶颈识别 |
| 多院区 | 医联体/区域检验中心数据汇总 |
| NMPA 注册 | 注册文档、软件测试、临床评价、提交注册 |

---

## 4. 仪器集成层详细设计

### 4.1 三层解耦架构

```
通信方式 (怎么连) × 数据格式 (怎么解析) = 解耦，避免 N×M 组合爆炸

Channel (通信通道)     Splitter (帧切分器)      Parser (数据解析器)
┌──────────────┐      ┌──────────────┐      ┌──────────────┐
│ TcpChannel   │      │ AstmSplitter │      │ Vitek2Parser │
│ FileChannel  │      │ Hl7Splitter  │      │ BacTParser   │
│ SerialChannel│      │ RawPassThru  │      │ GenericASTM  │
└──────────────┘      └──────────────┘      └──────────────┘
       复用                 复用                  每人一个
```

### 4.2 核心接口定义

```java
// 通信通道 —— 只管收发字节流，完全不懂业务
public interface CommunicationChannel {
    void open(ChannelConfig config);
    void close();
    boolean isOpen();
    void send(byte[] data);
    void setMessageListener(Consumer<byte[]> onMessage);
    void setErrorListener(Consumer<ConnectionError> onError);
}

// 帧切分器 —— 处理 STX/ETX 边界、转义字符、校验和
public interface FrameSplitter {
    List<byte[]> splitFrames(byte[] rawBytes, List<byte[]> incompleteFrames);
}

// 数据解析器 —— 把字节流变成 UnifiedResult，完全不懂网络
public interface DataParser {
    String getParserId();
    List<UnifiedResult> parse(byte[] rawMessage) throws ParseException;
}

// 仪器驱动 = Channel + Splitter + Parser 的组合 + 生命周期
public interface InstrumentDriver {
    String getDriverId();
    String getDisplayName();
    CommunicationMode getMode();  // PASSIVE_LISTEN | ACTIVE_POLL | ACTIVE_CONNECT
    List<InstrumentProtocol> getSupportedProtocols();
    void initialize(DriverConfig config);
    void start(DriverContext ctx);
    void stop();
    boolean testConnection();
    void sendCommand(InstrumentCommand cmd);  // 双向通信（一期接口预留）
    void registerListener(DataEventListener listener);
}
```

### 4.3 事件监听器（区分成功和失败路径）

```java
public interface DataEventListener {
    void onResultReceived(UnifiedResult result);       // 解析成功
    void onParseFailed(String rawText, String error);  // 解析失败 → 人工干预队列
    void onStatusChanged(InstrumentStatus status);     // 仪器状态变更
    void onConnectionError(InstrumentError error, int consecutiveFailures);
}
```

### 4.4 DriverContext（框架提供的公共服务）

```java
public interface DriverContext {
    Scheduler getRetryScheduler();          // 统一重连调度（指数退避 1s→2s→4s→...→60s）
    MessageStore getRawMessageStore();      // 所有原始报文自动存档
    AlertService getAlertService();         // 发送告警
    void reportHealth(String driverId, HealthStatus status); // 心跳上报
}
```

### 4.5 统一内部数据模型

```java
public class UnifiedResult {
    String instrumentId;
    String sampleBarcode;
    String patientId;
    String patientName;
    String caseId;              // 跨仪器关联键（条码+时间窗）
    ResultType resultType;      // BLOOD_CULTURE_FLAG | ORGANISM_ID | AST | QC
    String organismCode;
    String organismName;
    Double identificationPercent;
    List<AstResult> astResults;
    LocalDateTime testTime;
    String rawMessage;
}
```

### 4.6 可靠性保障（四种失败模式统一处理）

| 失败模式 | 策略 |
|----------|------|
| 连接断开 | 指数退避重连（1s → 2s → 4s → ... → max 60s），Hub 统一提供 |
| 报文校验失败 | 丢弃 + 告警（ASTM 帧有 checksum） |
| 解析失败 | 原始报文已存档 → 标记 parse_failed → 入人工解析队列 → UI 标记 |
| 仪器返回错误码 | 解析错误语义 + 记录 + 可选告警 |

### 4.7 仪表盘驱动热升级

```
drivers/
├── vitek2-1.0.0.jar        (当前运行)
├── vitek2-1.1.0.jar        (新上传，下次重启该驱动生效)
└── vitek2-1.0.0.jar.bak    (自动备份)
```

- 每个驱动独立 ClassLoader，卸载 = 销毁 ClassLoader
- 通过管理界面"重启单个驱动"，无需重启整个系统

### 4.8 自有仪器扩展接口

自有硬件（公司自主研发仪器）的集成需求远超第三方仪器：不仅需要数据采集，还需要双向指令、设备遥测、固件升级、校准维护等完整设备管理能力。

#### 4.8.1 扩充 InstrumentDriver 接口

```java
public interface InstrumentDriver {

    // ========== 原有（不变） ==========
    String getDriverId();
    CommunicationMode getMode();
    void initialize(DriverConfig config);
    void start(DriverContext ctx);
    void stop();
    boolean testConnection();
    void registerListener(DataEventListener listener);

    // ========== 双向结构化指令 ==========
    CompletableFuture<CommandResult> executeCommand(InstrumentCommand command);

    // ========== 设备发现与注册 ==========
    DiscoveryInfo getDiscoveryInfo();  // 仪器型号/序列号/固件版本/支持的指令集

    // ========== 设备遥测 ==========
    void registerTelemetryListener(TelemetryListener listener);

    // ========== 维护操作 ==========
    List<MaintenanceCapability> getMaintenanceCapabilities();
    CompletableFuture<CommandResult> executeMaintenance(MaintenanceCommand cmd);
}
```

#### 4.8.2 结构化指令模型

```java
// 仪器指令（结构化，不依赖字节流）
public class InstrumentCommand {
    String commandId;                // 唯一追踪ID
    CommandType type;                // START_TEST / STOP_TEST / SELECT_CARD / QUERY_STATUS
    Map<String, Object> parameters;  // 结构化参数
    int timeoutSeconds;
    Priority priority;               // NORMAL / URGENT
}

public class CommandResult {
    String commandId;
    CommandStatus status;            // ACCEPTED / EXECUTING / COMPLETED / FAILED / TIMEOUT
    Map<String, Object> output;
    String errorMessage;
    long elapsedMs;
}
```

#### 4.8.3 设备遥测模型

```java
public interface TelemetryListener {
    void onTelemetry(String instrumentId, TelemetryData data);
}

public class TelemetryData {
    LocalDateTime timestamp;
    // 环境与运行
    Double cpuTemp;                  // CPU温度
    Double ambientTemp;              // 环境温度
    Double humidity;                 // 湿度
    String powerStatus;              // 供电状态
    // 耗材
    Integer reagentRemaining;        // 剩余试剂次数
    LocalDate reagentExpiry;         // 试剂效期
    // 硬件
    Long uptimeSeconds;              // 运行时长
    List<HardwareModuleStatus> modules; // 各模块状态
    // 故障
    List<String> activeFaults;       // 当前故障码
}
```

#### 4.8.4 设备发现

```java
public class DiscoveryInfo {
    String manufacturer;             // 公司名称
    String model;                    // "AutoMicro-X1"
    String serialNumber;             // 唯一序列号
    String firmwareVersion;
    String hardwareRevision;
    List<CommandType> supportedCommands;
    List<MaintenanceCapability> capabilities;
    // 维护能力枚举: FIRMWARE_UPGRADE | CALIBRATE | SELF_TEST | RESET | ...
}
```

#### 4.8.5 自有协议 SDK (Proprietary Protocol)

既然硬件是自研的，在 TCP/串口之上定义一套标准应用层协议，所有自家仪器统一使用：

```
┌────────────────────────────────────┐
│     Proprietary Protocol SDK       │  ← 硬件团队参考实现，嵌入固件
│                                    │
│  Frame Format (统一帧格式):        │
│  ┌────┬────┬────┬────┬──────┬────┐│
│  │STX │Len │Type│Payld│ CRC  │ETX ││
│  │1B  │2B  │1B  │N B  │ 2B   │1B  ││
│  └────┴────┴────┴────┴──────┴────┘│
│                                    │
│  Message Types:                    │
│  · 0x01 HEARTBEAT   心跳           │
│  · 0x02 RESULT_PUSH 结果推送       │
│  · 0x03 COMMAND     指令下发       │
│  · 0x04 COMMAND_ACK 指令应答       │
│  · 0x05 TELEMETRY   遥测上报       │
│  · 0x06 FW_UPGRADE  固件升级       │
│  · 0x07 DISCOVERY   设备注册       │
│  · 0xFF ERROR       错误帧         │
└────────────────────────────────────┘
```

**收益：**
- 硬件固件团队与中间件团队共享同一份协议文档/SDK
- 新仪器只要实现这 8 种帧类型，中间件侧使用 `ProprietaryProtocolDriver` 通用驱动，零代码
- 第三方仪器仍走 `Channel → Splitter → Parser` 解耦路径
- 两条路径互不干扰，各自演进

#### 4.8.6 整合视图

```
                        ┌─────────────────────────┐
                        │     InstrumentHub        │
                        └────────────┬─────────────┘
                                     │
              ┌──────────────────────┼──────────────────────┐
              │                      │                      │
     ┌────────▼────────┐   ┌────────▼────────┐   ┌─────────▼────────┐
     │ 第三方仪器路径    │   │ 自家仪器路径      │   │ 设备管理模块      │
     │                  │   │                  │   │                  │
     │ Channel          │   │ Proprietary      │   │ · 仪器注册表     │
     │  → Splitter      │   │ Protocol Driver  │   │ · 固件版本管理   │
     │   → Parser       │   │  (通用驱动)       │   │ · 遥测数据看板   │
     │                  │   │                  │   │ · 远程维护作业   │
     │ 例: VITEK2 Driver│   │ 所有自家仪器复用  │   │                  │
     └──────────────────┘   └──────────────────┘   └──────────────────┘
```

#### 4.8.7 新增数据表

```sql
-- 仪器注册表
instrument_registry (
    id              BIGINT PK,
    instrument_id   VARCHAR(32) UNIQUE,   -- 系统内唯一编号
    driver_id       VARCHAR(32),          -- 关联驱动
    manufacturer    VARCHAR(64),
    model           VARCHAR(64),
    serial_number   VARCHAR(64),
    firmware_ver    VARCHAR(32),
    hardware_rev    VARCHAR(32),
    location        VARCHAR(128),         -- 实验室位置
    status          VARCHAR(20),          -- ONLINE / OFFLINE / MAINTENANCE
    registered_at   DATETIME,
    last_seen_at    DATETIME
);

-- 仪器遥测记录 (时序性质，定期归档)
instrument_telemetry (
    id              BIGINT PK,
    instrument_id   VARCHAR(32),
    cpu_temp        DECIMAL(5,2),
    ambient_temp    DECIMAL(5,2),
    humidity        DECIMAL(5,2),
    reagent_remain  INT,
    uptime_seconds  BIGINT,
    active_faults   JSON,
    recorded_at     DATETIME
);

-- 固件升级记录
firmware_upgrade_log (
    id              BIGINT PK,
    instrument_id   VARCHAR(32),
    from_version    VARCHAR(32),
    to_version      VARCHAR(32),
    status          VARCHAR(20),          -- PENDING / TRANSFERRING / FLASHING / SUCCESS / FAILED
    started_at      DATETIME,
    completed_at    DATETIME,
    error_message   TEXT
);
```

---

## 5. 工作流引擎 & 样本管理

### 5.1 样本生命周期状态机

```
┌─────────┐    ┌─────────┐    ┌───────────┐    ┌──────────┐
│ REGISTER│───►│INOCULATE│───►│ INCUBATING│───►│ PENDING  │
│ 登记    │    │ 接种    │    │ 培养中    │    │ REVIEW   │
└─────────┘    └─────────┘    └───────────┘    │ 待审核   │
                                                └────┬─────┘
                                        ┌────────────┼────────────┐
                                        ▼            ▼            ▼
                                   ┌─────────┐ ┌─────────┐ ┌─────────┐
                                   │APPROVED │ │REJECTED │ │RELEASED │
                                   │ 已审核  │ │ 退回    │ │ 已发布  │
                                   └─────────┘ └─────────┘ └─────────┘
```

### 5.2 工作流事件模型

```java
public enum LabEvent {
    SAMPLE_REGISTERED, SAMPLE_RECEIVED,
    CULTURE_POSITIVE, CULTURE_NEGATIVE,
    ORGANISM_IDENTIFIED, AST_RESULT_RECEIVED,
    RESULT_APPROVED, RESULT_RELEASED_TO_LIS,
    CRITICAL_VALUE_DETECTED, TAT_THRESHOLD_EXCEEDED,
    SAMPLE_MISMATCH, QC_OUT_OF_RANGE
}

public class WorkflowRule {
    String ruleId;
    String name;           // "血培养报阳→危急值通知"
    LabEvent trigger;
    String condition;      // MVEL 表达式，一期用
    List<RuleAction> actions;
    int priority;
    boolean enabled;
}
```

### 5.3 规则引擎演进

| 阶段 | 方案 | 说明 |
|------|------|------|
| 一期 | MVEL 表达式 | 规则存 MySQL，管理界面 CRUD |
| 二期 | Drools | 复杂规则、冲突消解 |
| 三期 | 自研 DSL → 可视化编排 | 面向检验科老师的友好配置 |

### 5.4 危急值处理链

```
仪器报结果 → 规则匹配 → 判定为危急值
  → DB 标记 + 审核队列置顶 + 界面红色高亮
  → RabbitMQ: myla.notification / notify.sms → 短信发送
  → 临床确认回执（从LIS获取ACK或在界面手动确认）
  → 超时未确认 → 逐级升级通知（主管→主任）
```

### 5.5 核心数据表

```sql
sample (          -- 核心样本表
    sample_id, barcode, patient_id, specimen_type,
    collect_time, receive_time, status, priority, ward_code
)
sample_test (     -- 样本检验明细
    sample_id, test_code, instrument_id, status, started_at, completed_at
)
sample_tracking ( -- 样本流转日志
    sample_id, from_status, to_status, operator, comment, created_at
)
critical_value_alert (  -- 危急值告警
    result_id, organism_name, alert_reason, alert_level,
    notify_methods, notify_status, confirm_time, escalate_count
)
```

---

## 6. LIS 双向通信

### 6.1 国内 LIS 对接现状与应对

| 对接方式 | 占比估计 | 应对 |
|----------|----------|------|
| HL7 v2.x | ~30% | 内建 MLLP/TCP Channel + HL7 Splitter |
| ASTM E1394 | ~20% | 复用仪器层的 AstmSplitter |
| TCP/文件+自定义格式 | ~35% | CustomFileChannel + 配置化字段映射 |
| WebService/HTTP+XML | ~10% | HttpChannel + XML/JSON 解析 |
| 数据库中间表 | ~5% | DB Channel（定制项目） |

**核心策略：字段映射全部配置化，每家新医院只配置不改代码。**

### 6.2 架构

```
LIS Gateway
  ├── Channel: HL7(MLLP) / HTTP / File / Custom
  ├── MessageTransformer: LIS字段 ↔ UnifiedResult 双向映射
  └── 功能: 订单管理 │ 结果回传 │ 状态同步 │ 对账重发
```

### 6.3 LIS 配置表

```sql
lis_config (
    hospital_code, channel_type, channel_config JSON,     -- 通信参数
    order_mapping JSON, test_code_map JSON,                -- 下行映射
    result_mapping JSON, organism_code_map JSON, antibiotic_code_map JSON, -- 上行映射
    retry_policy JSON, ack_timeout_sec, enabled
)
```

### 6.4 消息可靠性

```
发送结果 → 写入 outbound_message 表(PENDING)
  → RabbitMQ: myla.lis / outbound.msg → LIS Channel 消费
    → 成功: ACK + 更新状态为 SENT
    → 失败: NACK + 重新入队 (指数退避, 最多3次)
      → 耗尽: 转入 outbound.dlq 死信队列 → 管理界面人工重发/告警
```

---

## 7. RabbitMQ 消息管道

RabbitMQ 作为异步消息中枢，Redis 退回纯缓存+Session。

### 7.1 六大管道

| Exchange | 核心队列 | 用途 | 可靠性 |
|----------|----------|------|--------|
| `myla.instrument` | raw.message, result.parsed, driver.status, instrument.telemetry | 仪器数据流 + 遥测 | 高 |
| `myla.workflow` | lab.event, rule.action | 工作流事件驱动 | 中 |
| `myla.lis` | outbound.msg, outbound.dlq | LIS 外发 + 死信兜底 | 极高 |
| `myla.notification` | notify.sms, notify.email | 消息通知 | 中 |
| `myla.report` | report.gen, report.sched | 报告生成/调度 | 低 |
| `myla.system` | audit.write | 审计日志异步批量写入 | 高 |

### 7.2 可靠性配置

| 参数 | 值 |
|------|----|
| 消息持久化 | `delivery_mode=2` |
| 队列持久化 | `durable=true` |
| 发布确认 | `publisher confirm` |
| 消费确认 | `manual ack`（处理后手动确认） |
| 死信队列 | 每业务队列配 DLQ |
| 重试策略 | 指数退避：1min / 5min / 15min，最多 3 次 |

---

## 8. 报告引擎

### 8.1 报告类型

| 一期 | 二期 | 三期 |
|------|------|------|
| 检验报告单 | 危急值汇总报告 | Antibiogram (累积药敏) |
| 日志查询导出 | 阳性血培养追踪 | MDRO 监测报告 |
| | 质控报告 | 血培养污染率 / TAT 分析 |
| | 工作量统计 | 院感监测 (HAI) |

### 8.2 技术选型

| 组件 | 用途 |
|------|------|
| JasperReports | 复杂表格+图表报告模板，输出 PDF |
| Apache POI | 复杂格式 Excel |
| EasyExcel | 大数据量快速 Excel 导出 |
| Quartz | 定时报告自动生成+分发（每周一 8:00 自动邮件发送周报） |

---

## 9. 权限 & 审计

### 9.1 RBAC 模型

```
user ─┬── user_role ── role ── role_permission ── permission
      │
      └── DataScope: 数据范围控制（科室级别）
```

核心角色：微生物组长、检验技师、审核医师、系统管理员。

### 9.2 审计日志

```sql
audit_log (
    user_id, user_name, action, resource_type, resource_id,
    detail JSON,         -- 变更前后值 (diff)
    client_ip, session_id, created_at
)
```

**原则：** 异步写入（RabbitMQ），只追加不修改不删除。

---

## 10. 部署架构

```
                    医院内网 (三级等保)
┌────────────────────────────────────────────────────────┐
│                                                        │
│  浏览器客户端 (检验科/微生物科/ICU 的 PC)                │
│       │                                                │
│       ▼                                                │
│  ┌──────────────────┐                                  │
│  │  应用服务器        │  Spring Boot Jar :8080           │
│  │  (4C8G × 1, 可扩展)│                                 │
│  └──────┬───────────┘                                  │
│         │                                              │
│  ┌──────▼──────┐  ┌──────────────┐  ┌──────────┐      │
│  │  MySQL 8.0  │  │ RabbitMQ 3.x │  │  Redis   │      │
│  │  8C16G SSD  │  │ 异步消息中枢  │  │  缓存    │      │
│  └─────────────┘  └──────────────┘  └──────────┘      │
│                                                        │
│  ┌──────────┐  ┌──────────┐                            │
│  │ 仪器群    │  │ LIS服务器 │                            │
│  │(TCP/串口)│  │(HL7/TCP)│                            │
│  └──────────┘  └──────────┘                            │
│                                                        │
│  ────防火墙(仅出站)────────                              │
│     远程运维通道 (VPN/堡垒机)                            │
└────────────────────────────────────────────────────────┘
```

**部署规格（中型三甲医院）：**

| 组件 | 最低配置 |
|------|----------|
| 应用服务器 | 4C8G |
| MySQL | 8C16G + SSD 500G |
| Redis | 2C4G |
| RabbitMQ | 2C4G |

**交付形式：** 离线 RPM/DEB 包 + Docker Compose + 一键部署脚本 (`install.sh`)。

---

## 11. 项目模块结构

```
myla/
├── myla-common/                    # 公共模块
│   ├── myla-common-core/           # 基础工具、异常、常量
│   ├── myla-common-security/       # 权限、审计注解
│   └── myla-common-api/            # 统一DTO、接口定义
│
├── myla-platform/                  # 业务模块
│   ├── myla-platform-gateway/      # 仪器集成层
│   │   ├── gateway-core/           #   SPI框架、DriverContext、InstrumentHub
│   │   ├── gateway-channel/        #   TcpChannel, FileChannel, SerialChannel
│   │   ├── gateway-splitter/       #   AstmSplitter, Hl7Splitter
│   │   ├── gateway-protocol/       #   ★ 自有协议SDK (帧编解码/指令定义/遥测定义)
│   │   ├── gateway-device-mgmt/    #   ★ 设备管理 (注册/发现/固件/遥测看板)
│   │   └── gateway-drivers/        #   各仪器驱动
│   │       ├── driver-vitek2/      #   第三方仪器
│   │       ├── driver-bact-alert/  #   第三方仪器
│   │       └── driver-proprietary/ #   ★ 自家仪器通用驱动
│   ├── myla-platform-workflow/     # 工作流引擎
│   ├── myla-platform-sample/       # 样本管理
│   ├── myla-platform-result/       # 结果管理
│   ├── myla-platform-lis/          # LIS网关
│   ├── myla-platform-report/       # 报告引擎
│   ├── myla-platform-quality/      # 质控管理
│   ├── myla-platform-notification/ # 消息通知
│   └── myla-platform-admin/        # 系统管理
│
├── myla-server/                    # 启动模块
│   ├── MylaApplication.java
│   └── resources/application*.yml
│
├── myla-web/                       # 前端 (Vue 3 + Element Plus)
│   └── src/views/ {dashboard, sample, result, report, instrument, quality, system}
│
├── myla-deploy/                    # 部署资源
│   ├── docker/docker-compose.yml
│   ├── sql/ (init + migration)
│   └── scripts/install.sh
│
└── docs/
    ├── architecture.md
    ├── instrument-driver-guide.md
    └── lis-integration-guide.md
```

**模块依赖规则：** 严格单向。`myla-platform-*` 之间不相互依赖，通过 `myla-common-api` 通信。跨模块调用走 Spring 事件或直接注入 Service 接口。

---

## 12. 技术选型汇总

| 层面 | 选型 | 说明 |
|------|------|------|
| 基础框架 | Spring Boot 3.x + JDK 17 | 团队主力技术栈 |
| ORM | MyBatis-Plus | 复杂统计 SQL 友好 |
| 数据库 | MySQL 8.0 | InnoDB，支持主从（可选） |
| 缓存/Session | Redis | 纯缓存，不做队列 |
| 消息中间件 | RabbitMQ 3.x | 异步消息中枢，接管所有队列 |
| 前端 | Vue 3 + Element Plus | 国内生态好，医院终端兼容 |
| 规则引擎 | MVEL(一期) → Drools(二期) | 渐进演进 |
| 报告 | JasperReports + POI + EasyExcel | 医疗行业标准 |
| 定时任务 | Quartz | Spring 原生集成 |
| 日志 | Logback | 运行日志文件 + 审计日志进 MySQL |
| API 文档 | SpringDoc (OpenAPI 3.0) | 自动生成 |
| 构建 | Maven 多模块 | 模块化单体 |
| 部署 | 离线包 + Docker Compose | 一键安装 |

---

## 13. 关键数据表总览

| 数据域 | 核心表 | 日增量估计 |
|--------|--------|-----------|
| 样本 | sample, sample_test, sample_tracking | 200-500 |
| 结果 | unified_result, organism_id, ast_result | 1000-3000 |
| 仪器 | raw_message, instrument_log, instrument_status, instrument_registry, instrument_telemetry, firmware_upgrade_log | ~10000+ |
| 工作流 | workflow_rule, critical_value_alert, worklist | 少量 |
| 报告 | report_template, report_schedule, outbound_message | ~200 |
| LIS | lis_config, outbound_message | 少量配置 |
| 系统 | user, role, permission, audit_log | 少量 |
| 质控 | qc_strain, qc_result, qc_rule | ~50 |
| 码表 | organism_dict, antibiotic_dict, specimen_dict, hospital_dict | 静态 |

**数据保留策略：**
- 业务数据：≥5 年
- 原始报文 (raw_message)：可配置（默认 1 年，可归档）
- 审计日志：≥3 年（法规要求）
- 备份：全量(日) + binlog(实时) + 异地冷备
