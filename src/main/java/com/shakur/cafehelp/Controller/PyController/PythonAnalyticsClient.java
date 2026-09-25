package com.shakur.cafehelp.Controller.PyController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shakur.cafehelp.DTO.MlDTO.AnaliticDTO.*;
import com.shakur.cafehelp.exception.PythonServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class PythonAnalyticsClient {

    private final RestTemplate restTemplate;
    private final String pythonApiUrl;
    private final ObjectMapper objectMapper;

    public PythonAnalyticsClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${python.api.url:http://localhost:8000}") String configuredPythonApiUrl,
            @Value("${internal.service.token:}") String configuredInternalServiceToken,
            @Value("${internal.api.contract-version:1}") String contractVersion,
            @Value("${python.client.connect-timeout:3s}") Duration connectTimeout,
            @Value("${python.client.read-timeout:30s}") Duration readTimeout,
            ObjectMapper objectMapper
    ) {
        this.restTemplate = configureRestTemplate(
                restTemplateBuilder,
                configuredInternalServiceToken,
                contractVersion,
                connectTimeout,
                readTimeout
        );
        this.pythonApiUrl = configuredPythonApiUrl.replaceAll("/+$", "");
        this.objectMapper = objectMapper;
        log.info("PythonAnalyticsClient initialized");
    }

    private RestTemplate configureRestTemplate(
            RestTemplateBuilder builder,
            String serviceToken,
            String contractVersion,
            Duration connectTimeout,
            Duration readTimeout
    ) {
        return builder
                .connectTimeout(connectTimeout)
                .readTimeout(readTimeout)
                .additionalInterceptors(
                        new ServiceTokenInterceptor(serviceToken, contractVersion),
                        new LoggingInterceptor()
                )
                .build();
    }

    private static class ServiceTokenInterceptor implements ClientHttpRequestInterceptor {
        private final String serviceToken;
        private final String contractVersion;

        private ServiceTokenInterceptor(String serviceToken, String contractVersion) {
            this.serviceToken = serviceToken == null ? "" : serviceToken;
            this.contractVersion = contractVersion;
        }

        @Override
        public ClientHttpResponse intercept(
                HttpRequest request,
                byte[] body,
                ClientHttpRequestExecution execution
        ) throws IOException {
            if (!serviceToken.isBlank()) {
                request.getHeaders().set("X-Service-Token", serviceToken);
            }
            request.getHeaders().set("X-Contract-Version", contractVersion);
            return execution.execute(request, body);
        }
    }

    private static class LoggingInterceptor implements ClientHttpRequestInterceptor {
        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                            ClientHttpRequestExecution execution)
                throws IOException {
            long startTime = System.currentTimeMillis();
            log.info("🚀 Python API Request: {} {}", request.getMethod(), request.getURI());

            ClientHttpResponse response = execution.execute(request, body);
            long duration = System.currentTimeMillis() - startTime;

            log.info("📨 Python API Response: {} ({} ms)", response.getStatusCode(), duration);
            return response;
        }
    }

    /**
     * Проверка доступности Python сервиса
     */
    public boolean isPythonServiceAvailable() {
        String url = pythonApiUrl + "/health";
        log.debug("Checking Python health endpoint");
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            boolean available = response.getStatusCode().is2xxSuccessful();
            log.info("✅ Python service health check: {}", available ? "UP" : "DOWN");
            return available;
        } catch (Exception e) {
            log.warn("Python service health check failed: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * Запрос всех данных для дашборда с подробным логированием
     */
    public DashboardDataDTO getDashboardDataFromPython(
            String timeRange,
            LocalDateTime startDate,
            LocalDateTime endDate,
            boolean refresh) {

        // ВАЖНО: используем timeRange (camelCase) как в Java контроллере
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(pythonApiUrl)
                .path("/api/analytics/dashboard")
                .queryParam("timeRange", timeRange)  // <- camelCase!
                .queryParam("refresh", refresh);

        if (startDate != null) {
            builder.queryParam("startDate", startDate.toLocalDate().toString());
        }
        if (endDate != null) {
            builder.queryParam("endDate", endDate.toLocalDate().toString());
        }

        String url = builder.toUriString();
        log.debug("Requesting Python analytics dashboard");

        try {
            ResponseEntity<String> rawResponse = restTemplate.getForEntity(url, String.class);
            String body = requireBody(rawResponse, "dashboard");
            DashboardDataDTO data = objectMapper.readValue(body, DashboardDataDTO.class);
            if (!data.isValid()) {
                throw PythonServiceException.invalidResponse(
                        new IllegalStateException("Incomplete dashboard response")
                );
            }
            data.setGeneratedAt(String.valueOf(LocalDateTime.now()));
            data.setIsCached(false);
            if (data.getDataSource() == null) {
                data.setDataSource("Python Analytics API");
            }

            return data;

        } catch (HttpClientErrorException e) {
            log.error("Python analytics request rejected with status {}", e.getStatusCode());
            throw PythonServiceException.translate(e);
        } catch (PythonServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Python dashboard request failed: {}", e.getClass().getSimpleName());
            throw PythonServiceException.translate(e);
        }
    }

    /**
     * Запрос KPI данных из Python
     */
    public KpiDataDTO getKpiFromPython(String timeRange, boolean forceRefresh) {
        String url = UriComponentsBuilder.fromUriString(pythonApiUrl)
                .path("/api/analytics/kpi")
                .queryParam("timeRange", timeRange)  // camelCase
                .queryParam("refresh", forceRefresh)
                .toUriString();

        log.debug("Sending KPI request to Python");

        try {
            ResponseEntity<KpiDataDTO> response = restTemplate.getForEntity(url, KpiDataDTO.class);
            KpiDataDTO kpi = requireBody(response, "kpi");
            if (kpi.getTotalProfit() == null
                    || kpi.getTotalSales() == null
                    || kpi.getProfitChange() == null
                    || kpi.getSalesChange() == null) {
                throw PythonServiceException.invalidResponse(
                        new IllegalStateException("Incomplete KPI response")
                );
            }
            return kpi;

        } catch (PythonServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Python KPI request failed: {}", e.getClass().getSimpleName());
            throw PythonServiceException.translate(e);
        }
    }

    /**
     * Запрос топ роллов из Python
     */
    public List<TopRollDTO> getTopRollsFromPython(String timeRange, int limit, String sortBy) {
        String url = UriComponentsBuilder.fromUriString(pythonApiUrl)
                .path("/api/analytics/top-rolls")
                .queryParam("timeRange", timeRange)  // camelCase
                .queryParam("limit", limit)
                .queryParam("sortBy", sortBy)  // camelCase
                .toUriString();

        log.debug("Sending top rolls request to Python");

        try {
            ResponseEntity<List<TopRollDTO>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<TopRollDTO>>() {}
            );

            return requireBody(response, "top rolls");
        } catch (PythonServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Python top rolls request failed: {}", e.getClass().getSimpleName());
            throw PythonServiceException.translate(e);
        }
    }

    /**
     * Запрос трендов продаж
     */
    public List<SalesTrendDTO> getSalesTrendFromPython(String timeRange, String granularity) {
        String url = UriComponentsBuilder.fromUriString(pythonApiUrl)
                .path("/api/analytics/sales-trend")
                .queryParam("timeRange", timeRange)  // camelCase
                .queryParam("granularity", granularity)
                .toUriString();

        log.debug("Sending sales trend request to Python");

        try {
            ResponseEntity<List<SalesTrendDTO>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<SalesTrendDTO>>() {}
            );

            return requireBody(response, "sales trend");
        } catch (PythonServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Python sales trend request failed: {}", e.getClass().getSimpleName());
            throw PythonServiceException.translate(e);
        }
    }

    /**
     * Запрос AI инсайтов
     */
    public List<InsightDTO> getInsightsFromPython(String timeRange, String priority) {
        String url = UriComponentsBuilder.fromUriString(pythonApiUrl)
                .path("/api/analytics/insights")
                .queryParam("timeRange", timeRange)  // camelCase
                .queryParamIfPresent("priority", Optional.ofNullable(priority))
                .toUriString();

        log.debug("Sending insights request to Python");

        try {
            ResponseEntity<List<InsightDTO>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<InsightDTO>>() {}
            );

            return requireBody(response, "insights");
        } catch (PythonServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Python insights request failed: {}", e.getClass().getSimpleName());
            throw PythonServiceException.translate(e);
        }
    }

    private <T> T requireBody(ResponseEntity<T> response, String operation) {
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw PythonServiceException.invalidResponse(
                    new IllegalStateException("Missing response body for " + operation)
            );
        }
        return response.getBody();
    }

    /**
     * Получить статистику использования Python API
     */
    public Map<String, Object> getPythonApiStats() {
        boolean available = isPythonServiceAvailable();
        log.info("Python API availability checked: {}", available);
        return Map.of(
                "available", available,
                "lastChecked", LocalDateTime.now()
        );
    }
}
