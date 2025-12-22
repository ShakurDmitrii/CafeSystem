package com.shakur.cafehelp.DTO.MlDTO;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RollMenuItemDTO {
    private String id;
    private String name;
    private String description;
    private List<String> ingredients;
    private String category; // "classic", "premium", "vegetarian", "spicy", "special"
    private Double price;
    private Double cost; // себестоимость
    private Integer preparationTime; // в минутах
    private Boolean isAvailable;
    private Double popularityScore; // 0-1
    private Integer calories;
    private List<String> allergens; // ["fish", "soy", "gluten", ...]
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Аналитика
    private Integer totalSold;
    private Double totalRevenue;
    private Double profitMargin;
    private Double roi; // Return on Investment
}
