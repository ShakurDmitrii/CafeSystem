package com.shakur.cafehelp.Service;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class RecipeRequirementService {
    private final DSLContext dsl;

    public RecipeRequirementService(DSLContext dsl) {
        this.dsl = dsl;
    }

    public RequirementSet buildForDish(int dishId, double multiplier) {
        return build(RecipeSchema.TECH_DISH_ID.eq(dishId), multiplier);
    }

    public RequirementSet buildForPreparation(int preparationId, double multiplier) {
        return build(RecipeSchema.TECH_PREPARATION_ID.eq(preparationId), multiplier);
    }

    private RequirementSet build(org.jooq.Condition ownerCondition, double multiplier) {
        Map<Integer, Double> products = new HashMap<>();
        Map<Integer, Double> preparations = new HashMap<>();
        if (multiplier <= 0) return new RequirementSet(products, preparations);

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
            Double weight = row.get(RecipeSchema.TECH_WEIGHT);
            if (weight == null || weight <= 0) continue;

            double wastePct = row.get(RecipeSchema.TECH_WASTE) != null ? row.get(RecipeSchema.TECH_WASTE) : 0.0;
            if (wastePct < 0) wastePct = 0;
            if (wastePct > 100) wastePct = 100;

            double requiredQty = weight * multiplier * (1 + (wastePct / 100.0));
            if (requiredQty <= 0) continue;

            Integer productId = row.get(RecipeSchema.TECH_PRODUCT_ID);
            Integer preparationId = row.get(RecipeSchema.TECH_INGREDIENT_PREPARATION_ID);
            if (productId != null) {
                products.merge(productId, requiredQty, Double::sum);
            } else if (preparationId != null) {
                preparations.merge(preparationId, requiredQty, Double::sum);
            }
        }

        return new RequirementSet(products, preparations);
    }

    public record RequirementSet(
            Map<Integer, Double> productRequirements,
            Map<Integer, Double> preparationRequirements
    ) {
    }
}
