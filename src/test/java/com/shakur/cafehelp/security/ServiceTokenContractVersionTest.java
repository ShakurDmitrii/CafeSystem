package com.shakur.cafehelp.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ServiceTokenContractVersionTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validTokenWithIncompatibleVersionIsRejectedBeforeController() throws Exception {
        ServiceTokenAuthenticationFilter filter = new ServiceTokenAuthenticationFilter(
                "internal-test-token",
                "1"
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/ml/data/sales");
        request.addHeader("X-Service-Token", "internal-test-token");
        request.addHeader("X-Contract-Version", "999");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).contains("INTERNAL_CONTRACT_VERSION_MISMATCH");
        verifyNoInteractions(chain);
    }
}
