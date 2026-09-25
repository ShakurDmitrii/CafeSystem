package com.shakur.cafehelp.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.test.web.client.MockRestServiceServer;

import java.time.Duration;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PythonRestTemplateConfigTest {

    @Test
    void internalClientSendsTokenAndContractVersion() {
        var restTemplate = new AppConfig().restTemplate(
                new RestTemplateBuilder(),
                "internal-test-token",
                "1",
                Duration.ofSeconds(1),
                Duration.ofSeconds(1)
        );
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://python:8000/health"))
                .andExpect(header("X-Service-Token", "internal-test-token"))
                .andExpect(header("X-Contract-Version", "1"))
                .andRespond(withSuccess());

        restTemplate.getForEntity("http://python:8000/health", String.class);

        server.verify();
    }
}
