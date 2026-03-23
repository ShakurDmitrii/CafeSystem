package com.shakur.cafehelp.Service;

import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;

public final class RecipeSchema {
    private RecipeSchema() {
    }

    public static final Table<?> TECHPRODUCT = DSL.table(DSL.name("sales", "techproduct"));
    public static final Field<Integer> TECHPRODUCT_ID = DSL.field(DSL.name("techproductid"), Integer.class);
    public static final Field<Integer> TECH_DISH_ID = DSL.field(DSL.name("DishId"), Integer.class);
    public static final Field<Integer> TECH_PREPARATION_ID = DSL.field(DSL.name("preparation_id"), Integer.class);
    public static final Field<Integer> TECH_PRODUCT_ID = DSL.field(DSL.name("productid"), Integer.class);
    public static final Field<Integer> TECH_INGREDIENT_PREPARATION_ID = DSL.field(DSL.name("ingredient_preparation_id"), Integer.class);
    public static final Field<Double> TECH_WASTE = DSL.field(DSL.name("waste"), Double.class);
    public static final Field<Double> TECH_WEIGHT = DSL.field(DSL.name("weight"), Double.class);

    public static final Table<?> PREPARATION = DSL.table(DSL.name("sales", "preparation"));
    public static final Field<Integer> PREPARATION_ID = DSL.field(DSL.name("preparationid"), Integer.class);
    public static final Field<String> PREPARATION_NAME = DSL.field(DSL.name("preparationname"), String.class);
    public static final Field<Double> PREPARATION_OUTPUT_WEIGHT = DSL.field(DSL.name("output_weight"), Double.class);
}
