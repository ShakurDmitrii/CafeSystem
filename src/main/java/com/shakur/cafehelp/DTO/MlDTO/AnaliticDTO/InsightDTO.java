package com.shakur.cafehelp.DTO.MlDTO.AnaliticDTO;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class InsightDTO {

    // Уникальный идентификатор инсайта
    @JsonProperty("id")
    private String id;

    // Тип инсайта: opportunity, warning, insight, recommendation
    @JsonProperty("type")
    private String type;

    // Заголовок (кратко)
    @JsonProperty("title")
    private String title;

    // Подробное описание
    @JsonProperty("description")
    private String description;

    // Уверенность модели (0.0 - 1.0)
    @JsonProperty("confidence")
    private Double confidence;

    // Рекомендуемое действие
    @JsonProperty("action")
    private String action;

    // Какие роллы/ингредиенты затрагивает
    @JsonProperty("affected_items")
    private List<String> affectedItems;

    // Влияние на метрики (в %)
    @JsonProperty("impact")
    private Double impact;

    // Дата генерации инсайта
    @JsonProperty("generated_at")
    private LocalDateTime generatedAt;

    // Срок действия (до какой даты актуален)
    @JsonProperty("valid_until")
    private LocalDateTime validUntil;

    // Статус: new, read, applied, dismissed
    @JsonProperty("status")
    private String status;

    // Приоритет: low, medium, high, critical
    @JsonProperty("priority")
    private String priority;

    // Метод для получения иконки (для фронта)
    public String getIcon() {
        return switch (type) {
            case "opportunity" -> "🚀";
            case "warning" -> "⚠️";
            case "recommendation" -> "🎯";
            default -> "💡";
        };
    }

    // Метод для получения цвета (для фронта)
    public String getColor() {
        return switch (type) {
            case "opportunity" -> "#4CAF50"; // зеленый
            case "warning" -> "#F44336";     // красный
            case "recommendation" -> "#2196F3"; // синий
            default -> "#FF9800";            // оранжевый
        };
    }
}