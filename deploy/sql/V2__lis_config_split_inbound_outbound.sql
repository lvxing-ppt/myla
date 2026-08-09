-- ==================== V2: LIS 入站/出站配置拆分 ====================

ALTER TABLE lis_config
    ADD COLUMN inbound_config  JSON COMMENT '入站通道配置: {"port":2575} — MLMS 本机监听，LIS 主动连接' AFTER channel_type,
    ADD COLUMN outbound_config JSON COMMENT '出站通道配置: {"host":"10.0.1.5","port":2575} — MLMS 主动连接医院 LIS' AFTER inbound_config;
