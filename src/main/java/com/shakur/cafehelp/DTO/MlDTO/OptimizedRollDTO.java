package com.shakur.cafehelp.DTO.MlDTO;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class OptimizedRollDTO {
    // Идентификаторы
    private String id;
    private String name;

    // Состав
    private List<String> ingredients;
    private List<String> ingredientCategories;

    // Прогнозы
    private Double predictedSales;
    private Double adjustedSales;
    private Double confidenceScore;
    private Double lowerBound; // нижняя граница доверительного интервала
    private Double upperBound; // верхняя граница доверительного интервала

    // Экономика
    private Double estimatedCost;
    private Double estimatedProfit;
    private Double profitMargin; // 0-1
    private Double roi; // Return on Investment в %

    // Анализ
    private Double complexityScore; // 0-1 (сложность приготовления)
    private Double noveltyScore; // 0-1 (новизна комбинации)
    private Double pairSynergyScore; // 0-1 (общая синергия пар)
    private List<IngredientPairDTO> pairAnalysis;

    // Объяснение рекомендации
    private List<String> reasoning; // почему рекомендован
    private String bestFor; // "lunch", "dinner", "takeaway", "delivery"
    private List<String> alternatives; // альтернативные варианты

    // Бизнес-метрики
    private Double expectedWeeklySales;
    private Double expectedMonthlyProfit;
    private Integer breakEvenDays; // дней до окупаемости

    // Метрики алгоритма
    private Integer generationFound;
    private Double fitnessScore;

    // Helper
    public Double getConfidenceIntervalWidth() {
        return upperBound - lowerBound;
    }
}