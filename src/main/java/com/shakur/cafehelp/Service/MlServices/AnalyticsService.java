package com.shakur.cafehelp.Service.MlServices;

import com.shakur.cafehelp.Controller.PyController.PythonAnalyticsClient;
import com.shakur.cafehelp.DTO.MlDTO.AnaliticDTO.*;
import com.shakur.cafehelp.exception.PythonServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private static final Set<String> TIME_RANGES = Set.of("day", "week", "month", "quarter", "year");
    private static final Set<String> TOP_ROLL_SORTS = Set.of("sales", "profit", "margin");

    private final PythonAnalyticsClient pythonClient;

    /**
     * Получить все данные для дашборда
     */
    @Cacheable(value = "dashboardData", key = "#timeRange + #refresh", unless = "#refresh")
    public DashboardDataDTO getDashboardData(String timeRange, LocalDate startDate,
                                             LocalDate endDate, boolean refresh) {

        validateTimeRange(timeRange);
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate не может быть позже endDate");
        }

        log.info("📊 AnalyticsService.getDashboardData: timeRange={}", timeRange);

        try {
            // Преобразуем LocalDate в LocalDateTime если нужно
            LocalDateTime startDateTime = startDate != null ?
                    startDate.atStartOfDay() : null;
            LocalDateTime endDateTime = endDate != null ?
                    endDate.atTime(23, 59, 59) : null;

            log.info("🔗 Calling PythonAnalyticsClient...");
            // Запрашиваем данные из Python
            DashboardDataDTO dashboardData = pythonClient.getDashboardDataFromPython(
                    timeRange, startDateTime, endDateTime, refresh
            );

            log.info("✅ Got data from Python, enriching...");
            // Обогащаем данными из Java БД если нужно
            return enrichWithJavaData(dashboardData);

        } catch (PythonServiceException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Analytics enrichment failed: {}", e.getClass().getSimpleName());
            throw PythonServiceException.translate(e);
        }
    }

    /**
     * Получить KPI метрики
     */
    @Cacheable(value = "kpiData", key = "#timeRange")
    public KpiDataDTO getKpiData(String timeRange) {
        validateTimeRange(timeRange);
        log.debug("Fetching KPI data: timeRange={}", timeRange);
        return pythonClient.getKpiFromPython(timeRange, false);
    }

    /**
     * Получить топ роллов
     */
    @Cacheable(value = "topRolls", key = "#timeRange + #limit + #sortBy")
    public List<TopRollDTO> getTopRolls(String timeRange, int limit, String sortBy) {
        validateTimeRange(timeRange);
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit должен быть от 1 до 100");
        }
        if (!TOP_ROLL_SORTS.contains(sortBy)) {
            throw new IllegalArgumentException("sortBy должен быть sales, profit или margin");
        }
        log.debug("Fetching top rolls: timeRange={}, limit={}, sortBy={}",
                timeRange, limit, sortBy);
        return pythonClient.getTopRollsFromPython(timeRange, limit, sortBy);
    }

    /**
     * Получить тренды продаж
     */
    @Cacheable(value = "salesTrend", key = "#timeRange + #granularity")
    public List<SalesTrendDTO> getSalesTrend(String timeRange, String granularity) {
        validateTimeRange(timeRange);
        if (!"day".equals(granularity)) {
            throw new IllegalArgumentException("Поддерживается только granularity=day");
        }
        log.debug("Fetching sales trend: timeRange={}, granularity={}",
                timeRange, granularity);
        return pythonClient.getSalesTrendFromPython(timeRange, granularity);
    }

    /**
     * Получить AI инсайты
     */
    @Cacheable(value = "insights", key = "#timeRange")
    public List<InsightDTO> getInsights(String timeRange) {
        validateTimeRange(timeRange);
        log.debug("Fetching insights: timeRange={}", timeRange);
        return pythonClient.getInsightsFromPython(timeRange, null);
    }

    /**
     * Обогатить данные из Java БД
     *
     * @return
     */
    private DashboardDataDTO enrichWithJavaData(DashboardDataDTO dashboardData) {
        if (dashboardData == null) {
            log.error("Cannot enrich null dashboard data");
            throw new IllegalStateException("Python analytics service returned no data");
        }

        try {
            if (dashboardData.getKpi() != null) {
                // Добавляем дополнительную информацию
                dashboardData.getKpi().setDataSource("Python ML + Java");
                dashboardData.getKpi().setLastUpdated(LocalDateTime.now());
            } else {
                log.warn("KPI data is null in dashboard");
            }

            // Добавляем метаданные
            dashboardData.setProcessedBy("Java Analytics Service v1.0");
            dashboardData.setApiVersion("1.0");
            dashboardData.setGeneratedAt(String.valueOf(LocalDateTime.now()));

            return dashboardData;

        } catch (Exception e) {
            log.error("Error enriching dashboard data: ", e);
            return dashboardData;
        }
    }
    /**
     * Проверить доступность сервиса аналитики
     */
    public HealthCheckDTO checkAnalyticsHealth() {
        boolean pythonAvailable = pythonClient.isPythonServiceAvailable();

        return HealthCheckDTO.builder()
                .status(pythonAvailable ? "HEALTHY" : "DEGRADED")
                .pythonService(pythonAvailable ? "UP" : "DOWN")
                .timestamp(LocalDateTime.now())
                .message(pythonAvailable ?
                        "All services are operational" :
                        "Python ML service is unavailable")
                .build();
    }

    private void validateTimeRange(String timeRange) {
        if (!TIME_RANGES.contains(timeRange)) {
            throw new IllegalArgumentException("Недопустимый timeRange");
        }
    }
}

