package com.shakur.cafehelp.DTO.MlDTO;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class OptimizationStatsDTO {
    private Integer populationSize;
    private Integer generations;
    private Integer uniqueSolutionsFound;
    private Double bestFitnessScore;
    private Double averageFitnessScore;
    private Long executionTimeMs;
    private Map<String, Integer> ingredientUsageFrequency;
    private Map<String, Double> categoryDistribution;

    // Процент успешных мутаций/кроссоверов
    private Double mutationSuccessRate;
    private Double crossoverSuccessRate;

    // Сходимость алгоритма
    private List<Double> bestFitnessPerGeneration;
    private List<Double> averageFitnessPerGeneration;
}