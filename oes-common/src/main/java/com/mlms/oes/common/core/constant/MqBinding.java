package com.mlms.oes.common.core.constant;

import lombok.Getter;

/**
 * MQ 交换机与路由键绑定枚举。
 * <p>
 * 集中管理所有 RabbitMQ 的 exchange、routing-key 及其用途说明，
 * 禁止在代码中使用魔法字符串直接指定队列或路由键。
 * </p>
 *
 * <h3>使用方式</h3>
 * <pre>
 * // 发送
 * rabbitTemplate.convertAndSend(
 *         MqBinding.LIS_INBOUND.getExchange(),
 *         MqBinding.LIS_INBOUND.getRoutingKey(),
 *         message);
 *
 * // 监听（queue name 与 routingKey 相同，可直接引用）
 * &#64;RabbitListener(queues = MqBinding.QueueNames.LIS_INBOUND)
 * </pre>
 *
 * <h3>注意</h3>
 * <p>一个 exchange + routingKey 可以被多个队列绑定（如 {@code lab.event} 同时路由到
 * {@code lab.event} 和 {@code lis.outbound.trigger} 两个队列），
 * 发送端只需关注交换机和路由键，不需要知道有多少消费者。</p>
 *
 * @author MLMS Team
 * @see com.mlms.oes.server.config.RabbitMqConfig
 */
@Getter
public enum MqBinding {

    // ==================== 仪器数据管道 (myla.instrument) ====================

    /** 仪器原始消息归档（所有仪器原始报文，不可变，永久保存） */
    INSTRUMENT_RAW("myla.instrument", "raw.message", "仪器原始消息归档"),
    /** 仪器解析结果投递（解析后的统一结果，触发样本/结果业务处理） */
    INSTRUMENT_RESULT_PARSED("myla.instrument", "result.parsed", "仪器解析结果投递"),
    /** 仪器遥测数据上报（温度、电压、试剂余量等运行指标） */
    INSTRUMENT_TELEMETRY("myla.instrument", "instrument.telemetry", "仪器遥测数据"),

    // ==================== LIS 系统对接 (myla.lis) ====================

    /** LIS 入站消息（医院 LIS 系统推送的 HL7 医嘱/查询消息，经 MLLP 解码后投递到业务层处理） */
    LIS_INBOUND("myla.lis", "lis.inbound", "LIS入站HL7消息"),
    /** LIS 出站消息（向医院 LIS 系统发送检验结果、报告状态等 HL7 消息） */
    LIS_OUTBOUND("myla.lis", "outbound.msg", "LIS出站HL7消息"),
    /** LIS 出站死信队列（出站消息多次投递失败后进入，需人工排查） */
    LIS_OUTBOUND_DLQ("myla.lis", "outbound.dlq", "LIS出站死信队列"),

    // ==================== 工作流引擎 (myla.workflow) ====================

    /** 实验室业务事件（样本登记、结果接收、审核通过等，触发规则引擎；同时路由到 LIS 出站触发器） */
    WORKFLOW_LAB_EVENT("myla.workflow", "lab.event", "实验室业务事件"),

    // ==================== 通知服务 (myla.notification) ====================

    /** 短信通知（危急值通知、TAT 超时告警等需要即时触达的消息） */
    NOTIFICATION_SMS("myla.notification", "notify.sms", "短信通知"),
    /** 邮件通知（检验报告完成通知、周期性报表等非即时消息） */
    NOTIFICATION_EMAIL("myla.notification", "notify.email", "邮件通知"),

    // ==================== 报告服务 (myla.report) ====================

    /** 报告生成请求（触发 PDF/Excel 报告生成任务） */
    REPORT_GENERATION("myla.report", "report.gen", "报告生成请求"),

    // ==================== 系统服务 (myla.system) ====================

    /** 审计日志异步写入（操作审计事件批量写入 audit_log 表，解耦业务线程） */
    SYSTEM_AUDIT_LOG("myla.system", "audit.write", "审计日志异步写入");

    // ==================== 字段 ====================

    /** RabbitMQ 交换机名称 */
    private final String exchange;
    /** 路由键（与队列名一致，参见 {@link QueueNames}） */
    private final String routingKey;
    /** 中文用途说明 */
    private final String description;

    MqBinding(String exchange, String routingKey, String description) {
        this.exchange = exchange;
        this.routingKey = routingKey;
        this.description = description;
    }

    // ==================== 队列名常量（供 @RabbitListener 使用） ====================

    /**
     * 队列名常量。
     * <p>
     * Spring AMQP 的 {@code @RabbitListener(queues = "...")} 直接引用队列名而非路由键。
     * 避免各处分散魔法字符串，集中在此处统一定义。
     * </p>
     */
    public static final class QueueNames {
        /** {@link MqBinding#INSTRUMENT_RAW} */
        public static final String INSTRUMENT_RAW = "raw.message";
        /** {@link MqBinding#INSTRUMENT_RESULT_PARSED} */
        public static final String INSTRUMENT_RESULT_PARSED = "result.parsed";
        /** {@link MqBinding#INSTRUMENT_TELEMETRY} */
        public static final String INSTRUMENT_TELEMETRY = "instrument.telemetry";
        /** {@link MqBinding#LIS_INBOUND} */
        public static final String LIS_INBOUND = "lis.inbound";
        /** {@link MqBinding#LIS_OUTBOUND} */
        public static final String LIS_OUTBOUND = "outbound.msg";
        /** {@link MqBinding#LIS_OUTBOUND_DLQ} */
        public static final String LIS_OUTBOUND_DLQ = "outbound.dlq";
        /** {@link MqBinding#WORKFLOW_LAB_EVENT} — 也路由到 lis.outbound.trigger */
        public static final String WORKFLOW_LAB_EVENT = "lab.event";
        /** LIS 出站触发器（与 lab.event 共享路由键，独立队列） */
        public static final String LIS_OUTBOUND_TRIGGER = "lis.outbound.trigger";
        /** {@link MqBinding#NOTIFICATION_SMS} */
        public static final String NOTIFICATION_SMS = "notify.sms";
        /** {@link MqBinding#NOTIFICATION_EMAIL} */
        public static final String NOTIFICATION_EMAIL = "notify.email";
        /** {@link MqBinding#REPORT_GENERATION} */
        public static final String REPORT_GENERATION = "report.gen";
        /** {@link MqBinding#SYSTEM_AUDIT_LOG} */
        public static final String SYSTEM_AUDIT_LOG = "audit.write";

        private QueueNames() {}
    }
}
