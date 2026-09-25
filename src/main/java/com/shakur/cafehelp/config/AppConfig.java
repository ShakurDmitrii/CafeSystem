package com.shakur.cafehelp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate(
            RestTemplateBuilder builder,
            @Value("${internal.service.token:}") String internalServiceToken,
            @Value("${internal.api.contract-version:1}") String contractVersion,
            @Value("${python.client.connect-timeout:3s}") Duration connectTimeout,
            @Value("${python.client.read-timeout:30s}") Duration readTimeout
    ) {
        return builder
                .connectTimeout(connectTimeout)
                .readTimeout(readTimeout)
                .additionalInterceptors((request, body, execution) -> {
                    if (internalServiceToken != null && !internalServiceToken.isBlank()) {
                        request.getHeaders().set("X-Service-Token", internalServiceToken);
                    }
                    request.getHeaders().set("X-Contract-Version", contractVersion);
                    return execution.execute(request, body);
                })
                .build();
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
