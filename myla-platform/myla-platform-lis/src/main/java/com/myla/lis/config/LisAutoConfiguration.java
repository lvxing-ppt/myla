package com.myla.lis.config;

import com.myla.lis.inbound.LisInboundServer;
import com.myla.lis.inbound.LisInboundService;
import com.myla.lis.mapper.LisConfigMapper;
import com.myla.lis.outbound.AstmTcpSender;
import com.myla.lis.outbound.Hl7MllpSender;
import com.myla.lis.outbound.HttpSender;
import com.myla.lis.outbound.LisOutboundSender;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * LIS 模块自动配置。
 * <p>
 * 注册 LisInboundServer、各 LisOutboundSender 实现、组件扫描和 Mapper 扫描。
 * </p>
 */
@Configuration
@ComponentScan("com.myla.lis")
@MapperScan("com.myla.lis.mapper")
public class LisAutoConfiguration {

    /** LIS 入站 TCP MLLP 服务器 */
    @Bean
    public LisInboundServer lisInboundServer(LisConfigMapper configMapper,
                                              LisInboundService inboundService) {
        return new LisInboundServer(configMapper, inboundService);
    }

    /** HL7 MLLP 出站发送器 */
    @Bean
    public LisOutboundSender hl7MllpSender() {
        return new Hl7MllpSender();
    }

    /** ASTM TCP 出站发送器 */
    @Bean
    public LisOutboundSender astmTcpSender() {
        return new AstmTcpSender();
    }

    /** HTTP 出站发送器 */
    @Bean
    public LisOutboundSender httpSender() {
        return new HttpSender();
    }
}
