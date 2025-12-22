package com.shakur.cafehelp.DTO.MlDTO;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RollPredictionRequestDTO {
    private List<String> ingredients;
    private Boolean includeCostAnalysis;
    private Boolean includeSeasonalAdjustment;
    private String locationId;
    private String season;
    private Map<String, Object> context; // дополнительный контекст
}

