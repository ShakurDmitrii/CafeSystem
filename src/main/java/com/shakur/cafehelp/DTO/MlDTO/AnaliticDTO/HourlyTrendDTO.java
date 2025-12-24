package com.shakur.cafehelp.DTO.MlDTO.AnaliticDTO;

import lombok.Builder;
import lombok.Data;

// Дополнительные DTO для расширения
@Data
@Builder
class HourlyTrendDTO {
    private String hour;
    private Integer sales;
    private Double revenue;
    private Double avgOrderValue;
}