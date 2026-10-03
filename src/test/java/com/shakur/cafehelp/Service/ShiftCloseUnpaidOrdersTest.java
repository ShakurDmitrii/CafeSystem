package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.config.BusinessTimeProvider;
import com.shakur.cafehelp.exception.ShiftStateConflictException;
import jooqdata.tables.Order;
import jooqdata.tables.Shift;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ShiftCloseUnpaidOrdersTest {

    private static final DSLContext DATA = DSL.using(SQLDialect.POSTGRES);

    @Test
    void shiftWithUnpaidOrdersCannotBeClosed() {
        List<String> statements = new ArrayList<>();
        MockConnection connection = new MockConnection(context -> {
            String sql = context.sql().toLowerCase();
            statements.add(sql);
            if (sql.startsWith("select \"sales\".\"order\".\"orderid\" from \"sales\".\"order\"")) {
                var result = DATA.newResult(Order.ORDER.ORDERID);
                result.add(DATA.newRecord(Order.ORDER.ORDERID).values(12));
                result.add(DATA.newRecord(Order.ORDER.ORDERID).values(15));
                return new MockResult[]{new MockResult(2, result)};
            }
            if (sql.contains("from \"sales\".\"shift\"")) {
                var result = DATA.newResult(Shift.SHIFT.fields());
                var shift = DATA.newRecord(Shift.SHIFT);
                shift.setId(1);
                shift.setData(LocalDate.of(2026, 10, 3));
                shift.setStarttime(LocalTime.of(9, 0));
                result.add(shift);
                return new MockResult[]{new MockResult(1, result)};
            }
            return new MockResult[]{new MockResult(0, DATA.newResult(DSL.field("unused")))};
        });
        PayrollService payroll = mock(PayrollService.class);
        ShiftService service = new ShiftService(
                DSL.using(connection, SQLDialect.POSTGRES), null, mock(BusinessTimeProvider.class), payroll
        );

        assertThatThrownBy(() -> service.closeShift(1, BigDecimal.ZERO))
                .isInstanceOf(ShiftStateConflictException.class)
                .hasMessageContaining("неоплаченные заказы № 12, 15");

        String unpaidQuery = statements.stream()
                .filter(sql -> sql.startsWith("select \"sales\".\"order\".\"orderid\""))
                .findFirst().orElseThrow();
        // долг и отменённые заказы закрытию не мешают
        assertThat(unpaidQuery).contains("\"cancelled_at\" is null").contains("\"duty\"");
        assertThat(statements).noneMatch(sql -> sql.startsWith("update"));
        verifyNoInteractions(payroll);
    }
}
