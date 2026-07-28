package com.blog.platform.parts;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication(scanBasePackages = "com.blog.platform")
@EnableJpaAuditing
public class PartsServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PartsServiceApplication.class, args);
    }
}
