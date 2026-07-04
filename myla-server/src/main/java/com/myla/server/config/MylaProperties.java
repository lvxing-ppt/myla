package com.myla.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MyLA 系统配置属性类。
 * <p>
 * 绑定 {@code application.yml} 中以 {@code myla} 为前缀的所有配置项。
 * 通过 Spring Boot 的 {@link ConfigurationProperties} 机制自动映射。
 * </p>
 *
 * <h3>配置示例（YAML）：</h3>
 * <pre>{@code
 * myla:
 *   gateway:
 *     driver-dir: ./drivers
 *   security:
 *     jwt-secret: your-secret-key
 *     jwt-expiration: 7200
 * }</pre>
 *
 * @author MyLA Team
 */
@Data
@Component
@ConfigurationProperties(prefix = "myla")
public class MylaProperties {

    /** 网关相关配置 */
    private Gateway gateway = new Gateway();

    /** 安全相关配置 */
    private Security security = new Security();

    /**
     * 网关配置子类。
     */
    @Data
    public static class Gateway {

        /** 驱动目录：存放仪器驱动插件 JAR 包的路径，默认 "./drivers" */
        private String driverDir = "./drivers";
    }

    /**
     * 安全配置子类。
     */
    @Data
    public static class Security {

        /** JWT 签名密钥，生产环境务必修改默认值 */
        private String jwtSecret = "change-me-in-production";

        /** JWT Token 过期时间，单位秒，默认 7200（2 小时） */
        private long jwtExpiration = 7200;
    }
}
