package com.shakur.cafehelp.DTO.MlDTO;

import lombok.*;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class IngredientDTO {
    private String id;
    private String name;
    private String displayName;
    private String category; // "fish", "seafood", "vegetable", "fruit", "sauce", "rice", "wrap", "topping"
    private String unit; // "kg", "piece", "liter", "package"
    private Double costPerUnit;
    private Double currentStock;
    private Double minStockLevel;
    private Double maxStockLevel;
    private String supplierId;
    private String supplierName;
    private LocalDate lastDeliveryDate;
    private LocalDate nextDeliveryDate;
    private Integer shelfLifeDays;
    private Boolean isActive;
    private Boolean isSeasonal;
    private List<Integer> seasonalMonths; // [1,2,3] для зимних
    private List<String> allergens;
    private String storageConditions; // "frozen", "refrigerated", "room_temp"
    private List<String> allergies;

    // Для ML
    private Double popularityScore; // частота использования в продажах
    private List<String> bestPairings; // лучшие сочетания
    private List<String> worstPairings; // худшие сочетания
}
