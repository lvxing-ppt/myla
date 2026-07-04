package com.myla.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.myla")
public class MylaApplication {
    public static void main(String[] args) {
        SpringApplication.run(MylaApplication.class, args);
    }
}
