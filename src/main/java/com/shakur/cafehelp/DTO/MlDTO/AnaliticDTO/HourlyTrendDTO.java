package com.shakur.cafehelp.DTO.MlDTO.AnaliticDTO;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

// Дополнительные DTO для расширения
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class HourlyTrendDTO {
    private String hour;
    private Integer sales;
    private Double revenue;
    private Double avgOrderValue;
}
