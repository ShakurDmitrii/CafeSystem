package com.shakur.cafehelp.Controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shakur.cafehelp.Controller.PyController.PythonAnalyticsClient;
import com.shakur.cafehelp.exception.PythonServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PythonAnalyticsClientContractTest {

    private PythonAnalyticsClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        client = new PythonAnalyticsClient(
                new RestTemplateBuilder(),
                "http://python:8000/",
                "internal-test-token",
                "1",
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                new ObjectMapper()
        );
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        server = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @Test
    void partialDashboardResponseIsRejected() {
        server.expect(requestTo("http://python:8000/api/analytics/dashboard?timeRange=week&refresh=false"))
                .andExpect(header("X-Service-Token", "internal-test-token"))
                .andExpect(header("X-Contract-Version", "1"))
                .andRespond(withSuccess("""
                        {
                          "kpi": null,
                          "top_rolls": [],
                          "sales_trend": [],
                          "insights": [],
                          "time_range": "week",
                          "generated_at": "2026-08-29T12:00:00",
                          "data_source": "test"
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getDashboardDataFromPython("week", null, null, false))
                .isInstanceOfSatisfying(PythonServiceException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(PythonServiceException.Code.INVALID_RESPONSE)
                );
        server.verify();
    }
}
