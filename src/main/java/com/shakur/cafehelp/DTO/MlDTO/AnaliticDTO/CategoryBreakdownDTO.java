package com.shakur.cafehelp.DTO.MlDTO.AnaliticDTO;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
class CategoryBreakdownDTO {
    private String category;
    private Integer sales;
    private Double revenue;
    private Double profit;
    private Double percentage;
}