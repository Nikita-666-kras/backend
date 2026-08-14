package com.blog.platform.integrations.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class AppConfig {

    /**
     * JDK client supports PATCH (HttpURLConnection / SimpleClientHttpRequestFactory does not).
     * Timeouts keep Salesbot continue snappy and amo API calls reliable.
     */
    @Bean
    RestClient.Builder restClientBuilder() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder().requestFactory(factory);
    }

    /** Dedicated client for platform-api2.max.ru (Минцифры TLS). */
    @Bean
    @Qualifier("maxRestClient")
    RestClient maxRestClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .sslContext(RussianTrustedCa.sslContext())
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder().requestFactory(factory).build();
    }
}
