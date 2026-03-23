package com.shakur.cafehelp.Service.MlServices;

import com.shakur.cafehelp.DTO.MlDTO.*;
import com.shakur.cafehelp.DTO.DishDTO;
import com.shakur.cafehelp.DTO.ProductDTO;
import com.shakur.cafehelp.DTO.TechProductDTO;
import com.shakur.cafehelp.Service.DishService;
import com.shakur.cafehelp.Service.ProductService;
import com.shakur.cafehelp.Service.TechProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class PredictionService {
    private static final double DUPLICATE_SIMILARITY_THRESHOLD = 0.90;

    private final RestTemplate restTemplate;
    private final SalesService salesService;
    private final MenuService menuService;
    private final InventoryService inventoryService;
    private final DishService dishService;
    private final ProductService productService;
    private final TechProductService techProductService;
    private final DSLContext dsl;

    // Prefer explicit ml.service.url, but fall back to training/python settings used elsewhere.
    @Value("${ml.service.url:${ml.python.service.url:${python.api.url:http://localhost:8000}}}")
    private String mlServiceUrl;

    /**
     * Отправить запрос на предсказание в Python ML сервис
     */
    public RollPredictionResponseDTO predictSales(RollPredictionRequestDTO request) {
        try {
            String url = mlServiceUrl + "/api/ml/predict";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<RollPredictionRequestDTO> entity = new HttpEntity<>(request, headers);

            ResponseEntity<RollPredictionResponseDTO> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, RollPredictionResponseDTO.class
            );

            log.info("Prediction request sent to ML service: {}", request.getIngredients());
            return response.getBody();

        } catch (Exception e) {
            log.error("Failed to get prediction from ML service: {}", e.getMessage());
            throw new RuntimeException("ML service unavailable: " + e.getMessage());
        }
    }

    /**
     * Пакетное предсказание
     */
    public BatchPredictionResponseDTO batchPredict(BatchPredictionRequestDTO request) {
        try {
            String url = mlServiceUrl + "/api/ml/predict/batch";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<BatchPredictionRequestDTO> entity = new HttpEntity<>(request, headers);

            ResponseEntity<BatchPredictionResponseDTO> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, BatchPredictionResponseDTO.class
            );

            log.info("Batch prediction for {} rolls sent to ML service",
                    request.getRolls().size());
            return response.getBody();

        } catch (Exception e) {
            log.error("Failed batch prediction: {}", e.getMessage());
            throw new RuntimeException("ML service unavailable: " + e.getMessage());
        }
    }

    /**
     * Оптимизация состава роллов
     */
    public OptimizationResponseDTO optimizeRolls(OptimizationRequestDTO request) {
        try {
            String url = mlServiceUrl + "/api/ml/optimize";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Python optimizer needs ingredient pool/menu/sales to compute costs and novelty.
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(90);
            var sales = salesService.getSalesForMLOptimized(startDate, endDate);
            var menu = menuService.getAllMenuItems();
            var ingredients = inventoryService.getAllIngredients();

            Map<String, Object> constraints = new HashMap<>();
            constraints.put("minIngredients", request.getMinIngredients());
            constraints.put("maxIngredients", request.getMaxIngredients());
            constraints.put("maxCost", request.getMaxCost());
            constraints.put("minProfitMargin", request.getMinProfitMargin());
            constraints.put("mustInclude", request.getMustInclude());
            constraints.put("excludedIngredients", request.getExcludedIngredients());
            constraints.put("populationSize", request.getPopulationSize());
            constraints.put("generations", request.getGenerations());
            constraints.put("numResults", request.getNumResults());

            Map<String, Object> body = new HashMap<>();
            body.put("constraints", constraints);
            body.put("salesRecords", sales);
            body.put("menuItems", menu);
            body.put("ingredients", ingredients);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<OptimizationResponseDTO> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, OptimizationResponseDTO.class
            );

            log.info("Optimization request sent to ML service: {}", request.getRequestId());
            return response.getBody();

        } catch (Exception e) {
            log.error("Failed optimization request: {}", e.getMessage());
            throw new RuntimeException("ML service unavailable: " + e.getMessage());
        }
    }

    /**
     * Проверить доступность Python ML сервиса
     */
    public boolean isMlServiceAvailable() {
        try {
            String url = mlServiceUrl + "/health";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("ML service is not available: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Локальное предсказание (fallback если Python сервис недоступен)
     */
    public RollPredictionResponseDTO predictLocal(RollPredictionRequestDTO request) {
        log.warn("Using local fallback prediction (ML service unavailable)");

        // Простая эвристика если Python сервис недоступен
        double baseSales = 10.0; // Базовые продажи
        double ingredientBonus = request.getIngredients().size() * 2.0;

        return RollPredictionResponseDTO.builder()
                .predictedSales(baseSales + ingredientBonus)
                .confidenceScore(0.5)
                .ingredients(request.getIngredients())
                .build();
    }

    public Map<String, Object> generateNewDish(GenerateDishRequestDTO request) {
        try {
            int days = request.getDays() != null ? request.getDays() : 90;
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(days);

            var sales = salesService.getSalesForMLOptimized(startDate, endDate);
            var menu = menuService.getAllMenuItems();
            var ingredients = inventoryService.getAllIngredients();

            Map<String, Object> constraints = new HashMap<>();
            constraints.put("minIngredients", request.getMinIngredients());
            constraints.put("maxIngredients", request.getMaxIngredients());
            constraints.put("populationSize", request.getPopulationSize());
            constraints.put("generations", request.getGenerations());
            constraints.put("markup", request.getMarkup());
            constraints.put("mustInclude", request.getMustInclude());
            constraints.put("excludedIngredients", request.getExcludedIngredients());

            Map<String, Object> body = new HashMap<>();
            body.put("salesRecords", sales);
            body.put("menuItems", menu);
            body.put("ingredients", ingredients);
            body.put("constraints", constraints);

            String url = mlServiceUrl + "/api/ml/generate-dish";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            Map<String, Object> result = response.getBody() != null ? response.getBody() : new HashMap<>();
            result.putIfAbsent("status", "completed");
            result.put("sourceSalesRecords", sales.size());
            result.put("sourceMenuItems", menu.size());
            result.put("sourceIngredients", ingredients.size());
            return result;

        } catch (Exception e) {
            log.error("Failed to generate new dish: {}", e.getMessage(), e);
            throw new RuntimeException("ML dish generation failed: " + e.getMessage());
        }
    }

    @Transactional
    public SaveGeneratedDishResponseDTO saveGeneratedDish(SaveGeneratedDishRequestDTO request) {
        if (request == null || request.getDishName() == null || request.getDishName().isBlank()) {
            throw new IllegalArgumentException("dishName обязателен");
        }

        List<SaveGeneratedDishRequestDTO.TechCardItemDTO> rows = request.getTechCard();
        if (rows == null || rows.isEmpty()) {
            throw new IllegalArgumentException("techCard обязателен");
        }

        Set<String> requestedIngredients = new HashSet<>();
        for (SaveGeneratedDishRequestDTO.TechCardItemDTO row : rows) {
            if (row == null || row.getIngredientName() == null) continue;
            String normalized = row.getIngredientName().trim().toLowerCase(Locale.ROOT);
            if (!normalized.isBlank()) requestedIngredients.add(normalized);
        }

        DuplicateCheckResult duplicate = findDuplicateDishByIngredients(requestedIngredients);
        if (duplicate != null) {
            throw new IllegalArgumentException(
                    "Состав слишком похож на существующее блюдо: ID=" + duplicate.dishId()
                            + ", name=\"" + duplicate.dishName() + "\""
                            + ", similarity=" + String.format(Locale.ROOT, "%.3f", duplicate.similarity())
            );
        }

        Map<String, ProductDTO> productByName = new HashMap<>();
        for (ProductDTO product : productService.getProducts()) {
            if (product.getProductName() == null) continue;
            productByName.put(product.getProductName().trim().toLowerCase(Locale.ROOT), product);
        }

        DishDTO dish = new DishDTO();
        dish.setDishName(request.getDishName().trim());
        dish.setCategory(request.getCategory() != null && !request.getCategory().isBlank()
                ? request.getCategory()
                : "AI");
        dish.setPrice(request.getRecommendedPrice() != null ? request.getRecommendedPrice() : 0.0);
        dish.setFirstCost(request.getEstimatedCost() != null ? request.getEstimatedCost() : 0.0);

        double weight = request.getWeightGrams() != null ? request.getWeightGrams() : 0.0;
        if (weight <= 0) {
            weight = rows.stream()
                    .map(SaveGeneratedDishRequestDTO.TechCardItemDTO::getQuantityGrams)
                    .filter(v -> v != null && v > 0)
                    .mapToDouble(Double::doubleValue)
                    .sum();
        }
        dish.setWeight(weight > 0 ? weight : 140.0);

        DishDTO createdDish = dishService.createDish(dish);

        List<String> missingIngredients = new ArrayList<>();
        int createdRows = 0;

        for (SaveGeneratedDishRequestDTO.TechCardItemDTO row : rows) {
            if (row == null || row.getIngredientName() == null || row.getIngredientName().isBlank()) continue;

            String key = row.getIngredientName().trim().toLowerCase(Locale.ROOT);
            ProductDTO product = productByName.get(key);
            if (product == null) {
                missingIngredients.add(row.getIngredientName());
                continue;
            }

            TechProductDTO techProductDTO = new TechProductDTO();
            techProductDTO.setDishId(createdDish.getDishId());
            techProductDTO.setProductId(product.getProductId());
            techProductDTO.setWeight(row.getQuantityGrams() != null ? row.getQuantityGrams() : 0.0);
            techProductDTO.setWaste(0.0);
            techProductService.create(techProductDTO);
            createdRows++;
        }

        return SaveGeneratedDishResponseDTO.builder()
                .dishId(createdDish.getDishId())
                .dishName(createdDish.getDishName())
                .techCardRowsCreated(createdRows)
                .missingIngredients(missingIngredients)
                .status(createdRows > 0 ? "saved" : "saved_without_tech_rows")
                .build();
    }

    private DuplicateCheckResult findDuplicateDishByIngredients(Set<String> requestedIngredients) {
        if (requestedIngredients == null || requestedIngredients.isEmpty()) {
            return null;
        }
        DuplicateCheckResult best = null;
        int requestedCount = requestedIngredients.size();

        for (DishDTO dish : dishService.getAll()) {
            Set<String> dishIngredients = menuService.getDishIngredients(dish.getDishId())
                    .stream()
                    .map(name -> name == null ? "" : name.trim().toLowerCase(Locale.ROOT))
                    .filter(name -> !name.isBlank())
                    .collect(java.util.stream.Collectors.toSet());

            int dishCount = dishIngredients.size();
            if (dishCount == 0) continue;

            int intersection = 0;
            for (String ingredient : dishIngredients) {
                if (requestedIngredients.contains(ingredient)) {
                    intersection++;
                }
            }
            if (intersection == 0) continue;

            int union = requestedCount + dishCount - intersection;
            if (union <= 0) continue;

            double similarity = (double) intersection / union;
            if (similarity >= DUPLICATE_SIMILARITY_THRESHOLD) {
                DuplicateCheckResult candidate = new DuplicateCheckResult(
                        dish.getDishId(),
                        dish.getDishName(),
                        similarity
                );
                if (best == null || candidate.similarity() > best.similarity()) {
                    best = candidate;
                }
            }
        }

        return best;
    }

    private record DuplicateCheckResult(Integer dishId, String dishName, double similarity) {
    }
}
