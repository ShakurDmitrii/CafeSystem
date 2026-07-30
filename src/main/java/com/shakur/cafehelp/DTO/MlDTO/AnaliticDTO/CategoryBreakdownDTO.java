package com.shakur.cafehelp.DTO.MlDTO.AnaliticDTO;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class CategoryBreakdownDTO {
    private String category;
    private Integer sales;
    private Double revenue;
    private Double profit;
    private Double percentage;
}
