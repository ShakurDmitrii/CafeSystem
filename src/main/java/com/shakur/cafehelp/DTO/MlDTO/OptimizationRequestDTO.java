package com.shakur.cafehelp.DTO.MlDTO;


import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OptimizationRequestDTO {
    private String requestId;
    private String userId;  // кто запросил

    // Основные ограничения
    private Integer minIngredients = 3;
    private Integer maxIngredients = 8;
    private Double maxCost;          // максимальная себестоимость
    private Double minProfitMargin;  // минимальная маржа прибыли (0-1)

    // Ограничения по ингредиентам
    private List<String> requiredCategories;  // обязательные категории
    private List<String> excludedCategories;  // исключенные категории
    private List<String> mustInclude;         // обязательные ингредиенты
    private List<String> excludedIngredients; // исключенные ингредиенты

    // Контекст для улучшения рекомендаций
    private String season;           // "winter", "spring", "summer", "autumn"
    private String locationId;       // для локационных корректировок
    private String targetCustomerSegment; // "premium", "budget", "family", "student"
    private String promotionType;    // "happy_hour", "weekend", "holiday"
    private Integer preparationTimeLimit; // лимит времени приготовления (минуты)

    // Параметры генетического алгоритма
    private Integer populationSize = 100;
    private Integer generations = 50;
    private Integer numResults = 5;  // сколько роллов сгенерировать

    // Метаданные
    private LocalDateTime createdAt;
    private String callbackUrl;      // для асинхронного ответа
    private Boolean async = false;   // асинхронный режим

    // Валидация
    public boolean isValid() {
        if (minIngredients < 1 || maxIngredients > 15) return false;
        if (minIngredients > maxIngredients) return false;
        if (populationSize < 10 || populationSize > 1000) return false;
        if (generations < 5 || generations > 200) return false;
        if (numResults < 1 || numResults > 20) return false;
        return true;
    }
}