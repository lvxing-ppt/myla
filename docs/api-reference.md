# MLMS API 接口文档

> v1.0 | 2026-07-04 | Base URL: `http://localhost:8080`

---

## 通用说明

### 统一响应格式

所有接口（除 Excel 下载外）返回统一 JSON 结构：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": { ... }
}
```

| code | 说明 |
|------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未授权 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 500 | 系统内部错误 |
| 1001 | 仪器连接异常 |
| 1003 | 数据解析失败 |
| 2001 | 样本不存在 |
| 2002 | 结果不存在 |
| 2003 | 条码重复 |

### 认证

除 `/api/v1/auth/login` 外，所有接口需在 Header 中携带 JWT Token：

```
Authorization: Bearer <token>
```

---

## 1. 认证模块 — `/api/v1/auth`

### POST `/api/v1/auth/login` — 用户登录

**Request Body:**
```json
{
  "username": "admin",
  "password": "password123"
}
```

**Response (200):**
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIs...",
    "username": "admin"
  }
}
```

**Response (401):**
```json
{
  "code": 401,
  "message": "未授权",
  "data": null
}
```

---

## 2. 样本管理 — `/api/v1/samples`

### POST `/api/v1/samples` — 登记新样本

**Request Body:**
```json
{
  "barcode": "20240001001",
  "patientId": "P20240001",
  "patientName": "张三",
  "gender": "M",
  "age": 45,
  "specimenType": "BLOOD",
  "collectTime": "2026-07-04T08:30:00",
  "priority": "URGENT",
  "wardCode": "ICU-01",
  "wardName": "重症监护室",
  "diagnosis": "发热待查,疑似败血症",
  "sourceSystem": "LIS",
  "comment": "抗生素使用中采集"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| barcode | String | 是 | 样本条码，唯一 |
| patientId | String | 否 | 患者 ID |
| patientName | String | 否 | 患者姓名 |
| gender | String | 否 | 性别: M/F |
| age | Integer | 否 | 年龄 |
| specimenType | String | 否 | 标本类型: BLOOD/URINE/SPUTUM/CSF/... |
| collectTime | DateTime | 否 | 采集时间 ISO格式 |
| priority | String | 否 | NORMAL/URGENT，默认 NORMAL |
| wardCode | String | 否 | 病区编码 |
| wardName | String | 否 | 病区名称 |
| diagnosis | String | 否 | 临床诊断 |
| sourceSystem | String | 否 | 来源: HIS/LIS/MANUAL |
| comment | String | 否 | 备注 |

**Response (200):**
```json
{
  "code": 200,
  "data": {
    "id": 1,
    "sampleId": "20260704-0001",
    "barcode": "20240001001",
    "status": "REGISTERED",
    "priority": "URGENT",
    "createdAt": "2026-07-04T10:00:00"
  }
}
```

系统自动生成 `sampleId`（格式 `yyyyMMdd-xxxx`）和初始状态 `REGISTERED`。

---

### GET `/api/v1/samples/{id}` — 按主键查询

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long (Path) | 数据库主键 |

```
GET /api/v1/samples/1
```

---

### GET `/api/v1/samples/sampleId/{sampleId}` — 按内部编号查询

| 参数 | 类型 | 说明 |
|------|------|------|
| sampleId | String (Path) | 内部编号 yyyyMMdd-xxxx |

```
GET /api/v1/samples/sampleId/20260704-0001
```

---

### GET `/api/v1/samples/barcode/{barcode}` — 按条码查询

| 参数 | 类型 | 说明 |
|------|------|------|
| barcode | String (Path) | 医院条码 |

```
GET /api/v1/samples/barcode/20240001001
```

---

### PUT `/api/v1/samples/{id}/status` — 样本状态流转

**Path:** `/api/v1/samples/1/status`

**Request Body:**
```json
{
  "fromStatus": "REGISTERED",
  "toStatus": "INOCULATED",
  "operator": "王技师",
  "comment": "已接种血平板和巧克力平板"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| fromStatus | String | 是 | 当前状态 |
| toStatus | String | 是 | 目标状态 |
| operator | String | 否 | 操作人，默认 "SYSTEM" |
| comment | String | 否 | 备注 |

**状态流转规则：**
```
REGISTERED → INOCULATED → ANALYZING → APPROVED → RELEASED
```

**Response (200):**
```json
{
  "code": 200,
  "message": "操作成功",
  "data": null
}
```

---

## 3. 结果管理 — `/api/v1/results`

### PUT `/api/v1/results/{id}/review` — 审核结果

**Path:** `/api/v1/results/16/review`

**Request Body:**
```json
{
  "action": "APPROVE",
  "reviewer": "李医师"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| action | String | 是 | APPROVE/REJECT |
| reviewer | String | 否 | 审核人，默认 "SYSTEM" |

**状态约束：** 仅 `PENDING` 状态可审核。

**Response (200):**
```json
{
  "code": 200,
  "message": "操作成功",
  "data": null
}
```

**Response (2004 — 状态异常):**
```json
{
  "code": 2004,
  "message": "样本状态异常不允许此操作",
  "data": null
}
```

---

## 4. 报告管理 — `/api/v1/reports`

### POST `/api/v1/reports/sample/{barcode}/generate` — 生成报告

```
POST /api/v1/reports/sample/20240001001/generate
```

**Response (200):**
```json
{
  "code": 200,
  "message": "操作成功",
  "data": "C:\\Users\\Administrator\\AppData\\Local\\Temp\\myla-reports\\report_20240001001_20260704180248.xlsx"
}
```

报告包含：样本信息 + 菌种鉴定结果 + 药敏明细表。

---

### GET `/api/v1/reports/sample/{barcode}/excel` — 下载报告

```
GET /api/v1/reports/sample/20240001001/excel
```

浏览器直接触发 Excel 文件下载，文件名：`检验报告_{barcode}_{日期}.xlsx`。

---

## 5. API 总览

| 模块 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 认证 | POST | `/api/v1/auth/login` | 用户登录 |
| 样本 | POST | `/api/v1/samples` | 登记新样本 |
| 样本 | GET | `/api/v1/samples/{id}` | 按主键查询 |
| 样本 | GET | `/api/v1/samples/sampleId/{sampleId}` | 按内部编号查询 |
| 样本 | GET | `/api/v1/samples/barcode/{barcode}` | 按条码查询 |
| 样本 | PUT | `/api/v1/samples/{id}/status` | 状态流转 |
| 结果 | PUT | `/api/v1/results/{id}/review` | 审核结果 |
| 报告 | POST | `/api/v1/reports/sample/{barcode}/generate` | 生成报告 |
| 报告 | GET | `/api/v1/reports/sample/{barcode}/excel` | 下载报告 |

---

## 6. 快速调用示例

### curl

```bash
# 登录
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password123"}'

# 登记样本
curl -X POST http://localhost:8080/api/v1/samples \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"barcode":"TEST001","patientName":"张三","specimenType":"BLOOD"}'

# 审核结果
curl -X PUT http://localhost:8080/api/v1/results/16/review \
  -H "Content-Type: application/json" \
  -d '{"action":"APPROVE","reviewer":"李医师"}'

# 下载报告
curl -O http://localhost:8080/api/v1/reports/sample/TEST001/excel
```

### 全周期模拟（无需写代码）

启动服务后直接运行：
```
mvn exec:java -pl oes-server -Dexec.classpathScope=test \
  -Dexec.mainClass=com.mlms.oes.server.gateway.FullCycleDemo
```

自动完成：TCP 发数据 → 入库 → 规则 → 审核 → 报告 全流程。
