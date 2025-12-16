package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.OrderDTO;
import jooqdata.tables.Order;
import jooqdata.tables.Orderdish;
import jooqdata.tables.records.OrderRecord;
import jooqdata.tables.records.OrderdishRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static jooqdata.tables.Order.ORDER;

@Service
public class OrderService {
    private DSLContext dsl;
    public OrderService(DSLContext dsl) {
        this.dsl = dsl;
    }

    public OrderDTO createOrder(OrderDTO order) {
        OrderRecord record = dsl.newRecord(ORDER);

        // Устанавливаем поля от пользователя
        record.setClientid(order.getClientId());
        record.setDate(order.getDate());
        record.setShiftid(order.getShiftId());
        record.setAmount(order.getAmount());
        record.setStatus(order.getStatus()); // если есть
        record.setType(order.getType());

        // Сохраняем
        record.store();

        // Возвращаем DTO с сгенерированным ID
        order.setOrderId(record.getOrderid());

        return order;
    }
    @Transactional
    public Boolean updateOrderStatus(int orderId, Boolean status) {
        var record = dsl.selectFrom(ORDER)
                .where(ORDER.ORDERID.eq(orderId))
                .fetchOne();

        if (record == null) {
            throw new RuntimeException("Заказ с id " + orderId + " не найден");
        }

        record.setStatus(status); // обновляем статус
        record.store();           // сохраняем

        return record.getStatus();
    }

    public OrderDTO getOrderById(int id) {
        return dsl.selectFrom(ORDER)
                .where(ORDER.ORDERID.eq(id))
                .fetchOne(record -> {
                    OrderDTO order = new OrderDTO();
                    order.clientId = record.get(ORDER.CLIENTID);
                    order.orderId = record.get(ORDER.ORDERID);
                    order.date = record.get(ORDER.DATE);
                    order.status = record.get(ORDER.STATUS);
                    order.amount = record.get(ORDER.AMOUNT);
                    order.shiftId = record.get(ORDER.SHIFTID);
                    order.type = record.get(ORDER.TYPE);
                    return order;
                });
    }


    public List<OrderDTO> getOrdersByClientId(int clientId) {
        return dsl.selectFrom(ORDER)
                .where(ORDER.CLIENTID.eq(clientId))
                .fetch(record -> {
                    OrderDTO order = new OrderDTO();
                    order.setClientId(record.get(ORDER.CLIENTID));
                    order.setOrderId(record.get(ORDER.ORDERID));
                    order.setDate(record.get(ORDER.DATE));
                    order.setStatus(record.get(ORDER.STATUS));
                    order.setAmount(record.get(ORDER.AMOUNT));
                    order.setShiftId(record.get(ORDER.SHIFTID));
                    order.setType(record.get(ORDER.TYPE));
                    return order;
                });
    }
    public List<OrderDTO> getOrdersByDate(LocalDate date) {
        return dsl.selectFrom(ORDER)
                .where(ORDER.DATE.eq(date))
                .fetch(record -> {
                    OrderDTO order = new OrderDTO();
                    order.setClientId(record.get(ORDER.CLIENTID));
                    order.setOrderId(record.get(ORDER.ORDERID));
                    order.setDate(record.get(ORDER.DATE));
                    order.setStatus(record.get(ORDER.STATUS));
                    order.setAmount(record.get(ORDER.AMOUNT));
                    order.setShiftId(record.get(ORDER.SHIFTID));
                    order.setType(record.get(ORDER.TYPE));
                    return order;
                });
    }

    public List<OrderDTO> getOrdersByDateAndClientId(LocalDate date, int id) {
        return dsl.selectFrom(ORDER)
                .where(ORDER.CLIENTID.eq(id)
                        .and(ORDER.DATE.eq(date)))
                .fetch(record -> {
                    OrderDTO order = new OrderDTO();
                    order.setClientId(record.get(ORDER.CLIENTID));
                    order.setOrderId(record.get(ORDER.ORDERID));
                    order.setDate(record.get(ORDER.DATE));
                    order.setStatus(record.get(ORDER.STATUS));
                    order.setAmount(record.get(ORDER.AMOUNT));
                    order.setShiftId(record.get(ORDER.SHIFTID));
                    return order;
                });
    }

    public List<OrderDTO> getOrdersByStatus(Boolean status) {
        return dsl.selectFrom(ORDER)
                .where(ORDER.STATUS.eq(status))
                .fetch(record -> {
                    OrderDTO order = new OrderDTO();
                    order.setClientId(record.get(ORDER.CLIENTID));
                    order.setOrderId(record.get(ORDER.ORDERID));
                    order.setDate(record.get(ORDER.DATE));
                    order.setStatus(record.get(ORDER.STATUS));
                    order.setAmount(record.get(ORDER.AMOUNT));
                    order.setShiftId(record.get(ORDER.SHIFTID));
                    return order;
                });
    }
    public List<OrderDTO> getOrdersByShift(int id) {
        return dsl.selectFrom(ORDER)
                .where(ORDER.SHIFTID.eq(id))
                .fetch(record -> {
                    OrderDTO order = new OrderDTO();
                    order.setClientId(record.get(ORDER.CLIENTID));
                    order.setOrderId(record.get(ORDER.ORDERID));
                    order.setDate(record.get(ORDER.DATE));
                    order.setStatus(record.get(ORDER.STATUS));
                    order.setAmount(record.get(ORDER.AMOUNT));
                    order.setShiftId(record.get(ORDER.SHIFTID));
                    return order;
                });
    }
public List<OrderDTO> getOrders() {
        return dsl.selectFrom(ORDER)
                .fetch()
                .stream()
                .map(record ->{
                    OrderDTO order = new OrderDTO();
                    order.setClientId(record.get(ORDER.CLIENTID));
                    order.setOrderId(record.get(ORDER.ORDERID));
                    order.setDate(record.get(ORDER.DATE));
                    order.setStatus(record.get(ORDER.STATUS));
                    order.setAmount(record.get(ORDER.AMOUNT));
                    order.setShiftId(record.get(ORDER.SHIFTID));
                    return order;
                }).toList();
}
    public void addDishToOrder(int orderId, int dishId, int qty) {
        System.out.println("addDishToOrder вызван с параметрами: orderId=" + orderId + ", dishId=" + dishId + ", qty=" + qty);

        try {
            OrderdishRecord record = dsl.newRecord(Orderdish.ORDERDISH);
            record.setOrderid(orderId);
            record.setDishid(dishId);
            record.setQty(qty);
            record.store(); // безопаснее execute()
            System.out.println("Блюдо добавлено в заказ: " + record.getOrderid() + ", " + record.getDishid() + ", qty=" + record.getQty());
        } catch (Exception e) {
            System.err.println("Ошибка при добавлении блюда в заказ: " + e.getMessage());
            e.printStackTrace();
        }
    }


}
