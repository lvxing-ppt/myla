-- ==================== V3: LIS 入站原始报文存档 ====================

CREATE TABLE lis_inbound_message (
    id BIGINT PRIMARY KEY,
    hospital_code VARCHAR(32) NOT NULL COMMENT '医院编码',
    message_type VARCHAR(30) COMMENT '消息类型: ORM^O01 / ADT^A04',
    message_control_id VARCHAR(64) COMMENT 'HL7 MSH-10 消息控制ID',
    raw_content TEXT NOT NULL COMMENT '原始 HL7 报文',
    process_status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED' COMMENT 'RECEIVED/PROCESSED/FAILED',
    sample_id BIGINT COMMENT '关联 sample.id',
    error_msg TEXT COMMENT '处理失败原因',
    received_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '接收时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_hospital_time (hospital_code, received_at),
    INDEX idx_status (process_status),
    INDEX idx_sample (sample_id)
) ENGINE=InnoDB COMMENT='LIS入站原始报文存档';
