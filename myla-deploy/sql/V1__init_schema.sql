-- MYLA Phase 1 - Complete Database Schema
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
    order_mapping JSON COMMENT 'LIS->内部 字段映射',
    test_code_map JSON COMMENT 'LIS项目代码->内部代码',
    result_mapping JSON COMMENT '内部->LIS 字段映射',
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
