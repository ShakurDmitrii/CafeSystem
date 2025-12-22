package com.shakur.cafehelp.DTO.MlDTO;


import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SeasonalFactorDTO {
    private Integer month; // 1-12
    private Double factor; // множитель продаж (0.5-2.0)
    private String description;
    private List<String> recommendedIngredients;
    private List<String> recommendedCategories;
}

