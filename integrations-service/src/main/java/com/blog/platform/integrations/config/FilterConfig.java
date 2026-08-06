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
    FilterRegistrationBean<WebhookSecretFilter> webhookSecretFilterRegistration(
            @Value("${amocrm.webhook-secret:}") String secret
    ) {
        FilterRegistrationBean<WebhookSecretFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new WebhookSecretFilter(secret));
        bean.addUrlPatterns("/amocrm/*");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return bean;
    }
}
