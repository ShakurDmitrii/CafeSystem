package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.OrderDishDTO;
import com.shakur.cafehelp.exception.OrderStateConflictException;
import jooqdata.tables.Dish;
import jooqdata.tables.Order;
import jooqdata.tables.Shift;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class OrderServicePaymentGuardTest {

    private static final DSLContext DATA = DSL.using(SQLDialect.POSTGRES);
    private static final Order ORDER = Order.ORDER;
    private static final Field<LocalDateTime> CANCELLED_AT = DSL.field("cancelled_at", LocalDateTime.class);
    private static final Field<Boolean> INVENTORY_CONSUMED = DSL.field("inventory_consumed", Boolean.class);
    private static final Field<Integer> VERSION = DSL.field("version", Integer.class);

    private final List<String> statements = new ArrayList<>();
    private final List<Object[]> bindings = new ArrayList<>();

    @Test
    void paidOrderCannotBeSwitchedBackToUnpaid() {
        TaxOutboxWriterService tax = mock(TaxOutboxWriterService.class);
        OrderService service = service(paidOrder("cash"), tax);

        assertThatThrownBy(() -> service.updateOrderPayment(10, "unpaid", false, null))
                .isInstanceOf(OrderStateConflictException.class)
                .hasMessageContaining("отмену заказа с возвратом");

        assertThat(updates()).isEmpty();
        verifyNoInteractions(tax);
    }

    @Test
    void paidOrderCannotChangePaymentType() {
        OrderService service = service(paidOrder("cash"), mock(TaxOutboxWriterService.class));

        assertThatThrownBy(() -> service.updateOrderPayment(10, "transfer", true, null))
                .isInstanceOf(OrderStateConflictException.class);

        assertThat(updates()).isEmpty();
    }

    @Test
    void repeatingTheSamePaymentIsANoOp() {
        TaxOutboxWriterService tax = mock(TaxOutboxWriterService.class);
        OrderService service = service(paidOrder("transfer"), tax);

        service.updateOrderPayment(10, "transfer", true, null);

        assertThat(updates()).isEmpty();
        verifyNoInteractions(tax);
    }

    @Test
    void dishesCannotBeAddedToPaidOrder() {
        OrderService service = service(paidOrder("cash"), mock(TaxOutboxWriterService.class));

        assertThatThrownBy(() -> service.addDishesToOrder(10, List.of(dish(3, 2))))
                .isInstanceOf(OrderStateConflictException.class);

        assertThat(statements).noneMatch(sql -> sql.startsWith("insert"));
    }

    @Test
    void addingDishesRaisesOrderAmountAndVersion() {
        OrderService service = service(unpaidOrder(500.0), mock(TaxOutboxWriterService.class));

        service.addDishesToOrder(10, List.of(dish(3, 2)));

        assertThat(statements).anyMatch(sql -> sql.startsWith("insert into \"sales\".\"orderdish\""));
        List<Object[]> orderUpdates = updates();
        assertThat(orderUpdates).hasSize(1);
        // 500 + 2 x 150 = 800, версия 3 -> 4
        assertThat(Arrays.asList(orderUpdates.get(0))).contains(800.0, 4);
    }

    private OrderService service(org.jooq.Record order, TaxOutboxWriterService tax) {
        MockConnection connection = new MockConnection(context -> {
            String sql = context.sql().toLowerCase();
            statements.add(sql);
            bindings.add(context.bindings());
            if (sql.startsWith("insert") || sql.startsWith("update")) {
                return new MockResult[]{new MockResult(1)};
            }
            if (sql.startsWith("select \"sales\".\"order\".\"shiftid\" from")) {
                return one(ORDER.SHIFTID, 1);
            }
            if (sql.startsWith("select \"sales\".\"shift\".\"endtime\" from")) {
                return one(Shift.SHIFT.ENDTIME, null);
            }
            if (sql.contains("from \"sales\".\"shift\"")) {
                var result = DATA.newResult(Shift.SHIFT.ID, Shift.SHIFT.ENDTIME);
                result.add(DATA.newRecord(Shift.SHIFT.ID, Shift.SHIFT.ENDTIME).values(1, null));
                return new MockResult[]{new MockResult(1, result)};
            }
            if (sql.contains("from \"sales\".\"order\"") && sql.contains("for update")) {
                var result = DATA.newResult(ORDER.fields());
                result.add(order);
                return new MockResult[]{new MockResult(1, result)};
            }
            if (sql.startsWith("select \"cancelled_at\"")) {
                return new MockResult[]{new MockResult(0, DATA.newResult(CANCELLED_AT))};
            }
            if (sql.startsWith("select \"inventory_consumed\"")) {
                return one(INVENTORY_CONSUMED, false);
            }
            if (sql.startsWith("select \"version\"")) {
                return one(VERSION, 3);
            }
            if (sql.contains("from \"sales\".\"dish\"")) {
                var result = DATA.newResult(Dish.DISH.PRICE, Dish.DISH.FIRSTCOST);
                result.add(DATA.newRecord(Dish.DISH.PRICE, Dish.DISH.FIRSTCOST).values(150.0, 50.0));
                return new MockResult[]{new MockResult(1, result)};
            }
            return new MockResult[]{new MockResult(0, DATA.newResult(DSL.field("unused")))};
        });
        DSLContext dsl = DSL.using(connection, SQLDialect.POSTGRES);
        return new OrderService(dsl, null, null, null, null, tax, mock(ConsumableService.class));
    }

    private <T> MockResult[] one(Field<T> field, T value) {
        var result = DATA.newResult(field);
        result.add(DATA.newRecord(field).values(value));
        return new MockResult[]{new MockResult(1, result)};
    }

    private org.jooq.Record paidOrder(String paymentType) {
        var order = DATA.newRecord(ORDER);
        order.setOrderid(10);
        order.setShiftid(1);
        order.setAmount(500.0);
        order.setDuty(false);
        order.setIsPaid(true);
        order.setPaymentType(paymentType);
        return order;
    }

    private org.jooq.Record unpaidOrder(double amount) {
        var order = DATA.newRecord(ORDER);
        order.setOrderid(10);
        order.setShiftid(1);
        order.setAmount(amount);
        order.setDuty(false);
        order.setIsPaid(false);
        order.setPaymentType("unpaid");
        return order;
    }

    private OrderDishDTO dish(int dishId, int qty) {
        OrderDishDTO item = new OrderDishDTO();
        item.setDishID(dishId);
        item.setQty(qty);
        return item;
    }

    private List<Object[]> updates() {
        List<Object[]> result = new ArrayList<>();
        for (int i = 0; i < statements.size(); i++) {
            if (statements.get(i).startsWith("update \"sales\".\"order\"")) {
                result.add(bindings.get(i));
            }
        }
        return result;
    }
}
