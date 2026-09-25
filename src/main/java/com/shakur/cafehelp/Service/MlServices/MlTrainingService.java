// MlTrainingService.java
package com.shakur.cafehelp.Service.MlServices;

import com.shakur.cafehelp.DTO.MlDTO.RollMenuItemDTO;
import com.shakur.cafehelp.DTO.MlDTO.SalesRecordDTO;
import com.shakur.cafehelp.exception.PythonServiceException;
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

        Map<DailyKey, Map<String, Object>> dailyRecords = new LinkedHashMap<>();
        int skippedWithoutIngredients = 0;

        // 1. Создаем записи на основе продаж
        for (SalesRecordDTO sale : salesRecords) {
            if (sale == null || sale.getSaleDate() == null || sale.getQuantity() == null || sale.getQuantity() < 0) {
                throw new IllegalArgumentException("Продажа для обучения содержит некорректную дату или количество");
            }
            List<String> ingredients = normalizeIngredients(sale.getIngredients());
            if (ingredients.isEmpty()) {
                skippedWithoutIngredients++;
                continue;
            }
            DailyKey key = new DailyKey(sale.getRollId() != null ? sale.getRollId() : sale.getRollName(),
                    sale.getLocationId(), sale.getSaleDate(), ingredients.stream().sorted().toList());
            Map<String, Object> record = dailyRecords.computeIfAbsent(key, ignored -> {
                Map<String, Object> row = new HashMap<>();
                row.put("rollName", sale.getRollName());
                row.put("ingredients", ingredients);
                row.put("date", sale.getSaleDate().toString());
                row.put("sales", 0);
                return row;
            });
            record.put("sales", Math.addExact((Integer) record.get("sales"), sale.getQuantity()));
        }
        List<Map<String, Object>> trainingRecords = dailyRecords.values().stream()
                .sorted(Comparator.comparing(row -> row.get("date").toString())).toList();

        if (skippedWithoutIngredients > 0) {
            log.warn(
                    "Пропущено {} записей продаж без ингредиентов техкарты",
                    skippedWithoutIngredients
            );
        }
        log.info("Подготовлено {} записей для обучения ML", trainingRecords.size());
        return trainingRecords;
    }

    /**
     * Отправить данные в Python ML сервис для обучения
     */
    public Map<String, Object> sendToPythonML(List<Map<String, Object>> trainingRecords) {
        if (trainingRecords == null || trainingRecords.size() < 10) {
            throw new IllegalArgumentException("Для обучения требуется минимум 10 суточных записей по блюдам");
        }
        if (trainingRecords.size() > 100_000) {
            throw new IllegalArgumentException("Для одного запуска допускается не более 100000 записей");
        }
        try {
            String url = pythonMlServiceUrl + "/api/ml/train";

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("records", trainingRecords);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Source", "Java-Backend");

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    url, request, Map.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> result = response.getBody();
                if (result == null || result.isEmpty()) {
                    throw PythonServiceException.invalidResponse(
                            new IllegalStateException("Empty training response")
                    );
                }
                log.info("ML модель успешно обучена; responseFields={}", result.keySet());
                return result;
            } else {
                log.error("Ошибка обучения ML модели: {}", response.getStatusCode());
                throw new RuntimeException("Failed to train ML model");
            }

        } catch (IllegalArgumentException | PythonServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Ошибка отправки данных в ML сервис: {}", e.getClass().getSimpleName());
            throw PythonServiceException.translate(e);
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

        } catch (IllegalArgumentException | PythonServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Ошибка синхронизации данных: {}", e.getClass().getSimpleName());
            throw PythonServiceException.translate(e);
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
            health.put("message", "Python ML service is unavailable");
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

            return sendToPythonML(allTrainingData);

        } catch (IllegalArgumentException | PythonServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Ошибка полного переобучения: {}", e.getClass().getSimpleName());
            throw PythonServiceException.translate(e);
        }
    }

    private List<String> normalizeIngredients(List<String> ingredients) {
        if (ingredients == null) return Collections.emptyList();
        return ingredients.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private record DailyKey(String dish, String location, LocalDate date, List<String> ingredients) {}
}
