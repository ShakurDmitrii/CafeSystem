package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.DishSetDTO;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static jooqdata.tables.Dish.DISH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

class DishSetServiceReadContractTest {
    private static final Field<Integer> SET_ID = DSL.field(DSL.name("setid"), Integer.class);
    private static final Field<String> SET_NAME = DSL.field(DSL.name("setname"), String.class);
    private static final Field<Double> SET_PRICE = DSL.field(DSL.name("price"), Double.class);
    private static final Field<Double> SET_FIRST_COST = DSL.field(DSL.name("first_cost"), Double.class);
    private static final Field<String> SET_IMAGE_URL = DSL.field(DSL.name("image_url"), String.class);
    private static final Field<Integer> ITEM_ID = DSL.field(DSL.name("set_item_id"), Integer.class);
    private static final Field<Integer> ITEM_SET_ID = DSL.field(DSL.name("set_id"), Integer.class);
    private static final Field<Integer> ITEM_DISH_ID = DSL.field(DSL.name("dish_id"), Integer.class);
    private static final Field<Integer> ITEM_QTY = DSL.field(DSL.name("qty"), Integer.class);

    @Test
    void menuSetReadCalculatesCostWithoutUpdatingDatabase() {
        List<String> statements = new ArrayList<>();
        DSLContext data = DSL.using(SQLDialect.POSTGRES);
        MockConnection connection = new MockConnection(context -> {
            String sql = context.sql().toLowerCase();
            statements.add(sql);
            Result<Record> rows = sql.contains("dish_set_item") ? itemRows(data) : setRows(data);
            return new MockResult[]{new MockResult(rows.size(), rows)};
        });
        RecipeCostService costs = mock(RecipeCostService.class);
        when(costs.calculateDishCosts(anyCollection())).thenReturn(Map.of(10, 50.0));
        DishSetService service = new DishSetService(DSL.using(connection, SQLDialect.POSTGRES), costs);

        List<DishSetDTO> result = service.getAll();

        assertThat(result).singleElement().satisfies(set -> assertThat(set.getFirstCost()).isEqualTo(100.0));
        assertThat(statements).noneMatch(sql -> sql.stripLeading().startsWith("update"));
    }

    private Result<Record> setRows(DSLContext data) {
        Field<?>[] fields = {SET_ID, SET_NAME, SET_PRICE, SET_FIRST_COST, SET_IMAGE_URL};
        Result<Record> rows = data.newResult(fields);
        Record row = data.newRecord(fields);
        row.set(SET_ID, 7);
        row.set(SET_NAME, "Сет");
        row.set(SET_PRICE, 500.0);
        row.set(SET_FIRST_COST, 0.0);
        rows.add(row);
        return rows;
    }

    private Result<Record> itemRows(DSLContext data) {
        Field<String> dishImage = DSL.field(DSL.name("image_url"), String.class);
        Field<?>[] fields = {
                ITEM_ID, ITEM_SET_ID, ITEM_DISH_ID, ITEM_QTY,
                DISH.DISHNAME, DISH.PRICE, DISH.FIRSTCOST, dishImage, DISH.CATEGORY
        };
        Result<Record> rows = data.newResult(fields);
        Record row = data.newRecord(fields);
        row.set(ITEM_ID, 1);
        row.set(ITEM_SET_ID, 7);
        row.set(ITEM_DISH_ID, 10);
        row.set(ITEM_QTY, 2);
        row.set(DISH.DISHNAME, "Ролл");
        row.set(DISH.PRICE, 250.0);
        row.set(DISH.FIRSTCOST, 40.0);
        rows.add(row);
        return rows;
    }
}
