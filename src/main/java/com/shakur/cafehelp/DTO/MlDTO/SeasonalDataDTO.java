package com.shakur.cafehelp.DTO.MlDTO;

import lombok.Data;

import java.util.List;

@Data
public class SeasonalDataDTO {
    private Integer currentMonth;
    private Double currentFactor;
    private List<SeasonalFactorDTO> monthlyFactors;
    private String season; // "winter", "spring", "summer", "autumn"
}
