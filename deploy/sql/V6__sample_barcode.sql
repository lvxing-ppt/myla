-- ==================== V6: Sample 多 barcode 支持 ====================

CREATE TABLE sample_barcode (
    id BIGINT PRIMARY KEY,
    sample_id BIGINT NOT NULL COMMENT '关联 sample.id',
    barcode VARCHAR(64) NOT NULL COMMENT '条码',
    source VARCHAR(16) NOT NULL DEFAULT 'LIS' COMMENT '来源: LIS/HIS/MANUAL/RETEST',
    is_primary TINYINT NOT NULL DEFAULT 0 COMMENT '是否主条码 0-否 1-是',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_barcode (barcode),
    INDEX idx_sample (sample_id)
) ENGINE=InnoDB COMMENT='标本条码(一个标本可有多条码)';
