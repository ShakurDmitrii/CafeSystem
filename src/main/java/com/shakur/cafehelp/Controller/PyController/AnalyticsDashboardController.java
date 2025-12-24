package com.shakur.cafehelp.Controller.PyController;


import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import com.shakur.cafehelp.DTO.MlDTO.AnaliticDTO.HealthCheckDTO;
import com.shakur.cafehelp.DTO.MlDTO.AnaliticDTO.*;
import com.shakur.cafehelp.Service.MlServices.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Analytics Dashboard", description = "API для аналитического дашборда с AI инсайтами")
public class AnalyticsDashboardController {

    private final AnalyticsService analyticsService;

    /**
     * ГЛАВНЫЙ ЭНДПОИНТ - все данные для дашборда одним запросом
     * Используется фронтендом React для отображения всей аналитики
     */
    @Operation(
            summary = "Получить все данные для дашборда",
            description = "Возвращает KPI, топ роллов, тренды продаж и AI инсайты для фронтенда"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Данные успешно получены",
                    content = @Content(schema = @Schema(implementation = DashboardDataDTO.class))),
            @ApiResponse(responseCode = "400", description = "Неверный параметр timeRange"),
            @ApiResponse(responseCode = "502", description = "Ошибка соединения с Python ML сервисом")
    })
    @GetMapping(value = "/dashboard", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DashboardDataDTO> getDashboardData(
            @Parameter(description = "Временной диапазон: day, week, month, quarter, year",
                    example = "week")
            @RequestParam(defaultValue = "week") String timeRange,

            @Parameter(description = "Начальная дата (опционально)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,

            @Parameter(description = "Конечная дата (опционально)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,

            @Parameter(description = "Обновить кэш (игнорировать закэшированные данные)")
            @RequestParam(defaultValue = "false") boolean refresh) {

        log.info("Dashboard data request: timeRange={}, startDate={}, endDate={}, refresh={}",
                timeRange, startDate, endDate, refresh);

        try {
            DashboardDataDTO dashboardData = analyticsService.getDashboardData(
                    timeRange, startDate, endDate, refresh);

            // Настраиваем кэширование на клиенте (браузере)
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES))
                    .body(dashboardData);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid timeRange parameter: {}", timeRange);
            return ResponseEntity.badRequest()
                    .body(createErrorResponse("Неверный параметр timeRange. Допустимые значения: day, week, month, quarter, year", timeRange));

        } catch (Exception e) {
            log.error("Error fetching dashboard data: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(createErrorResponse("Временная ошибка сервиса аналитики. Попробуйте позже.", timeRange));
        }
    }

    /**
     * Только KPI метрики (если нужны отдельно)
     */
    @Operation(summary = "Получить KPI метрики")
    @GetMapping("/kpi")
    public ResponseEntity<KpiDataDTO> getKpiData(
            @RequestParam(defaultValue = "week") String timeRange) {

        log.debug("KPI data request: timeRange={}", timeRange);

        KpiDataDTO kpiData = analyticsService.getKpiData(timeRange);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(2, TimeUnit.MINUTES))
                .body(kpiData);
    }

    /**
     * Топ роллов по продажам
     */
    @Operation(summary = "Получить топ роллов")
    @GetMapping("/top-rolls")
    public ResponseEntity<List<TopRollDTO>> getTopRolls(
            @RequestParam(defaultValue = "week") String timeRange,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "sales") String sortBy) {

        log.debug("Top rolls request: timeRange={}, limit={}, sortBy={}", timeRange, limit, sortBy);

        List<TopRollDTO> topRolls = analyticsService.getTopRolls(timeRange, limit, sortBy);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES))
                .body(topRolls);
    }

    /**
     * Тренды продаж с прогнозами
     */
    @Operation(summary = "Получить тренды продаж")
    @GetMapping("/sales-trend")
    public ResponseEntity<List<SalesTrendDTO>> getSalesTrend(
            @RequestParam(defaultValue = "week") String timeRange,
            @RequestParam(defaultValue = "day") String granularity) {

        log.debug("Sales trend request: timeRange={}, granularity={}", timeRange, granularity);

        List<SalesTrendDTO> salesTrend = analyticsService.getSalesTrend(timeRange, granularity);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES))
                .body(salesTrend);
    }

    /**
     * AI инсайты и рекомендации
     */
    @Operation(summary = "Получить AI инсайты")
    @GetMapping("/insights")
    public ResponseEntity<List<InsightDTO>> getInsights(
            @RequestParam(defaultValue = "week") String timeRange,
            @RequestParam(required = false) String priority) {

        log.debug("Insights request: timeRange={}, priority={}", timeRange, priority);

        List<InsightDTO> insights = analyticsService.getInsights(timeRange);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(15, TimeUnit.MINUTES))
                .body(insights);
    }

    /**
     * Проверка здоровья аналитического сервиса
     */
    @Operation(summary = "Проверка здоровья сервиса")
    @GetMapping("/health")
    public ResponseEntity<HealthCheckDTO> getHealth() {

        HealthCheckDTO health = analyticsService.checkAnalyticsHealth();

        return ResponseEntity.ok(health);
    }


    /**
     * Получить статистику использования API
     */
    @Operation(summary = "Статистика использования аналитики")
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getApiStats() {

        Map<String, Object> stats = Map.of(
                "service", "Analytics Dashboard API",
                "version", "1.0",
                "timestamp", LocalDateTime.now(),
                "endpoints", List.of(
                        Map.of("path", "/api/analytics/dashboard", "method", "GET"),
                        Map.of("path", "/api/analytics/kpi", "method", "GET"),
                        Map.of("path", "/api/analytics/top-rolls", "method", "GET"),
                        Map.of("path", "/api/analytics/sales-trend", "method", "GET"),
                        Map.of("path", "/api/analytics/insights", "method", "GET")
                )
        );

        return ResponseEntity.ok(stats);
    }
    @GetMapping("/test-python")
    public Map<String, Object> testPython() {
        Map<String, Object> result = new HashMap<>();

        try {
            // 1. Проверь доступность Python
            boolean pythonAvailable = PythonAnalyticsClient.isPythonServiceAvailable();
            result.put("pythonAvailable", pythonAvailable);

            if (pythonAvailable) {
                // 2. Попробуй вызвать Python напрямую
                try {
                    String url = "http://localhost:8000/api/analytics/dashboard?timeRange=week";
                    RestTemplate rt = new RestTemplate();
                    String response = rt.getForObject(url, String.class);
                    result.put("pythonResponseRaw", response);
                } catch (Exception e) {
                    result.put("pythonCallError", e.getMessage());
                }

                // 3. Попробуй через pythonClient
                try {
                    DashboardDataDTO data = PythonAnalyticsClient.getDashboardDataFromPython("week", null, null, false);
                    result.put("pythonClientSuccess", data != null);
                } catch (Exception e) {
                    result.put("pythonClientError", e.getMessage());
                }
            }

            // 4. Проверь RestTemplate
            try {
                RestTemplate testRt = new RestTemplate();
                String testUrl = "http://localhost:8000/health";
                ResponseEntity<String> resp = testRt.getForEntity(testUrl, String.class);
                result.put("restTemplateTest", resp.getStatusCode());
            } catch (Exception e) {
                result.put("restTemplateError", e.getMessage());
            }

        } catch (Exception e) {
            result.put("testError", e.getMessage());
        }

        result.put("timestamp", LocalDateTime.now());
        return result;
    }
    /**
     * Создание ETag для кэширования
     */


    /**
     * Создание ответа с ошибкой
     */
    private DashboardDataDTO createErrorResponse(String message, String timeRange) {
        return DashboardDataDTO.builder()
                .kpi(KpiDataDTO.builder()
                        .totalProfit(0.0)
                        .totalSales(0)
                        .profitChange(0.0)
                        .salesChange(0.0)
                        .modelAccuracy(0.0)
                        .dataSource("Error")
                        .isFallback(true)
                        .build())
                .topRolls(List.of())
                .salesTrend(List.of())
                .insights(List.of())
                .timeRange(timeRange)
                .generatedAt(String.valueOf(LocalDateTime.now()))
                .isFallback(true)
                .errorMessage(message)
                .build();
    }
}