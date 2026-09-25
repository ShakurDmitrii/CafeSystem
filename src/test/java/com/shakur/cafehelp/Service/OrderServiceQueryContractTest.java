package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.OrderDTO;
import jooqdata.tables.Dish;
import jooqdata.tables.Order;
import jooqdata.tables.Orderdish;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class OrderServiceQueryContractTest {

    private static final Field<String> DELIVERY_PHONE = DSL.field("delivery_phone", String.class);
    private static final Field<String> DELIVERY_ADDRESS = DSL.field("delivery_address", String.class);
    private static final Field<String> PAYMENT_TYPE = DSL.field("payment_type", String.class);
    private static final Field<Boolean> IS_PAID = DSL.field("is_paid", Boolean.class);
    private static final Field<BigDecimal> CASH_RECEIVED = DSL.field("cash_received", BigDecimal.class);
    private static final Field<BigDecimal> CASH_CHANGE = DSL.field("cash_change", BigDecimal.class);
    private static final Field<Integer> SET_ID = DSL.field("set_id", Integer.class);
    private static final Field<Double> UNIT_PRICE = DSL.field("unit_price", Double.class);
    private static final Field<String> SET_NAME = DSL.field("set_name", String.class);
    private static final Field<Double> SET_PRICE = DSL.field("set_price", Double.class);

    @Test
    void getOrdersLoadsAllLineItemsInOneBatchQuery() {
        AtomicInteger queryCount = new AtomicInteger();
        DSLContext data = DSL.using(SQLDialect.POSTGRES);
        MockConnection connection = new MockConnection(context -> {
            queryCount.incrementAndGet();
            String sql = context.sql().toLowerCase();
            if (sql.contains("orderdish")) {
                return new MockResult[]{new MockResult(2, orderItems(data))};
            }
            return new MockResult[]{new MockResult(2, orders(data))};
        });
        DSLContext dsl = DSL.using(connection, SQLDialect.POSTGRES);
        OrderService service = new OrderService(dsl, null, null, null, null, null, null);

        List<OrderDTO> result = service.getOrders();

        assertThat(queryCount).hasValue(2);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getItems())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getDishID()).isEqualTo(10);
                    assertThat(item.getDishName()).isEqualTo("Филадельфия");
                    assertThat(item.getQty()).isEqualTo(2);
                });
        assertThat(result.get(1).getItems())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getSetId()).isEqualTo(20);
                    assertThat(item.getDishName()).isEqualTo("Сет дня");
                });
    }

    private Result<Record> orders(DSLContext data) {
        List<Field<?>> fields = new ArrayList<>(Arrays.asList(Order.ORDER.fields()));
        fields.addAll(List.of(DELIVERY_PHONE, DELIVERY_ADDRESS, PAYMENT_TYPE, IS_PAID, CASH_RECEIVED, CASH_CHANGE));
        Result<Record> result = data.newResult(fields.toArray(Field[]::new));
        result.add(orderRecord(data, fields, 1));
        result.add(orderRecord(data, fields, 2));
        return result;
    }

    private Record orderRecord(DSLContext data, List<Field<?>> fields, int orderId) {
        Record record = data.newRecord(fields.toArray(Field[]::new));
        record.set(Order.ORDER.ORDERID, orderId);
        record.set(Order.ORDER.SHIFTID, 7);
        record.set(Order.ORDER.DATE, LocalDate.of(2026, 8, 29));
        record.set(Order.ORDER.AMOUNT, 100.0 * orderId);
        record.set(DELIVERY_PHONE, "+70000000000");
        record.set(DELIVERY_ADDRESS, "Адрес");
        record.set(PAYMENT_TYPE, "cash");
        record.set(IS_PAID, true);
        record.set(CASH_RECEIVED, new BigDecimal("200.00"));
        record.set(CASH_CHANGE, new BigDecimal("100.00"));
        return record;
    }

    private Result<Record> orderItems(DSLContext data) {
        Field<Double> dishPrice = Dish.DISH.PRICE.as("dish_price");
        Field<?>[] fields = {
                Orderdish.ORDERDISH.ORDERID,
                Orderdish.ORDERDISH.DISHID,
                SET_ID,
                Orderdish.ORDERDISH.QTY,
                UNIT_PRICE,
                Dish.DISH.DISHNAME,
                dishPrice,
                SET_NAME,
                SET_PRICE
        };
        Result<Record> result = data.newResult(fields);

        Record dish = data.newRecord(fields);
        dish.set(Orderdish.ORDERDISH.ORDERID, 1);
        dish.set(Orderdish.ORDERDISH.DISHID, 10);
        dish.set(Orderdish.ORDERDISH.QTY, 2);
        dish.set(UNIT_PRICE, 150.0);
        dish.set(Dish.DISH.DISHNAME, "Филадельфия");
        dish.set(dishPrice, 140.0);
        result.add(dish);

        Record set = data.newRecord(fields);
        set.set(Orderdish.ORDERDISH.ORDERID, 2);
        set.set(SET_ID, 20);
        set.set(Orderdish.ORDERDISH.QTY, 1);
        set.set(SET_NAME, "Сет дня");
        set.set(SET_PRICE, 500.0);
        result.add(set);
        return result;
    }
}
