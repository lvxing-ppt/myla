package com.mlms.capl;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * LIS 通讯层独立应用。
 * <p>
 * 统一处理所有外部通讯：LIS 入站/出站 + 仪器网关。
 * 与业务层 (oes-server) 通过 RabbitMQ 解耦，零业务依赖。
 * </p>
 */
@SpringBootApplication
@EnableScheduling
public class LisCommApplication {

    public static void main(String[] args) {
        SpringApplication.run(LisCommApplication.class, args);
    }
}
