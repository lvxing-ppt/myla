# LIS 对接完整实现 — 设计文档

> 日期：2026-08-09 | 状态：待审核

## 1. 概述

完善 `myla-platform-lis` 模块，实现与医院 LIS 系统的双向对接。

- **入站**：从 LIS 接收检验医嘱（HL7 ORM^O01）和患者更新（HL7 ADT），自动创建/更新 Sample
- **出站**：将已发布的检验结果构造为 HL7/ASTM/JSON 消息，通过配置的通道实际发送到外部 LIS

### 当前状态（一期）

| 方向 | 状态 |
|---|---|
| 出站 | 骨架完成。`OutboundMessageConsumer` 只写库标记 SENT，**未实际发送** |
| 入站 | 仅有 `LisInboundService.NoOp` 占位实现，**完全不可用** |
| HL7 解析 | 无 HAPI 依赖，仅能做 MLLP 帧切分 |

### 目标状态（本设计）

| 方向 | 目标 |
|---|---|
| 出站 | 收到 `RESULT_RELEASED_TO_LIS` 事件后，按医院配置的真实通道（MLLP/ASTM/HTTP）发送消息 |
| 入站 | 按医院启动 MLLP 监听端口或文件监听，解析 HL7/ASTM 消息，创建 Sample |

---

## 2. 架构

```
myla-platform-lis/
├── entity/
│   ├── LisConfig.java          ← 已有
│   └── OutboundMessage.java    ← 已有
├── mapper/
│   ├── OutboundMessageMapper.java ← 已有
│   └── LisConfigMapper.java       ← 新增
├── inbound/
│   ├── LisInboundService.java     ← 已有接口，去掉 NoOp
│   ├── LisInboundServiceImpl.java ← 新增：真实实现
│   ├── LisInboundServer.java      ← 新增：MLLP TCP 监听器
│   ├── Hl7OrderParser.java        ← 新增：ORM^O01 解析
│   ├── Hl7AdtParser.java          ← 新增：ADT 解析
│   └── FieldMapper.java           ← 新增：字段映射
├── outbound/
│   ├── LisOutboundSender.java     ← 新增：发送策略接口
│   ├── Hl7MllpSender.java         ← 新增：HL7 MLLP 发送
│   ├── AstmTcpSender.java         ← 新增：ASTM TCP 发送
│   ├── HttpSender.java            ← 新增：HTTP 发送
│   └── Hl7MessageBuilder.java     ← 新增：构造 HL7 消息
├── service/
│   ├── LisGatewayService.java     ← 已有
│   └── impl/LisGatewayServiceImpl.java ← 已有
├── consumer/
│   └── OutboundMessageConsumer.java   ← 改造：接入 sender
└── config/
    └── LisAutoConfiguration.java     ← 新增：自动配置
```

### 模块依赖

```
myla-platform-lis
  ├── myla-common (LabEvent, UnifiedResult, ResultCode)
  ├── myla-platform-sample (SampleService)
  ├── myla-platform-result (ResultService, 查询已发布结果)
  ├── Spring Boot AMQP (RabbitMQ)
  ├── MyBatis-Plus
  ├── HAPI (ca.uhn.hapi:hapi-base + hapi-structures-v25)
  └── Lombok
```

---

## 3. 入站设计

### 3.1 LisInboundServer — TCP MLLP 监听器

```java
// 为每个启用的医院启动一个 MLLP 监听端口
// 生命周期：@PostConstruct 启动所有 → @PreDestroy 关闭所有
public class LisInboundServer implements DisposableBean {
    // Map<hospitalCode, ListeningThread>
    private final Map<String, Thread> listeners = new ConcurrentHashMap<>();

    @PostConstruct
    void startAll() {
        // 查询 lis_config WHERE channel_type='HL7' AND enabled=1
        // 为每条记录启动一个 ServerSocket，监听 channel_config.port
        // 收到 MLLP 连接 → 读取 HL7 消息 → 调用 LisInboundService
    }
}
```

- 每个医院独立端口，从 `channel_config.port` 读取
- 连接超时 30s，空闲超时 5min
- MLLP 帧格式：`VT(0x0B) [HL7] FS(0x1C) CR(0x0D)`
- 收到消息 → 解析 → 处理 → 发送 HL7 ACK（MSA 段）

### 3.2 Hl7OrderParser — ORM^O01 解析

```java
// 基于 HAPI 解析 HL7 ORM^O01 消息，提取 Sample 所需字段
public class Hl7OrderParser {
    // 输入: HL7 消息字符串
    // 输出: Sample 实体（部分填充，待 FieldMapper 映射）
    Sample parse(String hl7Message);
    // 提取: PID-3(patientId), PID-5(patientName), PID-8(gender),
    //       OBR-2(barcode), OBR-4(testCode), OBR-15(specimenType),
    //       OBR-7(collectTime), PV1-3(wardCode)
}
```

