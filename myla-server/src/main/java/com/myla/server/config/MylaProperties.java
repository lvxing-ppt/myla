package com.myla.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "myla")
public class MylaProperties {

    private Gateway gateway = new Gateway();
    private Security security = new Security();

    @Data
    public static class Gateway {
        private String driverDir = "./drivers";
    }

    @Data
    public static class Security {
        private String jwtSecret = "change-me-in-production";
        private long jwtExpiration = 7200;
    }
}
