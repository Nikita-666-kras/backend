package com.blog.platform.logging;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.blog.platform")
@EnableScheduling
public class LoggingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(LoggingServiceApplication.class, args);
    }
}
