package com.shakur.cafehelp.Controller.PyController;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shakur.cafehelp.DTO.MlDTO.AnaliticDTO.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class PythonAnalyticsClient {

    private static RestTemplate restTemplate;
    private static String pythonApiUrl = "http://localhost:8000";

    public PythonAnalyticsClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${python.api.url:http://localhost:8000}") String configuredPythonApiUrl
    ) {
        this.restTemplate = configureRestTemplate(restTemplateBuilder);
        pythonApiUrl = configuredPythonApiUrl;
        log.info("PythonAnalyticsClient initialized with URL: {}", pythonApiUrl);
    }

    private RestTemplate configureRestTemplate(RestTemplateBuilder builder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(30));

        return builder
                .requestFactory(() -> requestFactory)
                .additionalInterceptors(new LoggingInterceptor())
                .build();
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
    public static boolean isPythonServiceAvailable() {
        String url = pythonApiUrl + "/health";
        log.info("🩺 Checking Python health at: {}", url);
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            boolean available = response.getStatusCode().is2xxSuccessful();
            log.info("✅ Python service health check: {}", available ? "UP" : "DOWN");
            return available;
        } catch (Exception e) {
            log.warn("❌ Python service is not available: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Запрос всех данных для дашборда с подробным логированием
     */
    public static DashboardDataDTO getDashboardDataFromPython(
            String timeRange,
            LocalDateTime startDate,
            LocalDateTime endDate,
            boolean refresh) {

        // ВАЖНО: используем timeRange (camelCase) как в Java контроллере
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(pythonApiUrl)
                .path("/api/analytics/dashboard")
                .queryParam("timeRange", timeRange)  // <- camelCase!
                .queryParam("refresh", refresh);

        if (startDate != null) {
            builder.queryParam("start_date", startDate.toString());
        }
        if (endDate != null) {
            builder.queryParam("end_date", endDate.toString());
        }

        String url = builder.toUriString();
        log.info("🔗 Sending to Python: {}", url);

        try {
            // 1. Сначала получаем raw JSON для дебага
            ResponseEntity<String> rawResponse = restTemplate.getForEntity(url, String.class);
            log.info("📥 Python response status: {}", rawResponse.getStatusCode());
            log.info("📥 Python response body length: {} chars",
                    rawResponse.getBody() != null ? rawResponse.getBody().length() : 0);

            // Логируем первые 200 символов ответа
            if (rawResponse.getBody() != null && rawResponse.getBody().length() > 0) {
                String preview = rawResponse.getBody().length() > 200
                        ? rawResponse.getBody().substring(0, 200) + "..."
                        : rawResponse.getBody();
                log.info("📥 Python response preview: {}", preview);
            }

            // 2. Создаем ObjectMapper с настройками для snake_case
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            // 3. Маппим JSON в DTO
            DashboardDataDTO data = mapper.readValue(rawResponse.getBody(), DashboardDataDTO.class);

            // 4. Устанавливаем дополнительные поля
            data.setGeneratedAt(String.valueOf(LocalDateTime.now()));
            data.setIsCached(false);
            if (data.getDataSource() == null) {
                data.setDataSource("Python Analytics API");
            }

            log.info("✅ Successfully fetched dashboard data from Python");
            log.info("✅ Data summary - KPI: {}, TopRolls: {}, Insights: {}",
                    data.getKpi() != null ? "present" : "null",
                    data.getTopRolls() != null ? data.getTopRolls().size() : 0,
                    data.getInsights() != null ? data.getInsights().size() : 0);
            return data;

        } catch (HttpClientErrorException e) {
            log.error("❌ HTTP error from Python: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            return getFallbackDashboardData(timeRange, "HTTP error: " + e.getStatusCode());

        } catch (Exception e) {
            log.error("❌ Error fetching dashboard data from Python: {}", e.getMessage());
            return getFallbackDashboardData(timeRange, "Connection error: " + e.getMessage());
        }
    }

    /**
     * Запрос KPI данных из Python
     */
    public KpiDataDTO getKpiFromPython(String timeRange, boolean forceRefresh) {
        String url = UriComponentsBuilder.fromHttpUrl(pythonApiUrl)
                .path("/api/analytics/kpi")
                .queryParam("timeRange", timeRange)  // camelCase
                .queryParam("refresh", forceRefresh)
                .toUriString();

        log.info("📊 Sending KPI request to Python: {}", url);

        try {
            // Получаем raw JSON для дебага
            ResponseEntity<String> rawResponse = restTemplate.getForEntity(url, String.class);
            log.info("📥 KPI response status: {}", rawResponse.getStatusCode());

            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            KpiDataDTO kpi = mapper.readValue(rawResponse.getBody(), KpiDataDTO.class);
            log.info("✅ Successfully fetched KPI data");
            return kpi;

        } catch (Exception e) {
            log.error("❌ Error fetching KPI from Python: {}", e.getMessage());
            return getFallbackKpi();
        }
    }

    /**
     * Запрос топ роллов из Python
     */
    public List<TopRollDTO> getTopRollsFromPython(String timeRange, int limit, String sortBy) {
        String url = UriComponentsBuilder.fromHttpUrl(pythonApiUrl)
                .path("/api/analytics/top-rolls")
                .queryParam("timeRange", timeRange)  // camelCase
                .queryParam("limit", limit)
                .queryParam("sortBy", sortBy)  // camelCase
                .toUriString();

        log.info("🏆 Sending top rolls request to Python: {}", url);

        try {
            ResponseEntity<List<TopRollDTO>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<TopRollDTO>>() {}
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                log.info("✅ Successfully fetched {} top rolls", response.getBody().size());
                return response.getBody();
            }

            log.warn("⚠️ Empty response for top rolls");
            return Collections.emptyList();

        } catch (Exception e) {
            log.error("❌ Error fetching top rolls from Python: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Запрос трендов продаж
     */
    public List<SalesTrendDTO> getSalesTrendFromPython(String timeRange, String granularity) {
        String url = UriComponentsBuilder.fromHttpUrl(pythonApiUrl)
                .path("/api/analytics/sales-trend")
                .queryParam("timeRange", timeRange)  // camelCase
                .queryParam("granularity", granularity)
                .toUriString();

        log.info("📈 Sending sales trend request to Python: {}", url);

        try {
            ResponseEntity<List<SalesTrendDTO>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<SalesTrendDTO>>() {}
            );

            return response.getBody() != null ? response.getBody() : Collections.emptyList();

        } catch (Exception e) {
            log.error("❌ Error fetching sales trend from Python: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Запрос AI инсайтов
     */
    public List<InsightDTO> getInsightsFromPython(String timeRange, String priority) {
        String url = UriComponentsBuilder.fromHttpUrl(pythonApiUrl)
                .path("/api/analytics/insights")
                .queryParam("timeRange", timeRange)  // camelCase
                .queryParamIfPresent("priority", Optional.ofNullable(priority))
                .toUriString();

        log.info("💡 Sending insights request to Python: {}", url);

        try {
            ResponseEntity<List<InsightDTO>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<InsightDTO>>() {}
            );

            return response.getBody() != null ? response.getBody() : Collections.emptyList();

        } catch (Exception e) {
            log.error("❌ Error fetching insights from Python: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Отправка фидбека по инсайту в Python
     */
    public boolean sendInsightFeedback(String insightId, String action, Map<String, Object> metadata) {
        String url = pythonApiUrl + "/api/analytics/insights/" + insightId + "/feedback";

        log.info("📝 Sending feedback to Python: {}", url);

        try {
            Map<String, Object> requestBody = Map.of(
                    "action", action,
                    "metadata", metadata,
                    "timestamp", LocalDateTime.now().toString()
            );

            ResponseEntity<Void> response = restTemplate.postForEntity(
                    url,
                    requestBody,
                    Void.class
            );

            boolean success = response.getStatusCode().is2xxSuccessful();
            log.info("✅ Feedback sent successfully: {}", success);
            return success;

        } catch (Exception e) {
            log.error("❌ Error sending insight feedback to Python: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Fallback данные при недоступности Python
     */
    private static KpiDataDTO getFallbackKpi() {
        log.warn("⚠️ Using fallback KPI data");
        return KpiDataDTO.builder()
                .totalProfit(0.0)
                .totalSales(0)
                .profitChange(0.0)
                .salesChange(0.0)
                .modelAccuracy(0.0)
                .dataSource("Fallback")
                .isFallback(true)
                .build();
    }

    private static DashboardDataDTO getFallbackDashboardData(String timeRange, String error) {
        log.warn("⚠️ Using fallback dashboard data: {}", error);
        return DashboardDataDTO.builder()
                .kpi(getFallbackKpi())
                .topRolls(Collections.emptyList())
                .salesTrend(Collections.emptyList())
                .insights(Collections.emptyList())
                .timeRange(timeRange)
                .generatedAt(String.valueOf(LocalDateTime.now()))
                .dataSource("Fallback: " + error)
                .isFallback(true)
                .errorMessage("Python analytics service error: " + error)
                .build();
    }

    /**
     * Получить статистику использования Python API
     */
    public Map<String, Object> getPythonApiStats() {
        boolean available = isPythonServiceAvailable();
        log.info("📊 Python API Stats - Available: {}, URL: {}", available, pythonApiUrl);
        return Map.of(
                "url", pythonApiUrl,
                "available", available,
                "lastChecked", LocalDateTime.now()
        );
    }
}
