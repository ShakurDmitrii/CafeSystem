package com.shakur.cafehelp.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jooqdata.tables.Order;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TaxOutboxWriterReceiptDateTest {

    private static final DSLContext DATA = DSL.using(SQLDialect.POSTGRES);
    private static final Order ORDER = Order.ORDER;
    private static final Field<Boolean> IS_PAID = DSL.field(DSL.name("is_paid"), Boolean.class);
    private static final Field<String> PAYMENT_TYPE = DSL.field(DSL.name("payment_type"), String.class);
    private static final Field<LocalDateTime> CANCELLED_AT = DSL.field(DSL.name("cancelled_at"), LocalDateTime.class);
    private static final Field<LocalDateTime> PAID_AT = DSL.field(DSL.name("paid_at"), LocalDateTime.class);
    private static final Field<Long> OUTBOX_ID = DSL.field(DSL.name("id"), Long.class);

    private final List<String> payloads = new ArrayList<>();

    @Test
    void debtPaidLaterIsReceiptedOnPaymentDate() {
        LocalDate orderDate = LocalDate.of(2026, 8, 28);
        LocalDateTime paidAt = LocalDateTime.of(2026, 9, 29, 18, 45);

        var result = writer(orderDate, paidAt).enqueuePaidOrder(
                42, Map.of("items", List.of()), "order_paid", "debt-payment", false, null
        );

        assertThat(result.receiptDate()).isEqualTo(LocalDate.of(2026, 9, 29));
        assertThat(payloads).singleElement().satisfies(json -> {
            assertThat(json).contains("\"orderDate\":\"2026-09-29\"");
            assertThat(json).contains("\"paidAt\":\"2026-09-29T18:45\"");
        });
    }

    @Test
    void legacyOrderWithoutPaymentTimeKeepsOrderDate() {
        LocalDate orderDate = LocalDate.of(2026, 8, 28);

        var result = writer(orderDate, null).enqueuePaidOrder(
                42, Map.of(), "order_paid", "backfill", false, null
        );

        assertThat(result.receiptDate()).isEqualTo(orderDate);
    }

    @Test
    void explicitReceiptDateStillWins() {
        var result = writer(LocalDate.of(2026, 8, 28), LocalDateTime.of(2026, 9, 29, 10, 0)).enqueuePaidOrder(
                42, Map.of(), "order_paid", "backfill", false, LocalDate.of(2026, 9, 1)
        );

        assertThat(result.receiptDate()).isEqualTo(LocalDate.of(2026, 9, 1));
    }

    private TaxOutboxWriterService writer(LocalDate orderDate, LocalDateTime paidAt) {
        MockConnection connection = new MockConnection(context -> {
            String sql = context.sql().toLowerCase();
            if (sql.startsWith("select")) {
                var result = DATA.newResult(
                        ORDER.ORDERID, ORDER.SHIFTID, ORDER.DATE, IS_PAID, PAYMENT_TYPE, CANCELLED_AT, PAID_AT
                );
                result.add(DATA.newRecord(
                        ORDER.ORDERID, ORDER.SHIFTID, ORDER.DATE, IS_PAID, PAYMENT_TYPE, CANCELLED_AT, PAID_AT
                ).values(42, 7, orderDate, true, "cash", null, paidAt));
                return new MockResult[]{new MockResult(1, result)};
            }
            for (Object binding : context.bindings()) {
                if (binding instanceof String text && text.startsWith("{")) {
                    payloads.add(text);
                }
            }
            var inserted = DATA.newResult(OUTBOX_ID);
            inserted.add(DATA.newRecord(OUTBOX_ID).values(100L));
            return new MockResult[]{new MockResult(1, inserted)};
        });
        return new TaxOutboxWriterService(
                DSL.using(connection, SQLDialect.POSTGRES), new ObjectMapper().findAndRegisterModules(), null
        );
    }
}
