// MlTrainingService.java
package com.shakur.cafehelp.Service.MlServices;

import com.shakur.cafehelp.DTO.MlDTO.RollMenuItemDTO;
import com.shakur.cafehelp.DTO.MlDTO.SalesRecordDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class MlTrainingService {

    private final SalesService salesService;
    private final MenuService menuService;
    private final InventoryService inventoryService;
    private final RestTemplate restTemplate;

    @Value("${ml.python.service.url:http://localhost:8000}")
    private String pythonMlServiceUrl;

    /**
     * Подготовить данные для обучения ML модели
     */
    public List<Map<String, Object>> prepareTrainingData(
            List<SalesRecordDTO> salesRecords,
            List<RollMenuItemDTO> menuItems) {

        List<Map<String, Object>> trainingRecords = new ArrayList<>();

        // 1. Создаем записи на основе продаж
        for (SalesRecordDTO sale : salesRecords) {
            Map<String, Object> record = new HashMap<>();
            record.put("rollName", sale.getRollName());
            record.put("ingredients", parseIngredients(String.valueOf(sale.getIngredients())));
            record.put("sales", sale.getQuantity() * sale.getPricePerUnit());
            record.put("date", sale.getSaleDate().toString());
            record.put("dayOfWeek", sale.getSaleDate().getDayOfWeek().getValue());
            record.put("month", sale.getSaleDate().getMonthValue());

            trainingRecords.add(record);
        }

        // 2. Добавляем данные из меню (даже если продаж не было)
        for (RollMenuItemDTO menuItem : menuItems) {
            // Проверяем, нет ли уже такой записи
            boolean exists = trainingRecords.stream()
                    .anyMatch(r -> r.get("rollName").equals(menuItem.getName()));

            if (!exists) {
                Map<String, Object> record = new HashMap<>();
                record.put("rollName", menuItem.getName());
                record.put("ingredients", parseIngredients(String.valueOf(menuItem.getIngredients())));
                record.put("sales", 0.0); // Нулевые продажи для обучения
                record.put("isActive", menuItem.getIsAvailable());

                trainingRecords.add(record);
            }
        }

        log.info("Подготовлено {} записей для обучения ML", trainingRecords.size());
        return trainingRecords;
    }

    /**
     * Отправить данные в Python ML сервис для обучения
     */
    public Map<String, Object> sendToPythonML(List<Map<String, Object>> trainingRecords) {
        try {
            String url = pythonMlServiceUrl + "/api/ml/train";

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("records", trainingRecords);
            requestBody.put("training_date", LocalDate.now().toString());
            requestBody.put("model_type", "RandomForestRegressor");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Source", "Java-Backend");

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    url, request, Map.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> result = response.getBody();
                log.info("ML модель успешно обучена: {}", result);
                return result;
            } else {
                log.error("Ошибка обучения ML модели: {}", response.getStatusCode());
                throw new RuntimeException("Failed to train ML model");
            }

        } catch (Exception e) {
            log.error("Ошибка отправки данных в ML сервис: {}", e.getMessage(), e);
            throw new RuntimeException("ML service error: " + e.getMessage(), e);
        }
    }

    /**
     * Синхронизировать последние данные и обновить модель
     */
    public Map<String, Object> syncAndUpdateModel(int daysBack) {
        try {
            // 1. Получаем свежие данные
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(daysBack);

            List<SalesRecordDTO> recentSales = salesService.getSalesForML(startDate, endDate);
            List<RollMenuItemDTO> currentMenu = menuService.getAllMenuItems();

            // 2. Подготавливаем данные
            List<Map<String, Object>> trainingData = prepareTrainingData(recentSales, currentMenu);

            // 3. Отправляем на обучение
            Map<String, Object> trainingResult = sendToPythonML(trainingData);

            // 4. Получаем информацию об обновленной модели
            String infoUrl = pythonMlServiceUrl + "/api/ml/info";
            ResponseEntity<Map> infoResponse = restTemplate.getForEntity(infoUrl, Map.class);

            Map<String, Object> result = new HashMap<>();
            result.put("trainingResult", trainingResult);
            result.put("modelInfo", infoResponse.getBody());
            result.put("newRecords", trainingData.size());
            result.put("dateRange", startDate + " - " + endDate);

            return result;

        } catch (Exception e) {
            log.error("Ошибка синхронизации данных: {}", e.getMessage(), e);
            throw new RuntimeException("Sync failed: " + e.getMessage(), e);
        }
    }

    /**
     * Проверить статус ML сервиса
     */
    public Map<String, Object> checkMlServiceHealth() {
        try {
            String healthUrl = pythonMlServiceUrl + "/health";
            ResponseEntity<Map> response = restTemplate.getForEntity(healthUrl, Map.class);

            Map<String, Object> health = new HashMap<>();
            health.put("status", response.getStatusCode().is2xxSuccessful() ? "healthy" : "unhealthy");
            health.put("response", response.getBody());
            health.put("pythonServiceUrl", pythonMlServiceUrl);

            return health;

        } catch (Exception e) {
            Map<String, Object> health = new HashMap<>();
            health.put("status", "unreachable");
            health.put("error", e.getMessage());
            health.put("pythonServiceUrl", pythonMlServiceUrl);
            return health;
        }
    }

    /**
     * Получить популярные комбинации ингредиентов из Python ML
     */
    public List<String> getPopularIngredientCombinations(int limit) {
        try {
            String url = pythonMlServiceUrl + "/api/ml/insights/popular-pairs?limit=" + limit;
            ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);

            return response.getBody();

        } catch (Exception e) {
            log.warn("Не удалось получить популярные комбинации: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Парсинг строки ингредиентов в список
     */
    private List<String> parseIngredients(String ingredientsString) {
        if (ingredientsString == null || ingredientsString.trim().isEmpty()) {
            return Collections.emptyList();
        }

        // Разделяем по запятой, точке с запятой или вертикальной черте
        return Arrays.asList(ingredientsString.split("[,;|]"))
                .stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .toList();
    }

    /**
     * Получить текущие ингредиенты для отправки в ML
     */
    public List<Map<String, Object>> getCurrentIngredientsForML() {
        return inventoryService.getAllIngredients().stream()
                .map(ingredient -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("name", ingredient.getName());
                    map.put("category", ingredient.getCategory());
                    map.put("currentStock", ingredient.getCurrentStock());
                    map.put("unit", ingredient.getUnit());
                    return map;
                })
                .toList();
    }

    /**
     * Полная переобучение модели на всех данных
     */
    public Map<String, Object> retrainFullModel() {
        try {
            // Получаем все исторические данные - ПЕРЕДАЙ ДАТЫ!
            List<SalesRecordDTO> allSales = salesService.getSalesForML(
                    LocalDate.of(2000, 1, 1),  // ← НАЧАЛЬНАЯ ДАТА
                    LocalDate.now()             // ← КОНЕЧНАЯ ДАТА
            );

            List<RollMenuItemDTO> allMenuItems = menuService.getAllMenuItems();

            List<Map<String, Object>> allTrainingData = prepareTrainingData(allSales, allMenuItems);

            log.info("Полная переобучение модели на {} записях", allTrainingData.size());

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("records", allTrainingData);
            requestBody.put("retrain_full", true);
            requestBody.put("data_range", "all_history");

            String url = pythonMlServiceUrl + "/api/ml/train";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Полное переобучение завершено успешно");
                return response.getBody();
            } else {
                throw new RuntimeException("Full retraining failed");
            }

        } catch (Exception e) {
            log.error("Ошибка полного переобучения: {}", e.getMessage(), e);
            throw new RuntimeException("Full retrain error: " + e.getMessage(), e);
        }
    }
}