package com.shakur.cafehelp.Service.MlServices;

import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static jooqdata.tables.Dish.DISH;
import static jooqdata.tables.Order.ORDER;
import static jooqdata.tables.Orderdish.ORDERDISH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MlSalesDataTest {
    @Test
    void salesUseExpandedRecipeAndDoNotMultiplyQuantityByIngredientCount() {
        var menu = mock(MenuService.class);
        when(menu.getDishIngredients(7)).thenReturn(List.of("rice", "fish", "nested sauce"));
        var fields = new org.jooq.Field<?>[]{ORDER.DATE, ORDER.ORDERID, DISH.DISHID,
                DISH.DISHNAME, DISH.PRICE, DISH.FIRSTCOST, ORDERDISH.QTY};
        var data = DSL.using(SQLDialect.POSTGRES).newResult(fields);
        for (int orderId = 1; orderId <= 2; orderId++) {
            var record = DSL.using(SQLDialect.POSTGRES).newRecord(fields);
            record.set(ORDER.DATE, LocalDate.of(2026, 8, 1));
            record.set(ORDER.ORDERID, orderId);
            record.set(DISH.DISHID, 7);
            record.set(DISH.DISHNAME, "Roll");
            record.set(DISH.PRICE, 300.0);
            record.set(ORDERDISH.QTY, 3 - orderId);
            data.add(record);
        }
        var dsl = DSL.using(new MockConnection(context -> {
            assertThat(context.sql()).contains("firstcost", "cancelled_at");
            return new MockResult[]{new MockResult(2, data)};
        }), SQLDialect.POSTGRES);
        var sales = new SalesService(dsl, menu).getSalesForMLOptimized(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2));
        assertThat(sales).hasSize(2);
        assertThat(sales.getFirst().getIngredients()).contains("nested sauce");
        assertThat(sales.getFirst().getQuantity()).isEqualTo(2);
        assertThat(sales.getFirst().getTotalCost()).isNull();
        verify(menu, times(1)).getDishIngredients(7);
    }
}
