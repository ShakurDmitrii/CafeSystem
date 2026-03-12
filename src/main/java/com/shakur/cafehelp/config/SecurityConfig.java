package com.shakur.cafehelp.config;

import com.shakur.cafehelp.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/bootstrap",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/error"
                        ).permitAll()
                        .requestMatchers("/api/auth/me").authenticated()

                        // OWNER only: справочники/склады/движения/аналитика/админка
                        .requestMatchers("/api/user-accounts/**").hasRole("OWNER")
                        .requestMatchers(HttpMethod.GET, "/api/persons/**").hasAnyRole("WORKER", "OWNER")
                        .requestMatchers("/api/persons/**").hasRole("OWNER")
                        .requestMatchers("/api/supplier/**").hasRole("OWNER")
                        .requestMatchers("/api/consignmentNote/**").hasRole("OWNER")
                        .requestMatchers("/api/consProduct/**").hasRole("OWNER")
                        .requestMatchers("/api/ml/**").hasRole("OWNER")
                        .requestMatchers("/api/analytics/**").hasRole("OWNER")
                        .requestMatchers(
                                new AntPathRequestMatcher("/warehouses"),
                                new AntPathRequestMatcher("/warehouses/**")
                        ).permitAll()
                        .requestMatchers("/movements/**").hasRole("OWNER")

                        // WORKER + OWNER: касса и операционная работа
                        .requestMatchers("/api/orders/**").hasAnyRole("WORKER", "OWNER")
                        .requestMatchers("/api/clients/**").hasAnyRole("WORKER", "OWNER")
                        .requestMatchers(HttpMethod.GET, "/api/dishes/**").hasAnyRole("WORKER", "OWNER")
                        .requestMatchers(HttpMethod.GET, "/api/dish-categories/**").hasAnyRole("WORKER", "OWNER")
                        .requestMatchers("/api/dish-categories/**").hasRole("OWNER")
                        .requestMatchers(HttpMethod.GET, "/api/product/**").hasAnyRole("WORKER", "OWNER")
                        .requestMatchers(HttpMethod.GET, "/api/shifts/**").hasAnyRole("WORKER", "OWNER")
                        .requestMatchers(HttpMethod.POST, "/api/shifts/open", "/api/shifts/*/close").hasAnyRole("WORKER", "OWNER")
                        .requestMatchers(HttpMethod.GET, "/api/tech-products/**").hasAnyRole("WORKER", "OWNER")

                        // Любой прочий endpoint backend требует JWT
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
