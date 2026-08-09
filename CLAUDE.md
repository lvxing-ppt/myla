# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MLMS (Microbiology Laboratory Middleware System) — a clinical microbiology lab automation platform targeting the Chinese market (NMPA Class III medical device registration track). Connects lab instruments, hospital LIS systems, and clinical users for automated data acquisition, intelligent workflow orchestration, result review, and professional reporting.

**Tech stack:** Spring Boot 3.2 + JDK 17 + Maven multi-module, MySQL 8.0, Redis, RabbitMQ, MyBatis-Plus, Vue 3 + Element Plus + Vite.

## Build & Run

```bash
# Build entire project
mvn clean package -DskipTests

# Build a single module
mvn clean package -pl oes-server -am -DskipTests

# Run tests (requires Docker services or H2 fallback)
mvn test

# Run a single test class
mvn test -pl oes-server -Dtest=ClassName

# Start backend (port 8080)
mvn spring-boot:run -pl oes-server

# Start communication layer (port 8081, separate process)
mvn spring-boot:run -pl capl

# Frontend dev server (port 3000, proxies /api → :8080)
cd oes-web && npm run dev

# Build frontend
cd oes-web && npm run build
```

**Infrastructure (required before backend start):**
```bash
cd deploy/docker && docker compose up -d   # MySQL:3306, Redis:6379, RabbitMQ:5672+15672
```

Database migrations (Flyway) are in `deploy/sql/V*__*.sql` and auto-run by Docker Compose volume mount.

## Module Architecture

The project follows strict top-down dependency. All platform modules depend on `oes-common` and are aggregated by `oes-server` (the Spring Boot entry point). Platform modules do NOT depend on each other — cross-module communication uses Spring events or direct Service injection.

```
pom.xml (root, dependency version management)
├── oes-common/              Shared: DTOs (UnifiedResult, AstResultDTO), enums, exceptions, R.java, SnowflakeIdGenerator
├── oes-platform/ (pom aggregator)
│   ├── oes-gateway/         Instrument integration — SPI framework, channels, splitters, parsers, drivers
│   ├── oes-sample/          Sample CRUD, status state machine, tracking log
│   ├── oes-result/          Organism ID + AST result management, review workflow
│   ├── oes-workflow/        Rule engine (MVEL), critical value alerts, event-driven actions
│   ├── oes-lis/             LIS bidirectional: HL7/ASTM inbound parsing, outbound message building
│   ├── oes-report/          Excel/PDF report generation (POI, EasyExcel)
│   ├── oes-notification/    SMS/email notifications, RabbitMQ consumer
│   └── oes-admin/           RBAC (user/role/permission), JWT auth (login → token → filter chain)
├── oes-server/              Spring Boot entry (MylaApplication), application.yml, RabbitMQ config, all-module aggregator
├── capl/                    Standalone communication layer (LisCommApplication) — instrument gateway + LIS comm, RabbitMQ-decoupled from business
├── oes-web/                 Vue 3 + Element Plus + Pinia + Vue Router, Vite dev proxy → :8080
└── deploy/
    ├── docker/              docker-compose.yml (MySQL + Redis + RabbitMQ)
    └── sql/                 Flyway migration scripts V1–V6
```

**Two entry points:**
- `oes-server` (`MylaApplication`, port 8080) — business logic, REST API, workflow, reports
- `capl` (`LisCommApplication`, port 8081) — external communication only, no JDBC/MyBatis

Both share `oes-gateway` and `oes-common`. The CAPL layer is decoupled from business modules via RabbitMQ.

## Key Architecture Patterns

### Instrument Integration (SPI Framework)

Three-layer decoupled pipeline per instrument driver. Channel, Splitter, and Parser are composed to avoid N×M combinatorics:

```
Channel (how to connect)  →  Splitter (frame boundaries)  →  Parser (bytes → UnifiedResult)
  TcpChannel                  AstmSplitter                    Vitek2Parser
  FileChannel                 Hl7Splitter                     ProprietaryProtocolDriver
  NettyTcpChannel             RawPassthroughSplitter
```

