package com.blog.platform.integrations;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.blog.platform.integrations")
public class IntegrationsServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(IntegrationsServiceApplication.class, args);
    }
}
