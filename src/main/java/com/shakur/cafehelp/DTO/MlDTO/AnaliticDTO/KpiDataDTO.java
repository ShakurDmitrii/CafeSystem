package com.shakur.cafehelp.DTO.MlDTO.AnaliticDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KpiDataDTO {

    @JsonProperty("total_profit")
    private Double totalProfit;

    @JsonProperty("total_sales")
    private Integer totalSales;

    @JsonProperty("profit_change")
    private Double profitChange;

    @JsonProperty("sales_change")
    private Double salesChange;

    @JsonProperty("model_accuracy")
    private Double modelAccuracy;

    // Дополнительные поля для обогащения
    private String dataSource;
    private LocalDateTime lastUpdated;
    private Boolean isFallback;
    private String errorMessage;

    // Вычисляемые поля
    public Double getAverageOrderValue() {
        if (totalSales == null || totalSales == 0 || totalProfit == null) return 0.0;
        return totalProfit / totalSales;
    }

    public String getProfitTrend() {
        if (profitChange == null) return "stable";
        if (profitChange > 0) return "up";
        if (profitChange < 0) return "down";
        return "stable";
    }
}