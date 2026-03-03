package com.shakur.cafehelp.DTO.MlDTO;

import lombok.Data;

import java.util.List;

@Data
public class SaveGeneratedDishRequestDTO {
    private String dishName;
    private String category;
    private Double recommendedPrice;
    private Double estimatedCost;
    private Double weightGrams;
    private List<String> ingredients;
    private List<TechCardItemDTO> techCard;

    @Data
    public static class TechCardItemDTO {
        private String ingredientName;
        private Double quantityGrams;
        private Double unitCost;
        private Double totalCost;
    }
}
