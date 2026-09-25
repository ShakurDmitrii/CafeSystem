package com.shakur.cafehelp.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigCorsTest {

    private final SecurityConfig securityConfig = new SecurityConfig(null, null);

    @Test
    void permitsDesktopApplicationOrigin() {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/auth/login");
        CorsConfiguration configuration = securityConfig.corsConfigurationSource()
                .getCorsConfiguration(request);

        assertThat(configuration).isNotNull();
        assertThat(configuration.checkOrigin("http://tauri.localhost"))
                .isEqualTo("http://tauri.localhost");
    }

    @Test
    void rejectsUntrustedWebOrigin() {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/auth/login");
        CorsConfiguration configuration = securityConfig.corsConfigurationSource()
                .getCorsConfiguration(request);

        assertThat(configuration).isNotNull();
        assertThat(configuration.checkOrigin("https://untrusted.example")).isNull();
    }
}
