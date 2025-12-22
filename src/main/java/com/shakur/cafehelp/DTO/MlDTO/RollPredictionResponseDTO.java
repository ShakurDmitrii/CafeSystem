package com.shakur.cafehelp.DTO.MlDTO;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class RollPredictionResponseDTO {
    // Идентификаторы
    private String requestId;
    private String rollId;
    private String rollName;

    // Основные данные
    private List<String> ingredients;
    private Double predictedSales;
    private Double adjustedSales;
    private Double confidenceScore;

    // Экономика
    private Double estimatedCost;
    private Double estimatedProfit;
    private Double roi;

    // Анализ
    private List<IngredientPairDTO> pairAnalysis;
    private List<String> topSynergies;
    private List<String> weakPairs;

    // Метаданные
    private LocalDateTime predictionTime;
    private String modelVersion;

    // Поле для ошибок (ДОБАВЬТЕ ЭТО!)
    private String errorMessage;

    // Helper methods
    public boolean isSuccessful() {
        return errorMessage == null || errorMessage.isEmpty();
    }

    // Создать успешный ответ
    public static RollPredictionResponseDTO success(List<String> ingredients,
                                                    Double predictedSales) {
        return RollPredictionResponseDTO.builder()
                .ingredients(ingredients)
                .predictedSales(predictedSales)
                .confidenceScore(0.85)
                .predictionTime(LocalDateTime.now())
                .modelVersion("1.0")
                .build();
    }

    // Создать ответ с ошибкой
    public static RollPredictionResponseDTO error(String errorMessage) {
        return RollPredictionResponseDTO.builder()
                .errorMessage(errorMessage)
                .predictionTime(LocalDateTime.now())
                .confidenceScore(0.0)
                .build();
    }
}