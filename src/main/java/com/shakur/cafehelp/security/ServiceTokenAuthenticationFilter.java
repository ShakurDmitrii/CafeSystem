package com.shakur.cafehelp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Component
public class ServiceTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "X-Service-Token";
    private static final String CONTRACT_HEADER_NAME = "X-Contract-Version";
    private final String expectedToken;
    private final String expectedContractVersion;

    public ServiceTokenAuthenticationFilter(
            @Value("${internal.service.token:}") String expectedToken,
            @Value("${internal.api.contract-version:1}") String expectedContractVersion
    ) {
        this.expectedToken = expectedToken == null ? "" : expectedToken.trim();
        this.expectedContractVersion = expectedContractVersion == null
                ? "1"
                : expectedContractVersion.trim();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String providedToken = request.getHeader(HEADER_NAME);
        if (!expectedToken.isEmpty()
                && providedToken != null
                && constantTimeEquals(providedToken, expectedToken)) {
            String providedVersion = request.getHeader(CONTRACT_HEADER_NAME);
            if (providedVersion != null && !providedVersion.equals(expectedContractVersion)) {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                response.setContentType("application/json");
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.getWriter().write(
                        "{\"code\":\"INTERNAL_CONTRACT_VERSION_MISMATCH\","
                                + "\"message\":\"Несовместимая версия внутреннего API-контракта\"}"
                );
                return;
            }
            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                filterChain.doFilter(request, response);
                return;
            }
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            "cafehelp-internal-service",
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_OWNER"))
                    );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }

    private boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8)
        );
    }
}
