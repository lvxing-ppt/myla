package com.myla.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * MyLA（Microbiology Laboratory Automation）系统 Spring Boot 主启动类。
 * <p>
 * 微生物实验室自动化系统，负责与多种微生物检测仪器对接，
 * 实现数据采集、解析、审核、报告及 LIS 对接等全流程自动化。
 * </p>
 *
 * <h3>组件扫描范围：</h3>
 * <p>扫描 {@code com.myla} 包下所有模块的 Spring 组件，包括：</p>
 * <ul>
 *   <li>myla-common — 公共模块（API DTO、枚举、异常、工具类）</li>
 *   <li>myla-gateway — 网关模块（仪器通信通道、驱动、协议、分桢器）</li>
 *   <li>myla-server — 服务模块（业务逻辑、配置）</li>
 *   <li>myla-security — 安全模块（审计日志）</li>
 * </ul>
 *
 * @author MyLA Team
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.myla")
public class MylaApplication {

    /**
     * 应用程序入口。
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(MylaApplication.class, args);
    }
}
