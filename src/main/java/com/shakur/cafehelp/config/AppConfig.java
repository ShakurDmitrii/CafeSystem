package com.shakur.cafehelp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate(
            @Value("${internal.service.token:}") String internalServiceToken
    ) {
        RestTemplate restTemplate = new RestTemplate();
        if (internalServiceToken != null && !internalServiceToken.isBlank()) {
            restTemplate.getInterceptors().add((request, body, execution) -> {
                request.getHeaders().set("X-Service-Token", internalServiceToken);
                return execution.execute(request, body);
            });
        }
        return restTemplate;
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
