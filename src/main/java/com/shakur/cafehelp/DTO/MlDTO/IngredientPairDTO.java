package com.shakur.cafehelp.DTO.MlDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
class IngredientPairDTO {
    private String ingredientA;
    private String ingredientB;
    private Double synergyScore;
    private String strength; // "strong", "medium", "weak"
    private String recommendation;
}