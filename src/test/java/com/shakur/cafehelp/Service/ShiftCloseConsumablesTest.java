package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.config.BusinessTimeProvider;
import jooqdata.tables.Order;
import jooqdata.tables.Shift;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShiftCloseConsumablesTest {

    private static final DSLContext DATA = DSL.using(SQLDialect.POSTGRES);
    private static final Field<Integer> OC_ORDER_ID = DSL.field(DSL.name("order_id"), Integer.class);
    private static final Field<BigDecimal> OC_SURCHARGE = DSL.field(DSL.name("surcharge"), BigDecimal.class);
    private static final Field<BigDecimal> OC_COST = DSL.field(DSL.name("cost"), BigDecimal.class);

    @Test
    void packagingCostReducesShiftProfit() {
        List<String> statements = new ArrayList<>();
        List<Object[]> bindings = new ArrayList<>();
        MockConnection connection = new MockConnection(context -> {
            String sql = context.sql().toLowerCase();
            statements.add(sql);
            bindings.add(context.bindings());
            if (sql.startsWith("update")) {
                return new MockResult[]{new MockResult(1)};
            }
            if (sql.contains("from \"sales\".\"order_consumable\"")) {
                var result = DATA.newResult(OC_ORDER_ID, OC_SURCHARGE, OC_COST);
                result.add(DATA.newRecord(OC_ORDER_ID, OC_SURCHARGE, OC_COST)
                        .values(10, new BigDecimal("40.00"), new BigDecimal("30.00")));
                return new MockResult[]{new MockResult(1, result)};
            }
            if (sql.contains("from \"sales\".\"orderdish\"")) {
                return new MockResult[]{new MockResult(0, DATA.newResult(DSL.field("unused")))};
            }
            if (sql.contains("from \"sales\".\"order\"")) {
                var result = DATA.newResult(Order.ORDER.fields());
                var order = DATA.newRecord(Order.ORDER);
                order.setOrderid(10);
                order.setShiftid(1);
                order.setAmount(500.0);
                order.setIsPaid(true);
                result.add(order);
                return new MockResult[]{new MockResult(1, result)};
            }
            if (sql.contains("from \"sales\".\"shift\"")) {
                var result = DATA.newResult(Shift.SHIFT.fields());
                var shift = DATA.newRecord(Shift.SHIFT);
                shift.setId(1);
                shift.setData(LocalDate.of(2026, 9, 30));
                shift.setStarttime(LocalTime.of(9, 0));
                result.add(shift);
                return new MockResult[]{new MockResult(1, result)};
            }
            return new MockResult[]{new MockResult(0, DATA.newResult(DSL.field("unused")))};
        });
        BusinessTimeProvider time = mock(BusinessTimeProvider.class);
        when(time.now()).thenReturn(LocalDateTime.of(2026, 9, 30, 22, 0));
        ShiftService service = new ShiftService(
                DSL.using(connection, SQLDialect.POSTGRES), null, time, mock(PayrollService.class)
        );

        service.closeShift(1, new BigDecimal("20"));

        Object[] shiftUpdate = null;
        for (int i = 0; i < statements.size(); i++) {
            if (statements.get(i).startsWith("update \"sales\".\"shift\"") && statements.get(i).contains("\"profit\"")) {
                shiftUpdate = bindings.get(i);
            }
        }
        assertThat(shiftUpdate).isNotNull();
        // 500 выручки − 30 себестоимости упаковки − 20 расходов смены
        assertThat(Arrays.stream(shiftUpdate)
                .filter(BigDecimal.class::isInstance)
                .map(BigDecimal.class::cast)
                .anyMatch(value -> value.compareTo(new BigDecimal("450")) == 0)).isTrue();
    }
}
