package com.shakur.cafehelp.Controller.PyController;

import com.shakur.cafehelp.DTO.MlDTO.RollMenuItemDTO;
import com.shakur.cafehelp.DTO.MlDTO.SalesRecordDTO;
import com.shakur.cafehelp.Service.MlServices.MenuService;
import com.shakur.cafehelp.Service.MlServices.MlTrainingService;
import com.shakur.cafehelp.Service.MlServices.SalesService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// Java контроллер
@RestController
@RequestMapping("/api/ml")
@RequiredArgsConstructor
public class MlTrainingController {

    private final SalesService salesService;
    private final MenuService menuService;
    private final MlTrainingService mlTrainingService;

    @PostMapping("/train-with-recent-data")
    public ResponseEntity<Map<String, Object>> trainWithRecentData(
            @RequestBody TrainRequest request) {

        try {
            // 1. Собираем данные за указанный период
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(request.getDays());

            List<SalesRecordDTO> sales = salesService.getSalesForML(startDate, endDate);
            List<RollMenuItemDTO> menu = menuService.getAllMenuItems();

            // 2. Формируем данные для обучения
            List<Map<String, Object>> trainingRecords =
                    mlTrainingService.prepareTrainingData(sales, menu);

            // 3. Отправляем в Python ML сервис
            Map<String, Object> result = mlTrainingService.sendToPythonML(trainingRecords);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @Data
    public static class TrainRequest {
        private int days = 90;
        private boolean includeMenu = true;
        private boolean includeSales = true;
        private boolean includeIngredients = true;
    }
}