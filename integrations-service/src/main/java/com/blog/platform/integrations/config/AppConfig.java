package com.blog.platform.integrations.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class AppConfig {

    /**
     * Keep amo Salesbot widget_request under ~2s (ack) + continue call.
     * @see <a href="https://www.amocrm.ru/developers/content/digital_pipeline/salesbot">Salesbot docs</a>
     */
    @Bean
    RestClient.Builder restClientBuilder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(800));
        factory.setReadTimeout(Duration.ofMillis(1500));
        return RestClient.builder().requestFactory(factory);
    }
}
