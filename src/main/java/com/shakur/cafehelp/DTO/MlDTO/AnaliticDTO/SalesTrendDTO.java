package com.shakur.cafehelp.DTO.MlDTO.AnaliticDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SalesTrendDTO {

    // Дата в формате "2024-01-15"
    @JsonProperty("date")
    private String date;

    // День недели или период: "Пн", "Вт", "10:00-12:00"
    @JsonProperty("period")
    private String period;

    // Фактические продажи (количество)
    @JsonProperty("sales")
    private Integer sales;

    // Прогнозируемые продажи (от ML модели)
    @JsonProperty("predicted")
    private Integer predicted;

    // Выручка за период
    @JsonProperty("revenue")
    private Double revenue;

    // Количество заказов
    @JsonProperty("orders")
    private Integer orders;

    // Отклонение от прогноза в %
    @JsonProperty("deviation")
    private Double deviation;

    // Метод для расчета отклонения
    public Double calculateDeviation() {
        if (predicted == null || predicted == 0) return 0.0;
        return ((sales - predicted) / (double) predicted) * 100;
    }

    // Дополнительные метрики для графика
    @JsonProperty("target")
    private Integer target; // План продаж

    @JsonProperty("previous_year")
    private Integer previousYear; // Продажи за прошлый год
}
