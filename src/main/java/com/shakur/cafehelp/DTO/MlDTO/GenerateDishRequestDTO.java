package com.shakur.cafehelp.DTO.MlDTO;

import lombok.Data;

import java.util.List;

@Data
public class GenerateDishRequestDTO {
    private Integer days = 90;
    private Integer minIngredients = 3;
    private Integer maxIngredients = 6;
    private Integer populationSize = 80;
    private Integer generations = 40;
    private Double markup = 2.35;
    private List<String> mustInclude;
    private List<String> excludedIngredients;
}
