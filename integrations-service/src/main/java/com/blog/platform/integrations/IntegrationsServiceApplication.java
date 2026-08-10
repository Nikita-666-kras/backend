package com.blog.platform.integrations;

import com.blog.platform.integrations.config.AmoCrmProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = "com.blog.platform.integrations")
@EnableConfigurationProperties(AmoCrmProperties.class)
public class IntegrationsServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(IntegrationsServiceApplication.class, args);
    }
}