Key interfaces are in `oes-gateway/core/spi/`: `InstrumentDriver`, `CommunicationChannel`, `FrameSplitter`, `DataParser`, `DataEventListener`, `TelemetryListener`. The `InstrumentHub` (`core/hub/`) manages driver lifecycle, health monitoring, and raw message archiving.

For proprietary (in-house) instruments, `gateway/protocol/ProprietaryFrameCodec.java` defines a unified binary frame format (8 message types: heartbeat, result push, command, telemetry, firmware, discovery, etc.) — new hardware only needs to implement this protocol; the `ProprietaryProtocolDriver` handles them all without code changes.

### Event-Driven Messaging (RabbitMQ)

Six exchanges, all with publisher confirm + manual consumer ack + DLQ:

| Exchange | Queues | Purpose |
|----------|--------|---------|
| `myla.instrument` | raw.message, result.parsed, driver.status, instrument.telemetry | Instrument data pipeline |
| `myla.workflow` | lab.event, rule.action | Workflow event triggers |
| `myla.lis` | outbound.msg, outbound.dlq | LIS outbound with dead-letter fallback |
| `myla.notification` | notify.sms, notify.email | Alerts and notifications |
| `myla.report` | report.gen, report.sched | Report generation and scheduling |
| `myla.system` | audit.write | Async audit log batch writes |

### Sample State Machine

```
REGISTERED → INOCULATED → INCUBATING → PENDING_REVIEW → APPROVED/REJECTED/RELEASED
```

State transitions are enforced in `oes-sample`, recorded in `sample_tracking`, and published as `LabEvent` to RabbitMQ for workflow rule matching.

### Unified Internal Data Model

All instrument results normalize to `UnifiedResult` (in `oes-common`): `instrumentId`, `sampleBarcode`, `patientId`, `organismCode/Name`, `identificationPercent`, `resultType` (ORGANISM_ID/AST/BLOOD_CULTURE_FLAG), `List<AstResultDTO>`, `testTime`, `rawMessage`.

### All PKs Use Snowflake IDs

No AUTO_INCREMENT. `SnowflakeIdGenerator` in `oes-common` generates all primary keys. MyBatis-Plus configured with `id-type: assign_id` for automatic population.

### RBAC + JWT Auth

Standard RBAC: `sys_user` → `sys_user_role` → `sys_role` → `sys_role_permission` → `sys_permission`. Login returns JWT; all other endpoints require `Authorization: Bearer <token>`. `@AuditLog` AOP annotation records operations to `audit_log` via RabbitMQ async writes.

## Database

- **Schema**: `myla`, utf8mb4
- **Migrations**: `deploy/sql/V1__init_schema.sql` through `V6__sample_barcode.sql`
- Key tables: `sample`, `sample_test`, `sample_tracking`, `organism_result`, `ast_result`, `raw_message`, `lis_config`, `lis_order`, `lis_inbound_message`, `outbound_message`, `lab_plate`, `lab_slide`, `lab_plate_order`, `lab_slide_order`, `sample_barcode`, `critical_value_alert`, `workflow_rule`, `instrument_registry`, `instrument_telemetry`, `audit_log`, plus dictionary tables (`organism_dict`, `antibiotic_dict`, `specimen_dict`, `hospital`)
- `raw_message`: immutable archive of all instrument raw data
- `audit_log`: append-only, never modified or deleted

## REST API Convention

All endpoints return unified JSON: `{ "code": 200, "message": "...", "data": {...} }`. Error codes: 200=success, 400=bad request, 401=unauthorized, 500=internal error, 10xx=instrument errors, 20xx=business errors. Full API reference in `docs/api-reference.md`.

## Configuration

- `application.yml` — common: server port 8080, MyBatis-Plus global config
- `application-dev.yml` — dev datasource (MySQL root/root), RabbitMQ, Redis, instrument configs (VITEK2 demo on TCP :19001-19002)
- `application-prod.yml` — production overrides
- CAPL has its own `application.yml` (port 8081, no datasource, RabbitMQ only)
- JWT secret via env var `JWT_SECRET` (defaults to `change-me-in-production`)
