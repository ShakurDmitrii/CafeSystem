package com.shakur.cafehelp.Controller.PyController;

import com.shakur.cafehelp.DTO.MlDTO.RollMenuItemDTO;
import com.shakur.cafehelp.DTO.MlDTO.SalesRecordDTO;
import com.shakur.cafehelp.Service.MlServices.MenuService;
import com.shakur.cafehelp.Service.MlServices.MlTrainingService;
import com.shakur.cafehelp.Service.MlServices.SalesService;
import com.shakur.cafehelp.exception.PythonServiceException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// Java контроллер
@RestController

@RequiredArgsConstructor
public class MlTrainingController {

    private final SalesService salesService;
    private final MenuService menuService;
    private final MlTrainingService mlTrainingService;

    @PostMapping("api/ml/train-with-recent-data")
    public ResponseEntity<Map<String, Object>> trainWithRecentData(
            @RequestBody TrainRequest request) {

        try {
            // 1. Собираем продажи: по умолчанию за всю историю, days ограничивает последние N дней
            LocalDate endDate = LocalDate.now();
            Integer days = request.getDays();
            LocalDate startDate = days != null && days > 0 ? endDate.minusDays(days) : null;

            List<SalesRecordDTO> sales = salesService.getSalesForML(startDate, endDate);
            List<RollMenuItemDTO> menu = menuService.getAllMenuItems();

            // 2. Формируем суточные записи по блюдам
            List<Map<String, Object>> allRecords = mlTrainingService.prepareTrainingData(sales, menu);
            List<Map<String, Object>> trainingRecords = mlTrainingService.keepMostRecent(allRecords);

            // 3. Отправляем в Python ML сервис
            Map<String, Object> result = mlTrainingService.sendToPythonML(trainingRecords);

            Map<String, Object> response = new java.util.LinkedHashMap<>();
            response.put("recordsCount", trainingRecords.size());
            response.put("newIngredientsCount", countUniqueIngredients(trainingRecords));
            response.put("allHistory", startDate == null);
            response.put("trimmedToRecent", trainingRecords.size() < allRecords.size());
            response.put("trainingResult", result);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (PythonServiceException e) {
            throw e;
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Не удалось подготовить данные для обучения"));
        }
    }

    private int countUniqueIngredients(List<Map<String, Object>> trainingRecords) {
        Set<String> uniq = new HashSet<>();
        for (Map<String, Object> record : trainingRecords) {
            Object ingredientsObj = record.get("ingredients");
            if (ingredientsObj instanceof List<?> list) {
                for (Object item : list) {
                    if (item != null) uniq.add(item.toString());
                }
            } else if (ingredientsObj instanceof String s) {
                // Fallback if something sent as string
                for (String part : s.split("[,;|]")) {
                    String trimmed = part.trim().toLowerCase();
                    if (!trimmed.isEmpty()) uniq.add(trimmed);
                }
            }
        }
        return uniq.size();
    }

    @Data
    public static class TrainRequest {
        /** Пусто — обучение на всей истории продаж. */
        private Integer days;
        private boolean includeMenu = true;
        private boolean includeSales = true;
        private boolean includeIngredients = true;
    }
}
