package com.shakur.cafehelp.Service.MlServices;

import com.shakur.cafehelp.Controller.PyController.PythonAnalyticsClient;
import com.shakur.cafehelp.DTO.MlDTO.AnaliticDTO.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final PythonAnalyticsClient pythonClient;

    /**
     * Получить все данные для дашборда
     */
    @Cacheable(value = "dashboardData", key = "#timeRange + #refresh", unless = "#refresh")
    public DashboardDataDTO getDashboardData(String timeRange, LocalDate startDate,
                                             LocalDate endDate, boolean refresh) {

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

            if (dashboardData == null) {
                log.error("❌ Python client returned null");
                throw new RuntimeException("Python analytics service returned null");
            }

            log.info("✅ Got data from Python, enriching...");
            // Обогащаем данными из Java БД если нужно
            return enrichWithJavaData(dashboardData);

        } catch (Exception e) {
            log.error("❌ ERROR in getDashboardData: ", e); // ВАЖНО: полный stack trace
            throw new RuntimeException("Failed to fetch analytics data: " + e.getMessage(), e);
        }
    }

    /**
     * Получить KPI метрики
     */
    @Cacheable(value = "kpiData", key = "#timeRange")
    public KpiDataDTO getKpiData(String timeRange) {
        log.debug("Fetching KPI data: timeRange={}", timeRange);
        return pythonClient.getKpiFromPython(timeRange, false);
    }

    /**
     * Получить топ роллов
     */
    @Cacheable(value = "topRolls", key = "#timeRange + #limit + #sortBy")
    public List<TopRollDTO> getTopRolls(String timeRange, int limit, String sortBy) {
        log.debug("Fetching top rolls: timeRange={}, limit={}, sortBy={}",
                timeRange, limit, sortBy);
        return pythonClient.getTopRollsFromPython(timeRange, limit, sortBy);
    }

    /**
     * Получить тренды продаж
     */
    @Cacheable(value = "salesTrend", key = "#timeRange + #granularity")
    public List<SalesTrendDTO> getSalesTrend(String timeRange, String granularity) {
        log.debug("Fetching sales trend: timeRange={}, granularity={}",
                timeRange, granularity);
        return pythonClient.getSalesTrendFromPython(timeRange, granularity);
    }

    /**
     * Получить AI инсайты
     */
    @Cacheable(value = "insights", key = "#timeRange")
    public List<InsightDTO> getInsights(String timeRange) {
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
}

