package com.shakur.cafehelp.config;

import com.shakur.cafehelp.security.JwtAuthenticationFilter;
import com.shakur.cafehelp.security.ServiceTokenAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    public static final List<String> CORS_ALLOWED_ORIGINS = List.of(
            "http://localhost:3000",
            "http://127.0.0.1:3000",
            "http://tauri.localhost",
            "https://tauri.localhost",
            "tauri://localhost"
    );

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ServiceTokenAuthenticationFilter serviceTokenAuthenticationFilter;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            ServiceTokenAuthenticationFilter serviceTokenAuthenticationFilter
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.serviceTokenAuthenticationFilter = serviceTokenAuthenticationFilter;
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
                        // Let browser preflight requests through
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/bootstrap",
                                "/api/health",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/api/vk-bot/**",
                                "/error"
                        ).permitAll()
                        .requestMatchers("/api/auth/me").authenticated()

                        // Изображения доступны на чтение по непредсказуемому UUID,
                        // загрузка и любые будущие операции изменения — только OWNER.
                        .requestMatchers(HttpMethod.GET, "/api/v1/files/images/**").permitAll()
                        .requestMatchers("/api/v1/files/**", "/api/files/**").hasRole("OWNER")

                        // OWNER only: справочники/склады/движения/аналитика/админка
                        .requestMatchers("/api/user-accounts/**").hasRole("OWNER")
                        .requestMatchers("/api/v1/payroll/**").hasRole("OWNER")
                        .requestMatchers(HttpMethod.GET, "/api/persons/**").hasAnyRole("WORKER", "OWNER")
                        .requestMatchers("/api/persons/**").hasRole("OWNER")
                        .requestMatchers("/api/supplier/**").hasRole("OWNER")
                        .requestMatchers("/api/consignmentNote/**").hasRole("OWNER")
                        .requestMatchers("/api/consProduct/**").hasRole("OWNER")
                        .requestMatchers("/api/ml/**").hasRole("OWNER")
                        .requestMatchers("/api/analytics/**").hasRole("OWNER")
                        .requestMatchers("/api/tax/**").hasRole("OWNER")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/warehouses",
                                "/warehouses/*",
                                "/warehouses/*/products",
                                "/warehouses/*/preparations"
                        ).hasAnyRole("WORKER", "OWNER")
                        .requestMatchers("/warehouses", "/warehouses/**").hasRole("OWNER")
                        .requestMatchers("/movements/**").hasRole("OWNER")

                        // WORKER + OWNER: касса и операционная работа
                        .requestMatchers("/api/orders/**").hasAnyRole("WORKER", "OWNER")
                        .requestMatchers("/api/clients/**").hasAnyRole("WORKER", "OWNER")
                        // Меню и справочники: сотрудник только читает, менять может владелец
                        .requestMatchers(HttpMethod.GET, "/api/dishes/**").hasAnyRole("WORKER", "OWNER")
                        .requestMatchers("/api/dishes/**").hasRole("OWNER")
                        .requestMatchers(HttpMethod.GET, "/api/dish-sets/**").hasAnyRole("WORKER", "OWNER")
                        .requestMatchers("/api/dish-sets/**").hasRole("OWNER")
                        .requestMatchers(HttpMethod.GET, "/api/dish-categories/**").hasAnyRole("WORKER", "OWNER")
                        .requestMatchers("/api/dish-categories/**").hasRole("OWNER")
                        .requestMatchers(HttpMethod.GET, "/api/product/**").hasAnyRole("WORKER", "OWNER")
                        .requestMatchers("/api/product/**").hasRole("OWNER")
                        .requestMatchers(HttpMethod.GET, "/api/consumables/**").hasAnyRole("WORKER", "OWNER")
                        .requestMatchers(HttpMethod.POST, "/api/consumables/preview").hasAnyRole("WORKER", "OWNER")
                        .requestMatchers("/api/consumables/**").hasRole("OWNER")

                        // Смены: сотрудник открывает (/create из кассы, /open) и закрывает смену,
                        // менять состав существующей смены может только владелец
                        .requestMatchers(HttpMethod.GET, "/api/shifts/**").hasAnyRole("WORKER", "OWNER")
                        .requestMatchers(HttpMethod.POST, "/api/shifts/create", "/api/shifts/open", "/api/shifts/*/close")
                        .hasAnyRole("WORKER", "OWNER")
                        .requestMatchers("/api/shifts/**").hasRole("OWNER")
                        .requestMatchers("/api/tech-products/**").hasAnyRole("WORKER", "OWNER")
                        .requestMatchers("/api/preparations/**").hasAnyRole("WORKER", "OWNER")

                        // Любой прочий endpoint backend требует JWT
                        .anyRequest().authenticated()
                )
                .addFilterBefore(serviceTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(jwtAuthenticationFilter, ServiceTokenAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS configuration for Spring Security (preflight requests included).
     * WebMvcConfigurer CORS can be bypassed by the Security filter chain without this.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(CORS_ALLOWED_ORIGINS);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
