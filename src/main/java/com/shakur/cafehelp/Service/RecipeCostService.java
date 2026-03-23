package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.ProductDTO;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static jooqdata.tables.Dish.DISH;

@Service
public class RecipeCostService {
    private final DSLContext dsl;
    private final ProductService productService;

    public RecipeCostService(DSLContext dsl, ProductService productService) {
        this.dsl = dsl;
        this.productService = productService;
    }

    public double calculateDishCost(int dishId) {
        Map<Integer, Double> preparationCostCache = new HashMap<>();
        Map<Integer, Double> preparationOutputCache = new HashMap<>();
        Map<Integer, Double> productCostMap = loadEffectiveProductCostMap();
        double cost = calculateOwnerCost(
                RecipeSchema.TECH_DISH_ID.eq(dishId),
                preparationCostCache,
                new LinkedHashSet<>(),
                preparationOutputCache,
                productCostMap
        );
        return roundCurrency(cost);
    }

    public Map<Integer, Double> calculateDishCosts(Collection<Integer> dishIds) {
        Map<Integer, Double> result = new LinkedHashMap<>();
        if (dishIds == null || dishIds.isEmpty()) {
            return result;
        }

        Map<Integer, Double> preparationCostCache = new HashMap<>();
        Map<Integer, Double> preparationOutputCache = new HashMap<>();
        Map<Integer, Double> productCostMap = loadEffectiveProductCostMap();

        for (Integer dishId : dishIds) {
            if (dishId == null || dishId <= 0) continue;
            double cost = calculateOwnerCost(
                    RecipeSchema.TECH_DISH_ID.eq(dishId),
                    preparationCostCache,
                    new LinkedHashSet<>(),
                    preparationOutputCache,
                    productCostMap
            );
            result.put(dishId, roundCurrency(cost));
        }

        return result;
    }

    public double calculatePreparationCost(int preparationId) {
        Map<Integer, Double> preparationCostCache = new HashMap<>();
        Map<Integer, Double> preparationOutputCache = new HashMap<>();
        Map<Integer, Double> productCostMap = loadEffectiveProductCostMap();
        double cost = calculatePreparationCostInternal(
                preparationId,
                preparationCostCache,
                new LinkedHashSet<>(),
                preparationOutputCache,
                productCostMap
        );
        return roundCurrency(cost);
    }

    public Map<Integer, Double> calculatePreparationCosts(Collection<Integer> preparationIds) {
        Map<Integer, Double> result = new LinkedHashMap<>();
        if (preparationIds == null || preparationIds.isEmpty()) {
            return result;
        }

        Map<Integer, Double> preparationCostCache = new HashMap<>();
        Map<Integer, Double> preparationOutputCache = new HashMap<>();
        Map<Integer, Double> productCostMap = loadEffectiveProductCostMap();

        for (Integer preparationId : preparationIds) {
            if (preparationId == null || preparationId <= 0) continue;
            double cost = calculatePreparationCostInternal(
                    preparationId,
                    preparationCostCache,
                    new LinkedHashSet<>(),
                    preparationOutputCache,
                    productCostMap
            );
            result.put(preparationId, roundCurrency(cost));
        }

        return result;
    }

    public void refreshAffectedDishCosts(Integer dishId, Integer preparationId) {
        Set<Integer> affectedDishIds = new LinkedHashSet<>();
        if (dishId != null && dishId > 0) {
            affectedDishIds.add(dishId);
        }
        if (preparationId != null && preparationId > 0) {
            collectAffectedDishesByPreparation(preparationId, affectedDishIds, new LinkedHashSet<>());
        }

        for (Integer affectedDishId : affectedDishIds) {
            double cost = calculateDishCost(affectedDishId);
            dsl.update(DISH)
                    .set(DISH.FIRSTCOST, cost)
                    .where(DISH.DISHID.eq(affectedDishId))
                    .execute();
        }
    }

    private void collectAffectedDishesByPreparation(int preparationId, Set<Integer> dishIds, Set<Integer> visitedPreparations) {
        if (!visitedPreparations.add(preparationId)) {
            return;
        }

        var parents = dsl.select(RecipeSchema.TECH_DISH_ID, RecipeSchema.TECH_PREPARATION_ID)
                .from(RecipeSchema.TECHPRODUCT)
                .where(RecipeSchema.TECH_INGREDIENT_PREPARATION_ID.eq(preparationId))
                .fetch();

        for (Record parent : parents) {
            Integer dishId = parent.get(RecipeSchema.TECH_DISH_ID);
            Integer parentPreparationId = parent.get(RecipeSchema.TECH_PREPARATION_ID);
            if (dishId != null && dishId > 0) {
                dishIds.add(dishId);
            }
            if (parentPreparationId != null && parentPreparationId > 0) {
                collectAffectedDishesByPreparation(parentPreparationId, dishIds, visitedPreparations);
            }
        }
    }

