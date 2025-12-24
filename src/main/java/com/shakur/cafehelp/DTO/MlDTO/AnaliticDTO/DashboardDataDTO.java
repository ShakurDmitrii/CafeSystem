package com.shakur.cafehelp.DTO.MlDTO.AnaliticDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Setter
public class DashboardDataDTO {

    @JsonProperty("kpi")
    private KpiDataDTO kpi;

    @JsonProperty("top_rolls")
    private List<TopRollDTO> topRolls;

    @JsonProperty("sales_trend")
    private List<SalesTrendDTO> salesTrend;

    @JsonProperty("insights")
    private List<InsightDTO> insights;

    @JsonProperty("hourly_trend")
    private List<HourlyTrendDTO> hourlyTrend;

    @JsonProperty("category_breakdown")
    private List<CategoryBreakdownDTO> categoryBreakdown;

    @JsonProperty("time_range")
    private String timeRange;

    @JsonProperty("generated_at")
    private  String generatedAt;

    @JsonProperty("cache_key")
    private String cacheKey;

    @JsonProperty("is_cached")
    private Boolean isCached;

    // Добавь это поле если хочешь иметь dataSource на уровне Dashboard
    @JsonProperty("data_source")
    private String dataSource;

    // Дополнительные поля для обогащения
    private String processedBy;
    private String apiVersion;

    @JsonProperty("is_fallback")
    private Boolean isFallback;

    @JsonProperty("error_message")
    private String errorMessage;

    // Setters
    public void setGeneratedAt( String generatedAt) {
        this.generatedAt = generatedAt;
    }

    public void setIsCached(Boolean isCached) {
        this.isCached = isCached;
    }

    public void setDataSource(String dataSource) {
        this.dataSource = dataSource;
        // Также устанавливаем в KPI если есть
        if (this.kpi != null) {
            this.kpi.setDataSource(dataSource);
        }
    }

    public boolean isValid() {
        return kpi != null &&
                topRolls != null &&
                salesTrend != null &&
                insights != null &&
                (isFallback == null || !isFallback);
    }
}