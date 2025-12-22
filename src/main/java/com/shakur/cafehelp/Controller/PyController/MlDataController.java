package com.shakur.cafehelp.Controller.PyController;

import com.shakur.cafehelp.DTO.MlDTO.*;
import com.shakur.cafehelp.Service.MlServices.SalesService;
import com.shakur.cafehelp.Service.MlServices.MenuService;
import com.shakur.cafehelp.Service.MlServices.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/ml/data")
@RequiredArgsConstructor
public class MlDataController {

    private final SalesService salesService;
    private final MenuService menuService;
    private final InventoryService inventoryService;

    /**
     * 1. Получить данные о продажах для обучения модели (Python)
     */
    @GetMapping("/sales")
    public List<SalesRecordDTO> getSalesData(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,

            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,

            @RequestParam(defaultValue = "1000") int limit) {

        // Получаем данные и ограничиваем лимитом
        var sales = salesService.getSalesForML(startDate, endDate);
        return sales.stream().limit(limit).collect(java.util.stream.Collectors.toList());
    }

    /**
     * 2. Получить текущее меню роллов (Python)
     */
    @GetMapping("/menu")
    public List<RollMenuItemDTO> getMenu() {
        return menuService.getAllMenuItems();
    }

    /**
     * 3. Получить список ингредиентов (Python)
     */
    @GetMapping("/ingredients")
    public List<IngredientDTO> getIngredients() {
        return inventoryService.getAllIngredients();
    }

    /**
     * 4. Получить популярные ингредиенты
     */
    @GetMapping("/ingredients/popular")
    public List<String> getPopularIngredients(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "20") int limit) {

        return salesService.getPopularIngredients(days, limit);
    }

    /**
     * 5. Проверка здоровья сервиса
     */
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("ML Data API is running");
    }
}