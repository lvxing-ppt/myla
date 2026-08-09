package com.myla.server.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 消息队列配置类。
 * <p>
 * 一次性声明系统所需的所有交换机（Exchange）、队列（Queue）和绑定关系（Binding）。
 * 使用 Topic Exchange 进行消息路由，支持灵活的通配符路由键匹配。
 * </p>
 *
 * <h3>交换机规划：</h3>
 * <ul>
 *   <li>{@code myla.instrument} — 仪器数据相关消息（原始报文、解析结果、遥测）</li>
 *   <li>{@code myla.workflow} — 实验室工作流事件</li>
 *   <li>{@code myla.lis} — LIS 系统对接消息（出站消息及死信队列）</li>
 *   <li>{@code myla.notification} — 通知消息（短信、邮件）</li>
 *   <li>{@code myla.report} — 报告生成消息</li>
 *   <li>{@code myla.system} — 系统级消息（审计日志写库）</li>
 * </ul>
 *
 * <h3>死信队列（DLQ）：</h3>
 * <p>{@code outbound.msg} 队列配置了死信交换机和死信路由键。
 * 当消息被拒绝（NACK）或过期时，自动路由到 {@code outbound.dlq} 死信队列。</p>
 *
 * <h3>消息确认：</h3>
 * <p>{@link RabbitTemplate} 配置了发布确认（Publisher Confirm）回调，
 * 当消息未能成功投递到 Broker 时可记录日志或触发告警。</p>
 *
 * @author MyLA Team
 */
@Configuration
public class RabbitMqConfig {

    // ==================== 交换机声明 (Exchanges) ====================

    /**
     * 仪器数据 Topic 交换机。
     * <p>路由键示例：raw.message, result.parsed, instrument.telemetry</p>
     */
    @Bean
    public TopicExchange instrumentExchange() {
        return new TopicExchange("myla.instrument");
    }

    /**
     * 工作流事件 Topic 交换机。
     * <p>路由键示例：lab.event</p>
     */
    @Bean
    public TopicExchange workflowExchange() {
        return new TopicExchange("myla.workflow");
    }

    /**
     * LIS 对接 Topic 交换机。
     * <p>路由键示例：outbound.msg, outbound.dlq</p>
     */
    @Bean
    public TopicExchange lisExchange() {
        return new TopicExchange("myla.lis");
    }

