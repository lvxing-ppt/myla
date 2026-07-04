# MyLA 仪器接入开发指南

> v1.0 | 2026-07-04 | 适用于一期 MVP 框架

---

## 1. 架构概述

MyLA 网关采用三层解耦的 SPI 插件架构，新增仪器适配器 = 实现 3 个接口 + 1 个驱动类：

```
┌────────────────────────────────────────────────┐
│           InstrumentDriver (驱动)               │
│   组合 Channel + Splitter + Parser              │
├──────────┬─────────────────┬──────────────────┤
│ Channel  │   Splitter      │    Parser        │
│ 通信通道  │   帧切分器       │   数据解析器      │
│          │                 │                  │
│ TcpChannel│  AstmSplitter  │  Vitek2Parser    │
│FileChannel│  Hl7Splitter   │  GenericASTMParser│
│NettyTcpC │  RawPassThru   │  自定义Parser     │
├──────────┴─────────────────┴──────────────────┤
│              DriverContext (基础设施)           │
│   saveRawMessage | publishResult | reportHealth│
└────────────────────────────────────────────────┘

通信方式 (Channel) × 帧格式 (Splitter) × 业务协议 (Parser) = 可自由组合
```

**核心原则**：Channel 只管收发字节，Splitter 只管切帧，Parser 只管转成 UnifiedResult。三层完全解耦，复用已有时直接拿来用。

---

## 2. 已有可复用组件

### 2.1 Channel（通信通道）

