package com.shakur.cafehelp.Service;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Service
public class RecipeExpansionService {
    private final DSLContext dsl;

    public RecipeExpansionService(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Map<Integer, Double> buildRequirementsForDish(int dishId, int qty) {
        Map<Integer, Double> requiredByProduct = new HashMap<>();
        if (dishId <= 0 || qty <= 0) return requiredByProduct;
        expandDish(dishId, qty, requiredByProduct, new LinkedHashSet<>());
        return requiredByProduct;
    }

    public Map<Integer, Double> buildRequirementsForPreparation(int preparationId, double requiredWeight) {
        Map<Integer, Double> requiredByProduct = new HashMap<>();
        if (preparationId <= 0 || requiredWeight <= 0) return requiredByProduct;
        expandPreparation(preparationId, requiredWeight, requiredByProduct, new LinkedHashSet<>());
        return requiredByProduct;
    }

    private void expandDish(int dishId, double dishQty, Map<Integer, Double> acc, Set<String> stack) {
        expandRows(
                RecipeSchema.TECH_DISH_ID.eq(dishId),
                dishQty,
                acc,
                stack,
                "dish:" + dishId
        );
    }

    private void expandPreparation(int preparationId, double requiredWeight, Map<Integer, Double> acc, Set<String> stack) {
        Double outputWeight = dsl.select(RecipeSchema.PREPARATION_OUTPUT_WEIGHT)
                .from(RecipeSchema.PREPARATION)
                .where(RecipeSchema.PREPARATION_ID.eq(preparationId))
                .fetchOne(RecipeSchema.PREPARATION_OUTPUT_WEIGHT);

        if (outputWeight == null) {
            throw new IllegalStateException("Заготовка не найдена: ID " + preparationId);
        }
        if (outputWeight <= 0) {
            throw new IllegalStateException("У заготовки не задан корректный выход: ID " + preparationId);
        }

        double scale = requiredWeight / outputWeight;
        if (scale <= 0) return;

        expandRows(
                RecipeSchema.TECH_PREPARATION_ID.eq(preparationId),
                scale,
                acc,
                stack,
                "preparation:" + preparationId
        );
    }

    private void expandRows(
            org.jooq.Condition ownerCondition,
            double scale,
            Map<Integer, Double> acc,
            Set<String> stack,
            String ownerKey
    ) {
        if (!stack.add(ownerKey)) {
            throw new IllegalStateException("Обнаружена циклическая ссылка в техкартах: " + ownerKey);
        }

        try {
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
                Integer productId = row.get(RecipeSchema.TECH_PRODUCT_ID);
                Integer ingredientPreparationId = row.get(RecipeSchema.TECH_INGREDIENT_PREPARATION_ID);
                Double weight = row.get(RecipeSchema.TECH_WEIGHT);
                Double waste = row.get(RecipeSchema.TECH_WASTE);
                if (weight == null || weight <= 0) continue;

                double wastePct = waste != null ? waste : 0.0;
                if (wastePct < 0) wastePct = 0;
                if (wastePct > 100) wastePct = 100;

                double requiredQty = weight * scale * (1 + (wastePct / 100.0));
                if (requiredQty <= 0) continue;

                if (productId != null) {
                    acc.merge(productId, requiredQty, Double::sum);
                    continue;
                }

                if (ingredientPreparationId != null) {
                    expandPreparation(ingredientPreparationId, requiredQty, acc, stack);
                }
            }
        } finally {
            stack.remove(ownerKey);
        }
    }
}
