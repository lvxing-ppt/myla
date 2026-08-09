package com.mlms.oes.lis.config;

import com.mlms.oes.lis.inbound.InboundMessageConsumer;
import com.mlms.oes.lis.inbound.LisInboundServiceImpl;
import com.mlms.oes.lis.mapper.LisInboundMessageMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * LIS 模块自动配置（业务层）。
 * <p>
 * 通讯层代码（LisInboundServer、各 Sender）已移至 capl 独立应用。
 * 本配置仅注册业务层 Bean：InboundMessageConsumer（MQ 消费）等。
 * </p>
 */
@Configuration
@ComponentScan("com.mlms.oes.lis")
@MapperScan("com.mlms.oes.lis.mapper")
public class LisAutoConfiguration {

    /** LIS 入站 MQ 消费者 — 消费 lis.inbound 队列，解析 HL7 并创建 Sample */
    @Bean
    public InboundMessageConsumer inboundMessageConsumer(LisInboundServiceImpl inboundService,
                                                          LisInboundMessageMapper inboundMsgMapper) {
        return new InboundMessageConsumer(inboundService, inboundMsgMapper);
    }
}