| 类 | 适用场景 | 关键参数 |
|----|---------|---------|
| [TcpChannel](file://g:/myla/myla-platform/myla-platform-gateway/gateway-channel/src/main/java/com/myla/gateway/channel/TcpChannel.java) | 仪器主动连接推送数据 | `port` — 监听端口 |
| [NettyTcpChannel](file://g:/myla/myla-platform/myla-platform-gateway/gateway-channel/src/main/java/com/myla/gateway/channel/NettyTcpChannel.java) | 高并发 TCP 场景（NIO） | `port` — 监听端口 |
| [FileChannel](file://g:/myla/myla-platform/myla-platform-gateway/gateway-channel/src/main/java/com/myla/gateway/channel/FileChannel.java) | 仪器通过文件导出数据 | `directory`, `filePattern`, `pollIntervalMs` |

**选择指南**：
- 仪器作为 TCP client 推送数据 → `TcpChannel`
- 仪器写入共享目录的 txt/hl7 文件 → `FileChannel`
- 需要双向通信（发指令+收结果）→ `TcpChannel` (通过 `send()` 方法)

### 2.2 Splitter（帧切分器）

| 类 | 适用协议 | 帧边界 |
|----|---------|--------|
| [AstmSplitter](file://g:/myla/myla-platform/myla-platform-gateway/gateway-splitter/src/main/java/com/myla/gateway/splitter/AstmSplitter.java) | ASTM E1381/E1394 | STX(0x02)...ETX(0x03)/ETB(0x17) |
| [Hl7Splitter](file://g:/myla/myla-platform/myla-platform-gateway/gateway-splitter/src/main/java/com/myla/gateway/splitter/Hl7Splitter.java) | HL7 v2.x MLLP | VT(0x0B)...FS(0x1C)+CR(0x0D) |

如果仪器数据是完整帧（比如每行一个 JSON，换行分隔），可以不用 Splitter（直接在 Driver 里处理 raw bytes）。

### 2.3 Parser（数据解析器）

| 类 | 适用场景 |
|----|---------|
| [Vitek2Parser](file://g:/myla/myla-platform/myla-platform-gateway/gateway-drivers/driver-vitek2/src/main/java/com/myla/gateway/driver/vitek2/Vitek2Parser.java) | VITEK 2 ASTM 格式 (O\|/R\| 管道分隔) |

如果仪器是其他格式，需要自己实现 `DataParser` 接口。

---

## 3. 新增仪器 — 4 步上手

### Step 1: 分析仪器通信方式

回答三个问题：
1. **怎么连**？TCP 主动推送 / 文件导出 / 串口 / HTTP
2. **什么格式**？ASTM / HL7 / 私有二进制 / JSON / CSV
3. **数据内容**？菌种鉴定 + 药敏结果，还是有其他类型

### Step 2: 创建 Driver 模块

以接入 BD BACTEC FX 血培养仪（举例）为例：

```
myla-platform/myla-platform-gateway/gateway-drivers/
└── driver-bactec/
    ├── pom.xml
    └── src/main/java/com/myla/gateway/driver/bactec/
        ├── BactecDriver.java       ← 实现 InstrumentDriver
        └── BactecParser.java       ← 实现 DataParser (如果需要自定义解析)
```

**pom.xml 模板：**
```xml
<project xmlns="http://maven.apache.org/POM/4.0.0" ...>
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.myla</groupId>
        <artifactId>gateway-drivers</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>driver-bactec</artifactId>
    <name>Driver BD BACTEC FX</name>

    <dependencies>
        <dependency>
            <groupId>com.myla</groupId>
            <artifactId>gateway-core</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.myla</groupId>
            <artifactId>gateway-channel</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.myla</groupId>
            <artifactId>gateway-splitter</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
    </dependencies>
</project>
```

### Step 3: 实现 InstrumentDriver

参考 [Vitek2Driver.java](file://g:/myla/myla-platform/myla-platform-gateway/gateway-drivers/driver-vitek2/src/main/java/com/myla/gateway/driver/vitek2/Vitek2Driver.java)，核心模板：

```java
@Slf4j
public class BactecDriver implements InstrumentDriver {

    // 1. 组合需要的组件（直接用现成的或自己写）
    private final TcpChannel channel = new TcpChannel();        // 复用
    private final Hl7Splitter splitter = new Hl7Splitter();     // 复用
    private final BactecParser parser = new BactecParser();     // 自写

    private DriverConfig config;
    private DriverContext ctx;
    private DataEventListener listener;

    // 2. 元信息
    @Override public String getDriverId() { return "bactec-v1.0"; }
    @Override public String getDisplayName() { return "BD BACTEC FX Driver"; }
    @Override public String getVersion() { return "1.0"; }
    @Override public CommunicationMode getMode() { return CommunicationMode.PASSIVE_LISTEN; }

    // 3. 初始化
    @Override public void initialize(DriverConfig config) { this.config = config; }

    // 4. 启动 — 最关键的方法
    @Override
    public void start(DriverContext ctx) {
        this.ctx = ctx;
        List<byte[]> incompleteFrames = new ArrayList<>();

        channel.setMessageListener(rawBytes -> {
            try {
                // 4a. 保存原始报文
                ctx.saveRawMessage(config.getInstrumentId(), "HL7", rawBytes);

                // 4b. 分桢
                List<byte[]> frames = splitter.splitFrames(rawBytes, incompleteFrames);

                // 4c. 逐帧解析
                for (byte[] frame : frames) {
                    try {
                        List<UnifiedResult> results = parser.parse(frame);
                        for (UnifiedResult result : results) {
                            result.setInstrumentId(config.getInstrumentId());
                            ctx.publishResult(rawBytes);   // 发布到 MQ
                            if (listener != null) {
                                listener.onResultReceived(result); // 回调上层
                            }
                        }
                    } catch (Exception e) {
                        if (listener != null) {
                            listener.onParseFailed(new String(frame), e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                if (listener != null) {
                    listener.onConnectionError(config.getInstrumentId(), e.getMessage(), 1);
                }
            }
        });

        // 4d. 设置错误监听
        channel.setErrorListener(error -> {
            ctx.reportHealth(config.getInstrumentId(), "ERROR", error.getMessage());
            if (listener != null) {
                listener.onConnectionError(config.getInstrumentId(), error.getMessage(), 1);
            }
        });

        // 4e. 打开通道
        channel.open(config.getChannel());
        ctx.reportHealth(config.getInstrumentId(), "ONLINE", "BactecDriver started");
    }

    // 5. 停止
    @Override public void stop() {
        channel.close();
        if (ctx != null) ctx.reportHealth(config.getInstrumentId(), "OFFLINE", "Stopped");
    }

    @Override public boolean testConnection() { return channel.isOpen(); }
    @Override public void registerListener(DataEventListener listener) { this.listener = listener; }

    @Override
    public DiscoveryInfo getDiscoveryInfo() {
        DiscoveryInfo info = new DiscoveryInfo();
        info.setManufacturer("BD");
        info.setModel("BACTEC FX");
        info.setSerialNumber("N/A");
        info.setFirmwareVersion("N/A");
        info.setHardwareRevision("N/A");
        info.setSupportedCommands(List.of());
        return info;
    }
}
```

### Step 4: 注册驱动 + 配置

**4a. 在 GatewayBootstrap 注册 Driver：**

```java
// GatewayBootstrap.java 的 createDriver() 方法中添加
case "bactec-v1.0" -> new com.myla.gateway.driver.bactec.BactecDriver();
```

**4b. 在 myla-server pom.xml 添加依赖：**
```xml
<dependency>
    <groupId>com.myla</groupId>
    <artifactId>driver-bactec</artifactId>
    <version>${project.version}</version>
</dependency>
```

**4c. 在 application-dev.yml 添加仪器配置：**
```yaml
myla:
  gateway:
    instruments:
      - driver-id: vitek2-v1.0        # 已有的 VITEK 2
        instrument-id: VITEK2-LAB1-001
        channel:
          type: TCP
          port: 19001
        splitter-type: ASTM
        parser-type: vitek2-parser

      - driver-id: bactec-v1.0         # 新增的 BACTEC
        instrument-id: BACTEC-LAB1-001
        channel:
          type: TCP
          port: 19002                  # 不同端口
        splitter-type: HL7-MLLP
        parser-type: bactec-parser
```

---

## 4. 实现 DataParser — 数据格式适配

`DataParser` 接口只有一个方法：

```java
public interface DataParser {
    String getParserId();
    List<UnifiedResult> parse(byte[] frame) throws ParseException;
}
```

输入是 Splitter 切好的完整帧（byte[]），输出是 `List<UnifiedResult>`。

### UnifiedResult 字段说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| instrumentId | String | 是 | 由 Driver 自动设置，Parser 不需要填 |
| sampleBarcode | String | 是 | 样本条码，关联 sample 表 |
| patientId | String | 否 | 患者 ID |
| patientName | String | 否 | 患者姓名 |
| resultType | ResultType | 是 | BLOOD_CULTURE_FLAG / ORGANISM_ID / AST / QC |
| organismCode | String | 否 | 菌种编码（标准编码） |
| organismName | String | 否 | 菌种名称 |
| identificationPercent | Double | 否 | 鉴定置信度 (0.0-100.0) |
| astResults | List\<AstResultDTO\> | 否 | 药敏结果列表 |
| testTime | LocalDateTime | 是 | 检验时间 |
| rawMessage | String | 否 | 原始报文文本 |

### AstResultDTO 字段说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| antibioticCode | String | 否 | 抗生素编码 |
| antibioticName | String | 是 | 抗生素名称 |
| micValue | Double | 是 | MIC 值 |
| micUnit | String | 否 | 单位 (ug/mL, mg/L) |
| machineSIR | String | 是 | 仪器原始 SIR 判定: "S"/"I"/"R" |
| manualSIR | String | 否 | 人工修正 SIR（暂不填） |
| finalSIR | String | 是 | 最终 SIR（初始 = machineSIR） |
| expertRuleComment | String | 否 | 专家规则备注（暂不填） |

### 示例：解析 CSV 格式

```java
public class CsvParser implements DataParser {
    @Override public String getParserId() { return "csv-parser"; }

    @Override
    public List<UnifiedResult> parse(byte[] frame) throws ParseException {
        String text = new String(frame, StandardCharsets.UTF_8).trim();
        String[] lines = text.split("\n");

        UnifiedResult result = new UnifiedResult();
        result.setResultType(ResultType.AST);
        result.setTestTime(LocalDateTime.now());
        List<AstResultDTO> astList = new ArrayList<>();

        for (String line : lines) {
            String[] cols = line.split(",");
            // cols[0]=barcode, cols[1]=organism, cols[2]=antibiotic, cols[3]=mic, cols[4]=sir
            if (cols[0].startsWith("SAMPLE:")) {
                result.setSampleBarcode(cols[1]);
                result.setOrganismName(cols[2]);
            } else {
                AstResultDTO ast = new AstResultDTO();
                ast.setAntibioticName(cols[0]);
                ast.setMicValue(Double.parseDouble(cols[1]));
                ast.setMachineSIR(cols[2]);
                ast.setFinalSIR(cols[2]);
                astList.add(ast);
            }
        }
        result.setAstResults(astList);
        result.setRawMessage(text);
        return List.of(result);
    }
}
```

---

## 5. 通信模式说明

| 模式 | CommunicationMode | 说明 | 示例仪器 |
|------|------------------|------|---------|
| 被动监听 | PASSIVE_LISTEN | 网关开端口，仪器主动连 | VITEK 2, BACTEC |
| 主动轮询 | ACTIVE_POLL | 网关定时查文件目录 | 通过文件导出的仪器 |
| 主动连接 | ACTIVE_CONNECT | 网关主动 TCP 连接仪器 | 自有协议仪器 |

---

## 6. 数据处理管道总结

```
仪器 (TCP/串口/文件)
  │
  ▼
CommunicationChannel.open()
  │  接收原始字节
  ▼
FrameSplitter.splitFrames()
  │  STX/ETX 或 VT/FS+CR 切帧
  ▼
DataParser.parse()
  │  将帧转为 UnifiedResult
  ▼
InstrumentDriver (编排者)
  ├─ ctx.saveRawMessage()      → raw_message 表
  ├─ ctx.publishResult()       → MQ (myla.instrument/result.parsed)
  └─ listener.onResultReceived() → 写库 + 触发工作流
       ├─ organism_result 表
       ├─ ast_result 表
       └─ MQ 发布 AST_RESULT_RECEIVED
            └─ 工作流引擎 (CLSI 规则 → SIR修正 + 危急值)
```

---

## 7. 快速检查清单

接入新仪器时，确认以下项：

- [ ] 确定了通信方式（TCP/文件/串口）
- [ ] 确定了数据格式（ASTM/HL7/JSON/CSV/私有二进制）
- [ ] 已有 Channel 可复用？→ 是，直接用
- [ ] 已有 Splitter 可复用？→ 是，直接用
- [ ] Parser 需要新写吗？→ 是，实现 `DataParser` 接口
- [ ] 创建了 Driver 模块（pom.xml + Driver类 + Parser类）
- [ ] Driver 类实现了 `InstrumentDriver` 接口的 10 个方法
- [ ] 在 `GatewayBootstrap.createDriver()` 注册了 driverId 映射
- [ ] 在 `application-dev.yml` 添加了仪器配置
- [ ] 在 `myla-server/pom.xml` 添加了依赖
- [ ] 启动服务，仪器连接测试端口验证
