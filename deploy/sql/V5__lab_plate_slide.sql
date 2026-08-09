-- ==================== V5: 平板 & 玻片 ====================

CREATE TABLE lab_plate (
    id BIGINT PRIMARY KEY,
    plate_id VARCHAR(32) NOT NULL COMMENT '平板编号 P-{sampleId}-{seq}',
    sample_id BIGINT NOT NULL COMMENT '关联 sample.id',
    media_type VARCHAR(64) COMMENT '培养基类型: 血平板/麦康凯/巧克力',
    media_lot VARCHAR(32) COMMENT '培养基批号',
    status VARCHAR(20) NOT NULL DEFAULT 'INOCULATED' COMMENT 'INOCULATED/INCUBATING/GROWTH_DETECTED/NO_GROWTH/CONTAMINATED/COMPLETED',
    inoculate_time DATETIME COMMENT '接种时间',
    incubator_id VARCHAR(32) COMMENT '培养箱编号',
    incubator_location VARCHAR(32) COMMENT '培养箱位置',
    comment VARCHAR(256),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_plate_id (plate_id),
    INDEX idx_sample (sample_id),
    INDEX idx_status (status)
) ENGINE=InnoDB COMMENT='实验室平板(培养基)';

-- 平板-订单关联（多对多）
CREATE TABLE lab_plate_order (
    id BIGINT PRIMARY KEY,
    plate_id BIGINT NOT NULL COMMENT '关联 lab_plate.id',
    order_id BIGINT NOT NULL COMMENT '关联 lis_order.id',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_plate_order (plate_id, order_id),
    INDEX idx_order (order_id)
) ENGINE=InnoDB COMMENT='平板-订单关联';

CREATE TABLE lab_slide (
    id BIGINT PRIMARY KEY,
    slide_id VARCHAR(32) NOT NULL COMMENT '玻片编号 S-{sampleId}-{seq}',
    sample_id BIGINT NOT NULL COMMENT '关联 sample.id',
    stain_type VARCHAR(64) COMMENT '染色类型: 革兰/抗酸/墨汁',
    stain_lot VARCHAR(32) COMMENT '染液批号',
    status VARCHAR(20) NOT NULL DEFAULT 'PREPARED' COMMENT 'PREPARED/STAINED/EXAMINED/COMPLETED',
    wbc_count VARCHAR(32) COMMENT 'WBC计数',
    epi_cell_count VARCHAR(32) COMMENT '上皮细胞计数',
    bacteria_morphology VARCHAR(128) COMMENT '细菌形态描述',
    gram_result VARCHAR(20) COMMENT '革兰结果: G+球菌/G-杆菌/...',
    examined_by VARCHAR(32) COMMENT '镜检人',
    examined_at DATETIME COMMENT '镜检时间',
    comment VARCHAR(256),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_slide_id (slide_id),
    INDEX idx_sample (sample_id),
    INDEX idx_status (status)
) ENGINE=InnoDB COMMENT='实验室玻片(涂片镜检)';

-- 玻片-订单关联（多对多）
CREATE TABLE lab_slide_order (
    id BIGINT PRIMARY KEY,
    slide_id BIGINT NOT NULL COMMENT '关联 lab_slide.id',
    order_id BIGINT NOT NULL COMMENT '关联 lis_order.id',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_slide_order (slide_id, order_id),
    INDEX idx_order (order_id)
) ENGINE=InnoDB COMMENT='玻片-订单关联';
