package com.blog.platform.integrations.config;

import com.blog.platform.integrations.security.WebhookSecretFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class FilterConfig {

    @Bean
    WebhookSecretFilter webhookSecretFilter(@Value("${amocrm.webhook-secret:}") String secret) {
        return new WebhookSecretFilter(secret);
    }

    @Bean
    FilterRegistrationBean<WebhookSecretFilter> webhookSecretFilterRegistration(WebhookSecretFilter webhookSecretFilter) {
        FilterRegistrationBean<WebhookSecretFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(webhookSecretFilter);
        bean.addUrlPatterns("/amocrm/*");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        // Prevent Spring Boot from also registering the filter as a generic servlet filter
        bean.setEnabled(true);
        return bean;
    }
}