### 3.3 Hl7AdtParser — ADT 解析

```java
// 基于 HAPI 解析 HL7 ADT^A04/A08 消息，更新患者信息
public class Hl7AdtParser {
    // 返回: Map<fieldName, newValue> 用于更新 Sample
    Map<String, Object> parse(String hl7Message);
}
```

### 3.4 FieldMapper — 字段映射

```java
// 根据 lis_config.order_mapping JSON 做 LIS字段 → Sample字段 的转换
public class FieldMapper {
    // order_mapping 格式: {"patientId": "PID-3-1", "barcode": "OBR-31", ...}
    Sample apply(Sample sample, Map<String, String> rawFields, String mappingJson);
}
```

### 3.5 LisInboundServiceImpl

```java
@Service
public class LisInboundServiceImpl implements LisInboundService {

    @Override
    public Sample receiveOrder(String hospitalCode, byte[] rawMessage, String messageType) {
        // 1. HAPI 解析 HL7 消息
        // 2. 查 lis_config 获取该医院的 order_mapping
        // 3. FieldMapper 映射 → Sample
        // 4. 设置 sample.sourceSystem = "LIS"
        // 5. SampleService.register(sample)
        // 6. 返回 Sample（含自动生成的 sampleId）
    }

    @Override
    public void receivePatientUpdate(String hospitalCode, byte[] rawMessage) {
        // 1. HAPI 解析 ADT^A04/A08
        // 2. 提取 PID-3(patientId)
        // 3. 查找该 patientId 下所有非终态 Sample
        // 4. 更新 patientName/gender/ward/wardName/diagnosis 等
    }

    @Override
    public Sample findByBarcode(String barcode) {
        return sampleService.getByBarcode(barcode);
    }
}
```

### 3.6 入站消息流

```
LIS 系统
  │  TCP MLLP (VT...FS CR)
  ▼
LisInboundServer (per-hospital port，端口→hospitalCode 映射)
  │  切 VT/FS+CR 帧，提取 HL7 消息字符串
  ▼
LisInboundServiceImpl.receiveOrder(hospitalCode, rawBytes, "HL7")
  │  HAPI 解析 MSH/PID/OBR
  ▼
Hl7OrderParser → 提取原始字段
  │
  ▼
FieldMapper (order_mapping JSON) → Sample
  │
  ▼
SampleService.register(sample)
  │  生成 sampleId, INSERT sample, INSERT sample_tracking
  │  发布 SAMPLE_REGISTERED → RabbitMQ
  ▼
LisInboundServer 回 HL7 ACK (MSA^AA)
```

---

## 4. 出站设计

### 4.1 LisOutboundSender — 发送策略接口

```java
public interface LisOutboundSender {
    /** 发送通道类型，与 lis_config.channel_type 匹配 */
    String getChannelType();

    /** 发送消息到 LIS */
    SendResult send(OutboundMessage msg, LisConfig config);

    /** 测试连接 */
    boolean testConnection(LisConfig config);
}

@Data
class SendResult {
    boolean success;
    String error;
}
```

### 4.2 Hl7MllpSender

- 从 `channel_config` 读取 IP:Port
- 建立 TCP 连接，发送 MLLP 帧 `VT + HL7 + FS + CR`
- 等待 ACK（`timeout = ack_timeout_sec`）
- 验证 MSH-9 和 MSA-1
- 关闭连接（短连接模式，每次发送一个连接）

### 4.3 AstmTcpSender

- 类似 MLLP 但使用 ASTM 帧格式（STX...ETX）
- 发送后等待 ACK(0x06) 或 NAK(0x15)

### 4.4 HttpSender

- HTTP POST JSON 到 `channel_config.url`
- 请求体为 `{hospitalCode, messageType, messageContent}`
- 验证 HTTP 200 响应

### 4.5 Hl7MessageBuilder

```java
// 将内部 UnifiedResult + Sample 构造为 HL7 ORU^R01 消息
public class Hl7MessageBuilder {
    String buildOruR01(UnifiedResult result, Sample sample, LisConfig config);
    // MSH-9 = "ORU^R01", MSH-12 = "2.5"
    // PID 段 ← Sample 患者信息
    // OBR 段 ← Sample 条码/医嘱
    // OBX 段 ← result 检验结果
}
```

### 4.6 OutboundMessageConsumer 改造