    private double calculatePreparationCostInternal(
            int preparationId,
            Map<Integer, Double> preparationCostCache,
            Set<Integer> currentPath,
            Map<Integer, Double> preparationOutputCache,
            Map<Integer, Double> productCostMap
    ) {
        if (preparationCostCache.containsKey(preparationId)) {
            return preparationCostCache.get(preparationId);
        }

        if (!currentPath.add(preparationId)) {
            return 0.0;
        }

        double cost = calculateOwnerCost(
                RecipeSchema.TECH_PREPARATION_ID.eq(preparationId),
                preparationCostCache,
                currentPath,
                preparationOutputCache,
                productCostMap
        );

        currentPath.remove(preparationId);
        preparationCostCache.put(preparationId, cost);
        return cost;
    }

    private double calculateOwnerCost(
            Condition ownerCondition,
            Map<Integer, Double> preparationCostCache,
            Set<Integer> currentPath,
            Map<Integer, Double> preparationOutputCache,
            Map<Integer, Double> productCostMap
    ) {
        double total = 0.0;

        var rows = dsl.select(
                        RecipeSchema.TECH_PRODUCT_ID,
                        RecipeSchema.TECH_INGREDIENT_PREPARATION_ID,
                        RecipeSchema.TECH_WEIGHT,
                        RecipeSchema.TECH_WASTE
                )
                .from(RecipeSchema.TECHPRODUCT)
                .where(ownerCondition)
                .fetch();

        for (Record row : rows) {
            double requiredQuantity = adjustQuantity(row.get(RecipeSchema.TECH_WEIGHT), row.get(RecipeSchema.TECH_WASTE));
            if (requiredQuantity <= 0) continue;

            Integer productId = row.get(RecipeSchema.TECH_PRODUCT_ID);
            Integer ingredientPreparationId = row.get(RecipeSchema.TECH_INGREDIENT_PREPARATION_ID);

            if (productId != null) {
                total += requiredQuantity * productCostMap.getOrDefault(productId, 0.0);
                continue;
            }

            if (ingredientPreparationId != null) {
                double nestedOutput = getPreparationOutputWeight(ingredientPreparationId, preparationOutputCache);
                if (nestedOutput <= 0) continue;

                double nestedCost = calculatePreparationCostInternal(
                        ingredientPreparationId,
                        preparationCostCache,
                        currentPath,
                        preparationOutputCache,
                        productCostMap
                );
                total += requiredQuantity * (nestedCost / nestedOutput);
            }
        }

        return total;
    }

    private double getPreparationOutputWeight(int preparationId, Map<Integer, Double> cache) {
        if (cache.containsKey(preparationId)) {
            return cache.get(preparationId);
        }

        Double outputWeight = dsl.select(RecipeSchema.PREPARATION_OUTPUT_WEIGHT)
                .from(RecipeSchema.PREPARATION)
                .where(RecipeSchema.PREPARATION_ID.eq(preparationId))
                .fetchOne(RecipeSchema.PREPARATION_OUTPUT_WEIGHT);

        double normalized = outputWeight != null ? outputWeight : 0.0;
        cache.put(preparationId, normalized);
        return normalized;
    }

    private Map<Integer, Double> loadEffectiveProductCostMap() {
        Map<Integer, Double> result = new HashMap<>();

        for (ProductDTO product : productService.getProducts()) {
            if (product == null || product.getProductId() <= 0) continue;

            BigDecimal averageStockPrice = product.getAverageStockPrice();
            BigDecimal directPrice = product.getProductPrice() != null ? product.getProductPrice() : BigDecimal.ZERO;
            BigDecimal factor = product.getUnitFactor() != null && product.getUnitFactor().compareTo(BigDecimal.ZERO) > 0
                    ? product.getUnitFactor()
                    : BigDecimal.ONE;

            BigDecimal effectiveBasePrice = averageStockPrice != null
                    ? averageStockPrice
                    : directPrice.divide(factor, 6, RoundingMode.HALF_UP);

            result.put(product.getProductId(), effectiveBasePrice.doubleValue());
        }

        return result;
    }

    private double adjustQuantity(Double quantity, Double waste) {
        double normalizedQuantity = quantity != null ? quantity : 0.0;
        if (normalizedQuantity <= 0) return 0.0;

        double wastePct = waste != null ? waste : 0.0;
        if (wastePct < 0) wastePct = 0;
        if (wastePct > 100) wastePct = 100;

        return normalizedQuantity * (1 + (wastePct / 100.0));
    }

    private double roundCurrency(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
