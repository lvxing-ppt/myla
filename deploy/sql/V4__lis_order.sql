-- ==================== V4: LIS 订单表 ====================

CREATE TABLE lis_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_code VARCHAR(32) COMMENT '医嘱编号 (OBR-2 Placer 或 OBR-3 Filler)',
    sample_id BIGINT NOT NULL COMMENT '关联 sample.id',
    hospital_code VARCHAR(32) NOT NULL COMMENT '医院编码',
    test_code VARCHAR(32) COMMENT '检验项目编码 (OBR-4-1)',
    test_name VARCHAR(128) COMMENT '检验项目名称 (OBR-4-2)',
    specimen_type VARCHAR(64) COMMENT '标本类型 (OBR-15)',
    collect_time DATETIME COMMENT '采集时间 (OBR-7)',
    priority VARCHAR(20) COMMENT '优先级 (OBR-5)',
    status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED' COMMENT 'RECEIVED/PROCESSING/COMPLETED/CANCELLED',
    inbound_message_id BIGINT COMMENT '关联 lis_inbound_message.id',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sample (sample_id),
    INDEX idx_order_code (order_code),
    INDEX idx_hospital (hospital_code)
) ENGINE=InnoDB COMMENT='LIS检验医嘱(订单)';