```java
// 现有逻辑：标记 SENT 即返回
// 改造后：注入 List<LisOutboundSender>，按 channelType 选择 sender 真正发送
@Component
public class OutboundMessageConsumer {
    private final List<LisOutboundSender> senders; // 自动注入所有实现
    private final LisConfigMapper configMapper;

    @RabbitListener(queues = "outbound.msg")
    public void onOutboundMessage(OutboundMessage msg, Message message,
                                   Channel channel, long deliveryTag) {
        try {
            LisConfig config = configMapper.selectByHospitalCode(msg.getHospitalCode());
            LisOutboundSender sender = senders.stream()
                .filter(s -> s.getChannelType().equals(config.getChannelType()))
                .findFirst().orElseThrow();

            SendResult result = sender.send(msg, config);
            if (result.isSuccess()) {
                msg.setSendStatus("SENT");
                msg.setSentAt(LocalDateTime.now());
                messageMapper.updateById(msg);
                channel.basicAck(deliveryTag, false);
            } else {
                handleFailure(msg, channel, deliveryTag);
            }
        } catch (Exception e) {
            handleFailure(msg, channel, deliveryTag);
        }
    }
}
```

### 4.7 出站触发流程

```
ResultService.reviewResult(id, RELEASED)
  │  发布 RESULT_RELEASED_TO_LIS
  ▼
LabEventConsumer → WorkflowEngine
  │  匹配 WorkflowRule (eventType = RESULT_RELEASED_TO_LIS)
  │  → action = "SEND_TO_LIS"
  ▼
LisGatewayService.sendResult(hospitalCode, hl7Message)
  │  写 outbound_message (PENDING)
  ▼
RabbitMQ → OutboundMessageConsumer
  │  选 LisOutboundSender
  ▼
Hl7MllpSender / HttpSender 等
  │  真实发送
  ▼
SENT / FAILED (重试 or DLQ)
```

---

## 5. 配置管理

### 5.1 LisConfigMapper

```java
@Mapper
public interface LisConfigMapper extends BaseMapper<LisConfig> {
    // 按医院编码查询
    LisConfig selectByHospitalCode(String hospitalCode);
    // 查询所有已启用的入站配置
    List<LisConfig> selectEnabledInbound();
}
```

### 5.2 LisAutoConfiguration

```java
@Configuration
@ComponentScan("com.myla.lis")
@MapperScan("com.myla.lis.mapper")
public class LisAutoConfiguration {
    // 注册 LisInboundServer、FieldMapper、各 Sender 等 Bean
}
```

---

## 6. 错误处理

| 场景 | 处理 |
|---|---|
| HL7 消息格式不正确 | 返回 HL7 MSA^AR（Application Reject），记录错误日志 |
| 条码重复 | 返回 HL7 MSA^AE（Application Error），业务异常不重试 |
| TCP 连接超时 | 重试（最多 3 次），超限 → FAILED → DLQ |
| LIS 无响应 | 按 `lis_config.retry_policy` 重试，超限 → FAILED |
| 端口被占用 | LisInboundServer 启动时捕获，记录 WARN 日志，等待 30s 后重试 |

---

## 7. 数据库变更

无需 DDL 变更。`lis_config` 和 `outbound_message` 表已满足需求。

---

## 8. 依赖变更

`myla-platform/myla-platform-lis/pom.xml` 新增：

```xml
<!-- HAPI HL7 v2.x -->
<dependency>
    <groupId>ca.uhn.hapi</groupId>
    <artifactId>hapi-base</artifactId>
    <version>2.3</version>
</dependency>
<dependency>
    <groupId>ca.uhn.hapi</groupId>
    <artifactId>hapi-structures-v25</artifactId>
    <version>2.3</version>
</dependency>
```

---

## 9. 文件清单

| 文件 | 操作 | 说明 |
|---|---|---|
| `LisConfigMapper.java` | 新增 | MyBatis-Plus mapper |
| `LisInboundServer.java` | 新增 | TCP MLLP 监听 |
| `Hl7OrderParser.java` | 新增 | ORM^O01 解析 |
| `Hl7AdtParser.java` | 新增 | ADT 解析 |
| `FieldMapper.java` | 新增 | 字段映射 |
| `LisInboundServiceImpl.java` | 新增 | 替换 NoOp |
| `LisOutboundSender.java` | 新增 | 发送策略接口 |
| `Hl7MllpSender.java` | 新增 | HL7 MLLP 发送 |
| `AstmTcpSender.java` | 新增 | ASTM TCP 发送 |
| `HttpSender.java` | 新增 | HTTP 发送 |
| `Hl7MessageBuilder.java` | 新增 | 构造 HL7 消息 |
| `LisAutoConfiguration.java` | 新增 | 自动配置 |
| `LisInboundService.java` | 修改 | 去掉 NoOp 内部类 |
| `OutboundMessageConsumer.java` | 修改 | 接入真实 sender |
| `pom.xml` (lis) | 修改 | 加 HAPI 依赖 |
