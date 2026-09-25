package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.ClientWithDutyDTO;
import jooqdata.tables.Client;
import jooqdata.tables.Order;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ClientServiceQueryContractTest {

    private static final Field<BigDecimal> DEBT_ORIGINAL =
            DSL.field("debt_original_amount", BigDecimal.class);
    private static final Field<BigDecimal> DEBT_REMAINING =
            DSL.field("debt_remaining_amount", BigDecimal.class);

    @Test
    void clientsWithDebtAreGroupedFromOneQuery() {
        AtomicInteger queryCount = new AtomicInteger();
        DSLContext data = DSL.using(SQLDialect.POSTGRES);
        MockConnection connection = new MockConnection(context -> {
            queryCount.incrementAndGet();
            Result<Record> rows = debtRows(data);
            return new MockResult[]{new MockResult(rows.size(), rows)};
        });
        ClientService service = new ClientService(
                DSL.using(connection, SQLDialect.POSTGRES),
                null,
                null,
                null
        );

        List<ClientWithDutyDTO> result = service.getClientsWithDutyOrders(true);

        assertThat(queryCount).hasValue(1);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getClient().getFullName()).isEqualTo("Анна");
        assertThat(result.get(0).getDutyOrders()).extracting(order -> order.getOrderId())
                .containsExactly(101, 102);
        assertThat(result.get(0).getTotalDuty()).isEqualTo(350.0);
        assertThat(result.get(1).getClient().getFullName()).isEqualTo("Борис");
        assertThat(result.get(1).getDutyOrders()).singleElement()
                .satisfies(order -> assertThat(order.getOrderId()).isEqualTo(201));
    }

    private Result<Record> debtRows(DSLContext data) {
        Field<?>[] fields = {
                Client.CLIENT.CLIENTID,
                Client.CLIENT.FULLNAME,
                Client.CLIENT.NUMBER,
                Order.ORDER.ORDERID,
                Order.ORDER.CLIENTID,
                Order.ORDER.DATE,
                Order.ORDER.CREATED_AT,
                Order.ORDER.STATUS,
                Order.ORDER.AMOUNT,
                Order.ORDER.DUTY,
                Order.ORDER.TIMEDELAY,
                Order.ORDER.DEBT_PAYMENT_DATE,
                DEBT_ORIGINAL,
                DEBT_REMAINING
        };
        Result<Record> result = data.newResult(fields);
        result.add(debtRow(data, fields, 1, "Анна", 101, "100.00"));
        result.add(debtRow(data, fields, 1, "Анна", 102, "250.00"));
        result.add(debtRow(data, fields, 2, "Борис", 201, "75.00"));
        return result;
    }

    private Record debtRow(
            DSLContext data,
            Field<?>[] fields,
            int clientId,
            String clientName,
            int orderId,
            String remaining
    ) {
        Record record = data.newRecord(fields);
        record.set(Client.CLIENT.CLIENTID, clientId);
        record.set(Client.CLIENT.FULLNAME, clientName);
        record.set(Client.CLIENT.NUMBER, "+7000000000" + clientId);
        record.set(Order.ORDER.ORDERID, orderId);
        record.set(Order.ORDER.CLIENTID, clientId);
        record.set(Order.ORDER.DATE, LocalDate.of(2026, 8, 29));
        record.set(Order.ORDER.CREATED_AT, LocalDateTime.of(2026, 8, 29, 12, 0));
        record.set(Order.ORDER.STATUS, false);
        record.set(Order.ORDER.AMOUNT, Double.valueOf(remaining));
        record.set(Order.ORDER.DUTY, true);
        record.set(Order.ORDER.DEBT_PAYMENT_DATE, LocalDate.of(2026, 9, 1));
        record.set(DEBT_ORIGINAL, new BigDecimal(remaining));
        record.set(DEBT_REMAINING, new BigDecimal(remaining));
        return record;
    }
}