    /**
     * 通知 Topic 交换机。
     * <p>路由键示例：notify.sms, notify.email</p>
     */
    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange("myla.notification");
    }

    /**
     * 报告 Topic 交换机。
     * <p>路由键示例：report.gen</p>
     */
    @Bean
    public TopicExchange reportExchange() {
        return new TopicExchange("myla.report");
    }

    /**
     * 系统 Topic 交换机。
     * <p>路由键示例：audit.write</p>
     */
    @Bean
    public TopicExchange systemExchange() {
        return new TopicExchange("myla.system");
    }

    // ==================== 队列声明 (Queues) ====================

    /**
     * 原始报文持久化队列。
     * <p>消费者负责将接收到的仪器原始报文写入数据库或对象存储。</p>
     */
    @Bean
    public Queue rawMessageQueue() {
        return QueueBuilder.durable("raw.message").build();
    }

    /**
     * 解析结果队列。
     * <p>消费者负责将解析后的 UnifiedResult 写入业务数据库。</p>
     */
    @Bean
    public Queue resultParsedQueue() {
        return QueueBuilder.durable("result.parsed").build();
    }

    /**
     * 仪器遥测数据队列。
     * <p>消费者负责将遥测数据写入时序数据库或触发监控告警。</p>
     */
    @Bean
    public Queue instrumentTelemetryQueue() {
        return QueueBuilder.durable("instrument.telemetry").build();
    }

    /**
     * 实验室事件队列。
     * <p>工作流引擎监听此队列，根据事件驱动样本状态流转。</p>
     */
    @Bean
    public Queue labEventQueue() {
        return QueueBuilder.durable("lab.event").build();
    }

    /**
     * 出站消息队列（发送到 LIS）。
     * <p>
     * 配置了死信队列：消息被拒绝或过期时自动路由到 outbound.dlq。
     * 死信交换机：myla.lis，死信路由键：outbound.dlq。
     * </p>
     */
    @Bean
    public Queue outboundMsgQueue() {
        return QueueBuilder.durable("outbound.msg")
                .withArgument("x-dead-letter-exchange", "myla.lis")
                .withArgument("x-dead-letter-routing-key", "outbound.dlq")
                .build();
    }

    /**
     * 出站消息死信队列（DLQ）。
     * <p>接收 outbound.msg 队列中被拒绝或过期的消息，供运维人员排查。</p>
     */
    @Bean
    public Queue outboundDlqQueue() {
        return QueueBuilder.durable("outbound.dlq").build();
    }

    /**
     * LIS 出站触发队列。
     * <p>TopicExchange myla.workflow 的第二订阅者，与 lab.event 队列同时收到消息。
     * 消费者（ResultReleasedConsumer）过滤 RESULT_RELEASED_TO_LIS 事件，
     * 构造 HL7 消息后调用 LisGatewayService.sendResult()。</p>
     */
    @Bean
    public Queue lisOutboundTriggerQueue() {
        return QueueBuilder.durable("lis.outbound.trigger").build();
    }

    /**
     * 短信通知队列。
     * <p>消费者负责通过短信网关发送通知。</p>
     */
    @Bean
    public Queue notifySmsQueue() {
        return QueueBuilder.durable("notify.sms").build();
    }

    /**
     * 邮件通知队列。
     * <p>消费者负责通过邮件服务器发送通知。</p>
     */
    @Bean
    public Queue notifyEmailQueue() {
        return QueueBuilder.durable("notify.email").build();
    }

    /**
     * 报告生成队列。
     * <p>消费者负责异步生成 PDF/HTML 报告。</p>
     */
    @Bean
    public Queue reportGenQueue() {
        return QueueBuilder.durable("report.gen").build();
    }

    /**
     * 审计日志写入队列。
     * <p>消费者负责将审计日志异步写入数据库，避免阻塞主业务流程。</p>
     */
    @Bean
    public Queue auditWriteQueue() {
        return QueueBuilder.durable("audit.write").build();
    }

    // ==================== 绑定关系 (Bindings) ====================

    /**
     * 绑定：raw.message 路由键 -> rawMessageQueue。
     */
    @Bean
    public Binding rawMessageBinding() {
        return BindingBuilder.bind(rawMessageQueue()).to(instrumentExchange()).with("raw.message");
    }

    /**
     * 绑定：result.parsed 路由键 -> resultParsedQueue。
     */
    @Bean
    public Binding resultParsedBinding() {
        return BindingBuilder.bind(resultParsedQueue()).to(instrumentExchange()).with("result.parsed");
    }

    /**
     * 绑定：instrument.telemetry 路由键 -> instrumentTelemetryQueue。
     */
    @Bean
    public Binding instrumentTelemetryBinding() {
        return BindingBuilder.bind(instrumentTelemetryQueue()).to(instrumentExchange()).with("instrument.telemetry");
    }

    /**
     * 绑定：lab.event 路由键 -> labEventQueue。
     */
    @Bean
    public Binding labEventBinding() {
        return BindingBuilder.bind(labEventQueue()).to(workflowExchange()).with("lab.event");
    }

    /**
     * 绑定：lab.event 路由键 → lisOutboundTriggerQueue（LIS 出站触发）。
     */
    @Bean
    public Binding lisOutboundTriggerBinding() {
        return BindingBuilder.bind(lisOutboundTriggerQueue()).to(workflowExchange()).with("lab.event");
    }

    /**
     * 绑定：outbound.msg 路由键 -> outboundMsgQueue。
     */
    @Bean
    public Binding outboundMsgBinding() {
        return BindingBuilder.bind(outboundMsgQueue()).to(lisExchange()).with("outbound.msg");
    }

    /**
     * 绑定：outbound.dlq 路由键 -> outboundDlqQueue。
     */
    @Bean
    public Binding outboundDlqBinding() {
        return BindingBuilder.bind(outboundDlqQueue()).to(lisExchange()).with("outbound.dlq");
    }

    /**
     * 绑定：notify.sms 路由键 -> notifySmsQueue。
     */
    @Bean
    public Binding notifySmsBinding() {
        return BindingBuilder.bind(notifySmsQueue()).to(notificationExchange()).with("notify.sms");
    }

    /**
     * 绑定：notify.email 路由键 -> notifyEmailQueue。
     */
    @Bean
    public Binding notifyEmailBinding() {
        return BindingBuilder.bind(notifyEmailQueue()).to(notificationExchange()).with("notify.email");
    }

    /**
     * 绑定：report.gen 路由键 -> reportGenQueue。
     */
    @Bean
    public Binding reportGenBinding() {
        return BindingBuilder.bind(reportGenQueue()).to(reportExchange()).with("report.gen");
    }

    /**
     * 绑定：audit.write 路由键 -> auditWriteQueue。
     */
    @Bean
    public Binding auditWriteBinding() {
        return BindingBuilder.bind(auditWriteQueue()).to(systemExchange()).with("audit.write");
    }

    // ==================== 消息转换器 ====================

    /**
     * JSON 消息转换器：使用 Jackson 将 Java 对象序列化为 JSON 格式的消息体。
     * @return Jackson2JsonMessageConverter 实例
     */
    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * RabbitTemplate 配置。
     * <p>
     * 设置 JSON 消息转换器和发布确认回调。
     * 当消息发布到交换机失败时（ack=false），触发确认回调。
     * 生产环境应在回调中实现重试逻辑或告警通知。
     * </p>
     *
     * @param connectionFactory RabbitMQ 连接工厂
     * @param converter JSON 消息转换器
     * @return 配置好的 RabbitTemplate 实例
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        // 发布确认回调：消息未能到达交换机时触发
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                // Publisher confirm failed — handle in production
                // 生产环境应记录日志、触发告警或重试
            }
        });
        return template;
    }
}
