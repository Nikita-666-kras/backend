package com.blog.platform.integrations.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

@Configuration
public class AppConfig {

    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    /** Fire-and-forget continue to amoCRM so widget_request gets 200 within ~2s. */
    @Bean(name = "amocrmContinueExecutor")
    TaskExecutor amocrmContinueExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("amocrm-continue-");
        executor.initialize();
        return executor;
    }
}
