package com.blog.platform.admin.config;

import com.blog.platform.admin.security.JwtAuthenticationFilter;
import com.blog.platform.common.security.InternalHeaders;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.client.RestClient;

@Configuration
@RequiredArgsConstructor
public class AdminConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    RestClient postServiceRestClient(
            @Value("${post-service.base-url}") String baseUrl,
            @Value("${security.internal-api-keys.post:${POST_INTERNAL_API_KEY:${INTERNAL_API_KEY}}}") String internalApiKey
    ) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(InternalHeaders.API_KEY, internalApiKey)
                .build();
    }

    @Bean
    RestClient partsServiceRestClient(
            @Value("${parts-service.base-url}") String baseUrl,
            @Value("${security.internal-api-keys.parts:${PARTS_INTERNAL_API_KEY:${INTERNAL_API_KEY}}}") String internalApiKey
    ) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(InternalHeaders.API_KEY, internalApiKey)
                .build();
    }

    @Bean
    RestClient proposalServiceRestClient(
            @Value("${proposal-service.base-url}") String baseUrl,
            @Value("${security.internal-api-keys.proposal:${PROPOSAL_INTERNAL_API_KEY:${INTERNAL_API_KEY}}}") String internalApiKey
    ) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(InternalHeaders.API_KEY, internalApiKey)
                .build();
    }

    @Bean
    RestClient loggingServiceRestClient(
            @Value("${logging-service.base-url}") String baseUrl,
            @Value("${security.internal-api-keys.logging:${LOGGING_INTERNAL_API_KEY:${INTERNAL_API_KEY}}}") String internalApiKey
    ) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(InternalHeaders.API_KEY, internalApiKey)
                .build();
    }
}
