package com.shakur.cafehelp.DTO.MlDTO;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SaveGeneratedDishResponseDTO {
    private Integer dishId;
    private String dishName;
    private Integer techCardRowsCreated;
    private List<String> missingIngredients;
    private String status;
}
