package com.shakur.cafehelp.Service;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryValuationPreparationAdjustmentTest {

    private static final DSLContext DATA = DSL.using(SQLDialect.POSTGRES);
    private static final Field<Double> QUANTITY = DSL.field("quantity", Double.class);
    private static final Field<BigDecimal> INVENTORY_VALUE = DSL.field("inventory_value", BigDecimal.class);
    private static final Field<BigDecimal> SUM = DSL.field("sum", BigDecimal.class);
    private static final Field<BigDecimal> UNIT_COST = DSL.field("unit_cost", BigDecimal.class);

    @Test
    void manualWriteOffRemovesValueTogetherWithQuantity() {
        List<String> statements = new ArrayList<>();
        List<Object[]> bindings = new ArrayList<>();
        WareHouseService wareHouseService = warehouseService();
        InventoryValuationService service = new InventoryValuationService(
                dsl(statements, bindings, 10.0, "1000", "10"), wareHouseService
        );

        var change = service.adjustPreparationAtCurrentCost(1, 7, new BigDecimal("-10"), "manual_adjustment", null, "test");

        assertThat(change.value()).isEqualByComparingTo("1000");
        assertThat(change.balanceAfter().quantity()).isEqualByComparingTo("0");
        assertThat(change.balanceAfter().value()).isEqualByComparingTo("0");
        verify(wareHouseService).adjustPreparationQuantity(1, 7, -10.0);

        int update = indexOf(statements, "update \"sales\".\"preparationwarehouse\"");
        assertThat(update).isNotNegative();
        assertThat(Arrays.asList(bindings.get(update))).contains(new BigDecimal("0.000000"));
        assertThat(indexOf(statements, "insert into \"sales\".\"preparation_stock_movements\"")).isNotNegative();
    }

    @Test
    void negativePhysicalStockIsSetToTheAdjustedTargetInsteadOfAddingDelta() {
        WareHouseService wareHouseService = warehouseService();
        InventoryValuationService service = new InventoryValuationService(
                dsl(new ArrayList<>(), new ArrayList<>(), -2.0, "0", "-2"), wareHouseService
        );

        var change = service.adjustPreparationAtCurrentCost(1, 7, new BigDecimal("5"), "manual_adjustment", null, "test");

        assertThat(change.balanceAfter().quantity()).isEqualByComparingTo("5");
        verify(wareHouseService).adjustPreparationQuantity(1, 7, 7.0);
    }

    @Test
    void shortageIsRejectedWithoutTouchingStock() {
        WareHouseService wareHouseService = warehouseService();
        InventoryValuationService service = new InventoryValuationService(
                dsl(new ArrayList<>(), new ArrayList<>(), 3.0, "30", "3"), wareHouseService
        );

        assertThatThrownBy(() -> service.adjustPreparationAtCurrentCost(
                1, 7, new BigDecimal("-4"), "manual_adjustment", null, "test"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Недостаточно заготовки");
        verify(wareHouseService, never()).adjustPreparationQuantity(anyInt(), anyInt(), anyDouble());
    }

    private WareHouseService warehouseService() {
        WareHouseService wareHouseService = mock(WareHouseService.class);
        when(wareHouseService.lockWarehousesForInventory(1)).thenReturn(true);
        when(wareHouseService.adjustPreparationQuantity(eq(1), eq(7), anyDouble())).thenReturn(true);
        return wareHouseService;
    }

    private DSLContext dsl(
            List<String> statements,
            List<Object[]> bindings,
            double quantity,
            String value,
            String physicalQuantity
    ) {
        MockConnection connection = new MockConnection(context -> {
            String sql = context.sql().toLowerCase();
            statements.add(sql);
            bindings.add(context.bindings());
            if (sql.startsWith("select") && sql.contains("preparation_stock_movements")) {
                var result = DATA.newResult(UNIT_COST);
                return new MockResult[]{new MockResult(0, result)};
            }
            if (sql.startsWith("select") && sql.contains("sum(")) {
                var result = DATA.newResult(SUM);
                var row = DATA.newRecord(SUM);
                row.set(SUM, new BigDecimal(physicalQuantity));
                result.add(row);
                return new MockResult[]{new MockResult(1, result)};
            }
            if (sql.startsWith("select") && sql.contains("preparationwarehouse")) {
                var result = DATA.newResult(QUANTITY, INVENTORY_VALUE);
                var row = DATA.newRecord(QUANTITY, INVENTORY_VALUE);
                row.set(QUANTITY, quantity);
                row.set(INVENTORY_VALUE, new BigDecimal(value));
                result.add(row);
                return new MockResult[]{new MockResult(1, result)};
            }
            return new MockResult[]{new MockResult(1)};
        });
        return DSL.using(connection, SQLDialect.POSTGRES);
    }

    private int indexOf(List<String> statements, String prefix) {
        for (int i = 0; i < statements.size(); i++) {
            if (statements.get(i).startsWith(prefix)) return i;
        }
        return -1;
    }
}
