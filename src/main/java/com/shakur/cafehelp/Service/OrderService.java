package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.OrderDTO;
import com.shakur.cafehelp.DTO.OrderDishDTO;
import com.shakur.cafehelp.DTO.OrderEditRequestDTO;
import com.shakur.cafehelp.exception.InvalidOrderRequestException;
import com.shakur.cafehelp.exception.OrderNotFoundException;
import com.shakur.cafehelp.exception.OrderStateConflictException;
import jooqdata.tables.Order;
import jooqdata.tables.Orderdish;
import jooqdata.tables.Dish;
import jooqdata.tables.Shift;
import jooqdata.tables.records.OrderRecord;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static jooqdata.tables.Order.ORDER;
import static jooqdata.tables.Orderdish.ORDERDISH;
import static jooqdata.tables.Dish.DISH;

@Service
public class OrderService {
    private static final Field<String> DELIVERY_PHONE_FIELD = DSL.field(DSL.name("delivery_phone"), String.class);
    private static final Field<String> DELIVERY_ADDRESS_FIELD = DSL.field(DSL.name("delivery_address"), String.class);
    private static final Field<String> PAYMENT_TYPE_FIELD = DSL.field(DSL.name("payment_type"), String.class);
    private static final Field<Boolean> IS_PAID_FIELD = DSL.field(DSL.name("is_paid"), Boolean.class);
    private static final Field<LocalDateTime> CANCELLED_AT_FIELD = DSL.field(DSL.name("cancelled_at"), LocalDateTime.class);
    private static final Field<String> CANCEL_REASON_FIELD = DSL.field(DSL.name("cancel_reason"), String.class);
    private static final Field<Integer> VERSION_FIELD = DSL.field(DSL.name("version"), Integer.class);
    private static final Field<Integer> ORDERDISH_SET_ID = DSL.field(DSL.name("set_id"), Integer.class);
    private static final Field<Double> ORDERDISH_UNIT_PRICE = DSL.field(DSL.name("unit_price"), Double.class);
    private static final Field<Double> ORDERDISH_UNIT_COST = DSL.field(DSL.name("unit_cost"), Double.class);
    private static final org.jooq.Table<?> DISH_SET = DSL.table(DSL.name("sales", "dish_set"));
    private static final org.jooq.Table<?> DISH_SET_ITEM = DSL.table(DSL.name("sales", "dish_set_item"));
    private static final Field<Integer> DISH_SET_ID = DSL.field(DSL.name("sales", "dish_set", "setid"), Integer.class);
    private static final Field<String> DISH_SET_NAME = DSL.field(DSL.name("sales", "dish_set", "setname"), String.class);
    private static final Field<Double> DISH_SET_PRICE = DSL.field(DSL.name("sales", "dish_set", "price"), Double.class);
    private static final Field<Double> DISH_SET_FIRST_COST = DSL.field(DSL.name("sales", "dish_set", "first_cost"), Double.class);
    private static final Field<Integer> DISH_SET_ITEM_SET_ID = DSL.field(DSL.name("set_id"), Integer.class);
    private static final Field<Integer> DISH_SET_ITEM_DISH_ID = DSL.field(DSL.name("dish_id"), Integer.class);
    private static final Field<Integer> DISH_SET_ITEM_QTY = DSL.field(DSL.name("qty"), Integer.class);

    private final DSLContext dsl;
    private final WareHouseService wareHouseService;
    private final RecipeRequirementService recipeRequirementService;

    public OrderService(DSLContext dsl, WareHouseService wareHouseService, RecipeRequirementService recipeRequirementService) {
        this.dsl = dsl;
        this.wareHouseService = wareHouseService;
        this.recipeRequirementService = recipeRequirementService;
    }

    @Transactional
    public OrderDTO createOrder(OrderDTO orderDTO) {
        LocalDateTime now = LocalDateTime.now();
        try {
            // Проверяем обязательные поля
            if (orderDTO.getShiftId() == 0) {
                throw new IllegalArgumentException("Shift ID is required");
            }
            lockOpenShift(orderDTO.getShiftId());

            List<ValidatedOrderItem> items = validateOrderItems(orderDTO.getItems());
            boolean delivery = Boolean.TRUE.equals(orderDTO.getType());
            double itemsTotal = items.stream()
                    .map(ValidatedOrderItem::lineTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();
            double requestedTotal = orderDTO.getAmount() != null
                    ? normalizeNonNegative(orderDTO.getAmount(), "Сумма заказа")
                    : itemsTotal;
            double deliveryCost = delivery ? Math.max(0.0, requestedTotal - itemsTotal) : 0.0;
            double serverTotal = BigDecimal.valueOf(itemsTotal)
                    .add(BigDecimal.valueOf(deliveryCost))
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();

            String normalizedPaymentType = normalizePaymentType(orderDTO.getPaymentType());
            boolean paid = orderDTO.getPaid() != null ? orderDTO.getPaid() : false;
            String storedPaymentType = paid ? normalizedPaymentType : "unpaid";

            Integer clientId = orderDTO.getClientId();
            if (clientId != null && clientId == 0) {
                clientId = null;
            }
            var result = dsl.insertInto(ORDER)
                    .set(ORDER.CLIENTID, clientId)
                    .set(ORDER.SHIFTID, orderDTO.getShiftId())
                    .set(ORDER.DATE, orderDTO.getDate() != null ? orderDTO.getDate() : LocalDate.now())
                    .set(ORDER.CREATED_AT, now)
                    .set(ORDER.AMOUNT, serverTotal)
                    .set(ORDER.STATUS, orderDTO.getStatus() != null ? orderDTO.getStatus() : false)
                    .set(ORDER.TYPE, delivery)
                    .set(ORDER.TIME, orderDTO.getTime() != null ? orderDTO.getTime() : 30.0) // время по умолчанию 30 мин
                    .set(ORDER.TIMEDELAY, orderDTO.getTimeDelay()) // может быть null
                    .set(ORDER.DUTY, orderDTO.getDuty())
                    .set(ORDER.DEBT_PAYMENT_DATE, orderDTO.getDebt_payment_date())
                    .set(DELIVERY_PHONE_FIELD, orderDTO.getDeliveryPhone())
                    .set(DELIVERY_ADDRESS_FIELD, orderDTO.getDeliveryAddress())
                    .set(PAYMENT_TYPE_FIELD, storedPaymentType)
                    .set(IS_PAID_FIELD, paid)
                    .returningResult(ORDER.ORDERID)
                    .fetchOne();

            if (result == null) {
                throw new RuntimeException("Failed to create order - no ID returned");
            }

            Integer orderId = result.get(ORDER.ORDERID);
            System.out.println("Created order with ID: " + orderId);

            if (!items.isEmpty()) {
                for (ValidatedOrderItem item : items) {
                    var insert = dsl.insertInto(ORDERDISH)
                            .set(ORDERDISH.ORDERID, orderId)
                            .set(ORDERDISH.QTY, item.qty())
                            .set(ORDERDISH_UNIT_PRICE, item.unitPrice())
                            .set(ORDERDISH_UNIT_COST, item.unitCost());

                    if (item.dishId() != null) {
                        insert.set(ORDERDISH.DISHID, item.dishId());
                    } else {
                        insert.set(ORDERDISH_SET_ID, item.setId());
                    }

                    insert.execute();
                }
                if (paid) {
                    applyWarehouseWriteoffForOrder(orderId);
                }
            }

            // Получаем полный объект заказа
            OrderDTO createdOrder = getOrderById(orderId);


            return createdOrder;

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("Error creating order: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to create order: " + e.getMessage(), e);
        }
    }
    @Transactional
    public Boolean updateOrderStatus(int orderId, Boolean status) {
        OrderRecord record = lockOrderForMutation(orderId);
        ensureOrderActive(orderId);
        record.setStatus(status); // обновляем статус
        record.store();           // сохраняем

        return record.getStatus();
    }

    public OrderDTO getOrderById(int id) {
        return dsl.select(ORDER.fields())
                .select(
                        DELIVERY_PHONE_FIELD,
                        DELIVERY_ADDRESS_FIELD,
                        PAYMENT_TYPE_FIELD,
                        IS_PAID_FIELD,
                        CANCELLED_AT_FIELD,
                        CANCEL_REASON_FIELD,
                        VERSION_FIELD
                )
                .from(ORDER)
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
                    order.time = record.get(ORDER.TIME);
                    order.timeDelay = record.get(ORDER.TIMEDELAY);
                    order.created_at = record.get(ORDER.CREATED_AT);
                    order.date_issue = record.get(ORDER.DATE_ISSUE);
                    order.deliveryPhone = record.get(DELIVERY_PHONE_FIELD);
                    order.deliveryAddress = record.get(DELIVERY_ADDRESS_FIELD);
                    order.paymentType = record.get(PAYMENT_TYPE_FIELD);
                    order.paid = record.get(IS_PAID_FIELD);
                    order.cancelledAt = record.get(CANCELLED_AT_FIELD);
                    order.cancelReason = record.get(CANCEL_REASON_FIELD);
                    order.version = record.get(VERSION_FIELD);
                    order.items = buildOrderDishDtos(record.get(ORDER.ORDERID));
                    return order;
                });
    }


    public List<OrderDTO> getOrdersByClientId(int clientId) {
        return dsl.select(ORDER.fields())
                .select(PAYMENT_TYPE_FIELD, IS_PAID_FIELD)
                .from(ORDER)
                .where(ORDER.CLIENTID.eq(clientId))
                .and(CANCELLED_AT_FIELD.isNull())
                .fetch(record -> {
                    OrderDTO order = new OrderDTO();
                    order.setClientId(record.get(ORDER.CLIENTID));
                    order.setOrderId(record.get(ORDER.ORDERID));
                    order.setDate(record.get(ORDER.DATE));
                    order.setStatus(record.get(ORDER.STATUS));
                    order.setAmount(record.get(ORDER.AMOUNT));
                    order.setShiftId(record.get(ORDER.SHIFTID));
                    order.setType(record.get(ORDER.TYPE));
                    order.setTime(record.get(ORDER.TIME));
                    order.timeDelay = record.get(ORDER.TIMEDELAY);
                    order.setCreated_at(record.get(ORDER.CREATED_AT));
                    order.setDate_issue(record.get(ORDER.DATE_ISSUE));
                    order.setPaymentType(record.get(PAYMENT_TYPE_FIELD));
                    order.setPaid(record.get(IS_PAID_FIELD));
                    return order;
                });
    }
    public List<OrderDTO> getOrdersByDate(LocalDate date) {
        return dsl.selectFrom(ORDER)
                .where(ORDER.DATE.eq(date))
                .and(CANCELLED_AT_FIELD.isNull())
                .fetch(record -> {
                    OrderDTO order = new OrderDTO();
                    order.setClientId(record.get(ORDER.CLIENTID));
                    order.setOrderId(record.get(ORDER.ORDERID));
                    order.setDate(record.get(ORDER.DATE));
                    order.setStatus(record.get(ORDER.STATUS));
                    order.setAmount(record.get(ORDER.AMOUNT));
                    order.setShiftId(record.get(ORDER.SHIFTID));
                    order.setType(record.get(ORDER.TYPE));
                    order.setTime(record.get(ORDER.TIME));
                    order.timeDelay = record.get(ORDER.TIMEDELAY);
                    order.setCreated_at(record.get(ORDER.CREATED_AT));
                    order.setDate_issue(record.get(ORDER.DATE_ISSUE));
                    return order;
                });
    }

    public List<OrderDTO> getOrdersByDateAndClientId(LocalDate date, int id) {
        return dsl.selectFrom(ORDER)
                .where(ORDER.CLIENTID.eq(id)
                        .and(ORDER.DATE.eq(date)))
                .and(CANCELLED_AT_FIELD.isNull())
                .fetch(record -> {
                    OrderDTO order = new OrderDTO();
                    order.setClientId(record.get(ORDER.CLIENTID));
                    order.setOrderId(record.get(ORDER.ORDERID));
                    order.setDate(record.get(ORDER.DATE));
                    order.setStatus(record.get(ORDER.STATUS));
                    order.setAmount(record.get(ORDER.AMOUNT));
                    order.setShiftId(record.get(ORDER.SHIFTID));
                    order.setTime(record.get(ORDER.TIME));
                    order.timeDelay = record.get(ORDER.TIMEDELAY);
                    order.setCreated_at(record.get(ORDER.CREATED_AT));
                    order.setDate_issue(record.get(ORDER.DATE_ISSUE));
                    return order;
                });
    }

    public List<OrderDTO> getOrdersByStatus(Boolean status) {
        return dsl.selectFrom(ORDER)
                .where(ORDER.STATUS.eq(status))
                .and(CANCELLED_AT_FIELD.isNull())
                .fetch(record -> {
                    OrderDTO order = new OrderDTO();
                    order.setClientId(record.get(ORDER.CLIENTID));
                    order.setOrderId(record.get(ORDER.ORDERID));
                    order.setDate(record.get(ORDER.DATE));
                    order.setStatus(record.get(ORDER.STATUS));
                    order.setAmount(record.get(ORDER.AMOUNT));
                    order.setShiftId(record.get(ORDER.SHIFTID));
                    order.setTime(record.get(ORDER.TIME));
                    order.timeDelay = record.get(ORDER.TIMEDELAY);
                    order.setCreated_at(record.get(ORDER.CREATED_AT));
                    order.setDate_issue(record.get(ORDER.DATE_ISSUE));
                    return order;
                });
    }
    public List<OrderDTO> getOrdersByShift(int id) {
        return dsl.selectFrom(ORDER)
                .where(ORDER.SHIFTID.eq(id))
                .and(CANCELLED_AT_FIELD.isNull())
                .fetch(record -> {
                    OrderDTO order = new OrderDTO();
                    order.setClientId(record.get(ORDER.CLIENTID));
                    order.setOrderId(record.get(ORDER.ORDERID));
                    order.setDate(record.get(ORDER.DATE));
                    order.setStatus(record.get(ORDER.STATUS));
                    order.setAmount(record.get(ORDER.AMOUNT));
                    order.setShiftId(record.get(ORDER.SHIFTID));
                    order.setTime(record.get(ORDER.TIME));
                    order.timeDelay = record.get(ORDER.TIMEDELAY);
                    order.setCreated_at(record.get(ORDER.CREATED_AT));
                    order.setDate_issue(record.get(ORDER.DATE_ISSUE));
                    return order;
                });
    }
public List<OrderDTO> getOrders() {
        return dsl.select(ORDER.fields())
                .select(DELIVERY_PHONE_FIELD, DELIVERY_ADDRESS_FIELD, PAYMENT_TYPE_FIELD, IS_PAID_FIELD)
                .from(ORDER)
                .where(CANCELLED_AT_FIELD.isNull())
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
                    order.setType(record.get(ORDER.TYPE));
                    order.setTime(record.get(ORDER.TIME));
                    order.timeDelay = record.get(ORDER.TIMEDELAY);
                    order.setCreated_at(record.get(ORDER.CREATED_AT));
                    order.setDate_issue(record.get(ORDER.DATE_ISSUE));
                    order.setDeliveryPhone(record.get(DELIVERY_PHONE_FIELD));
                    order.setDeliveryAddress(record.get(DELIVERY_ADDRESS_FIELD));
                    order.setPaymentType(record.get(PAYMENT_TYPE_FIELD));
                    order.setPaid(record.get(IS_PAID_FIELD));
                    order.setItems(buildOrderDishDtos(record.get(ORDER.ORDERID)));
                    return order;
                }).toList();
}
    private OrderDTO mapToDTO(OrderRecord record) {
        OrderDTO order = new OrderDTO();
        order.setClientId(record.get(ORDER.CLIENTID));
        order.setOrderId(record.get(ORDER.ORDERID));
        order.setDate(record.get(ORDER.DATE));
        order.setStatus(record.get(ORDER.STATUS));
        order.setAmount(record.get(ORDER.AMOUNT));
        order.setShiftId(record.get(ORDER.SHIFTID));
        order.setTime(record.get(ORDER.TIME));
        order.setTimeDelay(record.get(ORDER.TIMEDELAY));
        order.setCreated_at(record.get(ORDER.CREATED_AT));
        order.setDate_issue(record.get(ORDER.DATE_ISSUE));
        return order;
    }
    @Transactional
    public OrderDTO addTimeDelay(int orderId, Double delayMinutes) {
        lockOrderForMutation(orderId);
        ensureOrderActive(orderId);
        // Обновляем время задержки
        dsl.update(ORDER)
                .set(ORDER.TIMEDELAY, delayMinutes)
                .where(ORDER.ORDERID.eq(orderId))
                .execute();

        // Получаем обновленную запись
        OrderRecord updatedRecord = dsl.selectFrom(ORDER)
                .where(ORDER.ORDERID.eq(orderId))
                .fetchOne();

        return mapToDTO(updatedRecord);
    }

    @Transactional
    public OrderDTO replaceEditableOrder(int orderId, OrderEditRequestDTO request) {
        if (request == null) {
            throw new InvalidOrderRequestException("Не переданы данные заказа");
        }

        OrderRecord order = lockOrderForMutation(orderId);
        ensureEditableOrder(order, request.getExpectedVersion());
        List<ValidatedOrderItem> items = validateOrderItems(request.getItems());

        boolean delivery = request.getType() != null ? request.getType() : Boolean.TRUE.equals(order.getType());
        double deliveryCost = request.getDeliveryCost() != null
                ? normalizeNonNegative(request.getDeliveryCost(), "Стоимость доставки")
                : currentDeliveryCost(orderId, order);
        double itemsTotal = items.stream()
                .map(ValidatedOrderItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
        double total = BigDecimal.valueOf(itemsTotal)
                .add(BigDecimal.valueOf(delivery ? deliveryCost : 0.0))
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();

        Integer clientId = request.getClientId() != null ? request.getClientId() : order.getClientid();
        if (clientId != null && clientId == 0) {
            clientId = null;
        }
        double preparationTime = request.getTime() != null ? request.getTime() : safeDouble(order.getTime(), 30.0);
        double delay = request.getTimeDelay() != null ? request.getTimeDelay() : safeDouble(order.getTimedelay(), 0.0);
        if (!Double.isFinite(preparationTime) || preparationTime < 0) {
            throw new InvalidOrderRequestException("Время приготовления должно быть неотрицательным числом");
        }
        if (!Double.isFinite(delay) || delay < 0) {
            throw new InvalidOrderRequestException("Задержка должна быть неотрицательным числом");
        }

        int currentVersion = currentOrderVersion(orderId);
        dsl.update(ORDER)
                .set(ORDER.CLIENTID, clientId)
                .set(ORDER.TYPE, delivery)
                .set(ORDER.TIME, preparationTime)
                .set(ORDER.TIMEDELAY, delay)
                .set(ORDER.AMOUNT, total)
                .set(DELIVERY_PHONE_FIELD, trimToNull(request.getDeliveryPhone()))
                .set(DELIVERY_ADDRESS_FIELD, trimToNull(request.getDeliveryAddress()))
                .set(VERSION_FIELD, currentVersion + 1)
                .where(ORDER.ORDERID.eq(orderId))
                .execute();

        dsl.deleteFrom(ORDERDISH)
                .where(ORDERDISH.ORDERID.eq(orderId))
                .execute();
        for (ValidatedOrderItem item : items) {
            var insert = dsl.insertInto(ORDERDISH)
                    .set(ORDERDISH.ORDERID, orderId)
                    .set(ORDERDISH.QTY, item.qty())
                    .set(ORDERDISH_UNIT_PRICE, item.unitPrice())
                    .set(ORDERDISH_UNIT_COST, item.unitCost());
            if (item.dishId() != null) {
                insert.set(ORDERDISH.DISHID, item.dishId());
            } else {
                insert.set(ORDERDISH_SET_ID, item.setId());
            }
            insert.execute();
        }

        return getOrderById(orderId);
    }

    @Transactional
    public OrderDTO cancelOrder(int orderId, String reason, Integer expectedVersion) {
        OrderRecord order = lockOrderForMutation(orderId);
        LocalDateTime cancelledAt = dsl.select(CANCELLED_AT_FIELD)
                .from(ORDER)
                .where(ORDER.ORDERID.eq(orderId))
                .fetchOne(CANCELLED_AT_FIELD);
        if (cancelledAt != null) {
            return getOrderById(orderId);
        }

        ensureEditableOrder(order, expectedVersion);
        String normalizedReason = trimToNull(reason);
        if (normalizedReason != null && normalizedReason.length() > 500) {
            throw new InvalidOrderRequestException("Причина отмены не должна превышать 500 символов");
        }

        int currentVersion = currentOrderVersion(orderId);
        dsl.update(ORDER)
                .set(CANCELLED_AT_FIELD, LocalDateTime.now())
                .set(CANCEL_REASON_FIELD, normalizedReason != null ? normalizedReason : "Отменён пользователем")
                .set(VERSION_FIELD, currentVersion + 1)
                .where(ORDER.ORDERID.eq(orderId))
                .execute();
        return getOrderById(orderId);
    }

    @Transactional
    public OrderDTO markOrderIssued(int orderId) {
        lockOrderForMutation(orderId);
        ensureOrderActive(orderId);
        int updated = dsl.update(ORDER)
                .set(ORDER.DATE_ISSUE, LocalDate.now())
                .where(ORDER.ORDERID.eq(orderId))
                .execute();
        if (updated != 1) {
            throw new RuntimeException("Заказ с id " + orderId + " не найден");
        }
        return getOrderById(orderId);
    }

    @Transactional
    public OrderDTO updateOrderPayment(int orderId, String paymentType, Boolean paid) {
        OrderRecord record = lockOrderForMutation(orderId);
        ensureOrderActive(orderId);

        String normalizedPaymentType = normalizePaymentType(paymentType);
        boolean nextPaid = paid != null ? paid : !"unpaid".equals(normalizedPaymentType);
        boolean wasPaid = Boolean.TRUE.equals(record.get(IS_PAID_FIELD));

        dsl.update(ORDER)
                .set(PAYMENT_TYPE_FIELD, nextPaid ? normalizedPaymentType : "unpaid")
                .set(IS_PAID_FIELD, nextPaid)
                .where(ORDER.ORDERID.eq(orderId))
                .execute();

        if (!wasPaid && nextPaid) {
            applyWarehouseWriteoffForOrder(orderId);
        }

        return getOrderById(orderId);
    }

    @Transactional
    public void addDishToOrder(int orderId, int dishId, int qty) {
        System.out.println("addDishToOrder вызван с параметрами: orderId=" + orderId + ", dishId=" + dishId + ", qty=" + qty);

        lockOrderForMutation(orderId);
        ensureOrderActive(orderId);
        boolean paid = isOrderPaid(orderId);
        OrderDishDTO requestedItem = new OrderDishDTO();
        requestedItem.setDishID(dishId);
        requestedItem.setQty(qty);
        ValidatedOrderItem item = validateOrderItems(List.of(requestedItem)).get(0);

        dsl.insertInto(ORDERDISH)
                .set(ORDERDISH.ORDERID, orderId)
                .set(ORDERDISH.DISHID, dishId)
                .set(ORDERDISH.QTY, qty)
                .set(ORDERDISH_UNIT_PRICE, item.unitPrice())
                .set(ORDERDISH_UNIT_COST, item.unitCost())
                .execute();
        if (paid) {
            applyWarehouseWriteoffForDish(dishId, qty);
        }
        System.out.println("Блюдо добавлено в заказ: " + orderId + ", " + dishId + ", qty=" + qty);
    }

    private boolean isOrderPaid(int orderId) {
        Record record = dsl.select(IS_PAID_FIELD, PAYMENT_TYPE_FIELD)
                .from(ORDER)
                .where(ORDER.ORDERID.eq(orderId))
                .fetchOne();
        if (record == null) return false;
        Boolean paid = record.get(IS_PAID_FIELD);
        String type = record.get(PAYMENT_TYPE_FIELD);
        if (Boolean.TRUE.equals(paid)) return true;
        String normalized = normalizePaymentType(type);
        return "cash".equals(normalized) || "transfer".equals(normalized);
    }

    private void applyWarehouseWriteoffForOrder(int orderId) {
        Integer mainWarehouseId = wareHouseService.getMainWarehouseId();
        if (mainWarehouseId == null) return;

        Map<Integer, Double> requiredByProduct = new HashMap<>();
        Map<Integer, Double> requiredByPreparation = new HashMap<>();
        var rows = dsl.select(ORDERDISH.DISHID, ORDERDISH_SET_ID, ORDERDISH.QTY)
                .from(ORDERDISH)
                .where(ORDERDISH.ORDERID.eq(orderId))
                .fetch();
        if (rows.isEmpty()) return;

        for (Record r : rows) {
            Integer dishId = r.get(ORDERDISH.DISHID);
            Integer setId = r.get(ORDERDISH_SET_ID);
            Integer qty = r.get(ORDERDISH.QTY);
            if (qty == null || qty <= 0) continue;
            RecipeRequirementService.RequirementSet set;
            if (dishId != null && dishId > 0) {
                set = buildRequirementsForDish(dishId, qty);
            } else if (setId != null && setId > 0) {
                set = buildRequirementsForSet(setId, qty);
            } else {
                continue;
            }
            mergeRequirements(requiredByProduct, set.productRequirements());
            mergeRequirements(requiredByPreparation, set.preparationRequirements());
        }

        applyWarehouseWriteoffForRequirements(mainWarehouseId, requiredByProduct, requiredByPreparation);
    }

    private void applyWarehouseWriteoffForDish(int dishId, int qty) {
        if (qty <= 0) return;
        Integer mainWarehouseId = wareHouseService.getMainWarehouseId();
        if (mainWarehouseId == null) return;
        RecipeRequirementService.RequirementSet set = buildRequirementsForDish(dishId, qty);
        applyWarehouseWriteoffForRequirements(mainWarehouseId, set.productRequirements(), set.preparationRequirements());
    }

    private RecipeRequirementService.RequirementSet buildRequirementsForDish(int dishId, int qty) {
        return recipeRequirementService.buildForDish(dishId, qty);
    }

    private RecipeRequirementService.RequirementSet buildRequirementsForSet(int setId, int orderQty) {
        Map<Integer, Double> requiredByProduct = new HashMap<>();
        Map<Integer, Double> requiredByPreparation = new HashMap<>();

        var rows = dsl.select(DISH_SET_ITEM_DISH_ID, DISH_SET_ITEM_QTY)
                .from(DISH_SET_ITEM)
                .where(DISH_SET_ITEM_SET_ID.eq(setId))
                .fetch();

        for (Record row : rows) {
            Integer dishId = row.get(DISH_SET_ITEM_DISH_ID);
            Integer setDishQty = row.get(DISH_SET_ITEM_QTY);
            if (dishId == null || dishId <= 0 || setDishQty == null || setDishQty <= 0) {
                continue;
            }
            RecipeRequirementService.RequirementSet nested = buildRequirementsForDish(dishId, orderQty * setDishQty);
            mergeRequirements(requiredByProduct, nested.productRequirements());
            mergeRequirements(requiredByPreparation, nested.preparationRequirements());
        }

        return new RecipeRequirementService.RequirementSet(requiredByProduct, requiredByPreparation);
    }

    private void mergeRequirements(Map<Integer, Double> target, Map<Integer, Double> add) {
        for (Map.Entry<Integer, Double> e : add.entrySet()) {
            target.merge(e.getKey(), e.getValue(), Double::sum);
        }
    }

    private void applyWarehouseWriteoffForRequirements(
            Integer warehouseId,
            Map<Integer, Double> requiredByProduct,
            Map<Integer, Double> requiredByPreparation
    ) {
        if (warehouseId == null) return;
        boolean noProducts = requiredByProduct == null || requiredByProduct.isEmpty();
        boolean noPreparations = requiredByPreparation == null || requiredByPreparation.isEmpty();
        if (noProducts && noPreparations) return;

        for (Map.Entry<Integer, Double> e : (requiredByProduct != null ? requiredByProduct.entrySet() : java.util.Collections.<Map.Entry<Integer, Double>>emptySet())) {
            Integer productId = e.getKey();
            double required = e.getValue() != null ? e.getValue() : 0.0;
            if (required <= 0) continue;
            wareHouseService.consumeAvailableQuantity(warehouseId, productId, required);
        }

        for (Map.Entry<Integer, Double> e : (requiredByPreparation != null ? requiredByPreparation.entrySet() : java.util.Collections.<Map.Entry<Integer, Double>>emptySet())) {
            Integer preparationId = e.getKey();
            double required = e.getValue() != null ? e.getValue() : 0.0;
            if (required <= 0) continue;
            wareHouseService.consumeAvailablePreparationQuantity(warehouseId, preparationId, required);
        }
    }

    private List<OrderLineItem> loadOrderLineItems(int orderId) {
        Field<Double> dishPriceField = DISH.PRICE.as("dish_price");
        Field<Double> setPriceField = DISH_SET_PRICE.as("set_price");
        Field<String> setNameField = DISH_SET_NAME.as("set_name");

        return dsl.select(
                        ORDERDISH.DISHID,
                        ORDERDISH_SET_ID,
                        ORDERDISH.QTY,
                        ORDERDISH_UNIT_PRICE,
                        DISH.DISHNAME,
                        dishPriceField,
                        setNameField,
                        setPriceField
                )
                .from(ORDERDISH)
                .leftJoin(DISH).on(DISH.DISHID.eq(ORDERDISH.DISHID))
                .leftJoin(DISH_SET).on(DISH_SET_ID.eq(ORDERDISH_SET_ID))
                .where(ORDERDISH.ORDERID.eq(orderId))
                .fetch(record -> {
                    Integer dishId = record.get(ORDERDISH.DISHID);
                    Integer setId = record.get(ORDERDISH_SET_ID);
                    Integer qty = record.get(ORDERDISH.QTY);
                    String name = dishId != null && dishId > 0
                            ? record.get(DISH.DISHNAME)
                            : record.get(setNameField);
                    Double price = record.get(ORDERDISH_UNIT_PRICE);
                    if (price == null) {
                        price = dishId != null && dishId > 0
                                ? record.get(dishPriceField)
                                : record.get(setPriceField);
                    }
                    return new OrderLineItem(
                            dishId,
                            setId,
                            name != null ? name : "Позиция",
                            price != null ? price : 0.0,
                            qty != null ? qty : 0
                    );
                });
    }

    private List<OrderDishDTO> buildOrderDishDtos(int orderId) {
        return loadOrderLineItems(orderId).stream().map(row -> {
            OrderDishDTO item = new OrderDishDTO();
            item.setDishID(row.dishId);
            item.setSetId(row.setId);
            item.setItemType(row.setId != null && row.setId > 0 ? "set" : "dish");
            item.setQty(row.qty != null ? row.qty : 0);
            item.setDishName(row.name != null ? row.name : "Позиция");
            item.setName(row.name != null ? row.name : "Позиция");
            item.setPrice(row.price != null ? row.price : 0.0);
            item.setSum((row.price != null ? row.price : 0.0) * (row.qty != null ? row.qty : 0));
            return item;
        }).toList();
    }

    private double calculateOrderItemsFirstCost(int orderId) {
        double total = 0.0;
        var rows = dsl.select(
                        ORDERDISH.DISHID,
                        ORDERDISH_SET_ID,
                        ORDERDISH.QTY,
                        ORDERDISH_UNIT_COST,
                        DISH.FIRSTCOST,
                        DISH_SET_FIRST_COST
                )
                .from(ORDERDISH)
                .leftJoin(DISH).on(DISH.DISHID.eq(ORDERDISH.DISHID))
                .leftJoin(DISH_SET).on(DISH_SET_ID.eq(ORDERDISH_SET_ID))
                .where(ORDERDISH.ORDERID.eq(orderId))
                .fetch();

        for (Record row : rows) {
            int qty = row.get(ORDERDISH.QTY) != null ? row.get(ORDERDISH.QTY) : 0;
            if (qty <= 0) continue;
            Integer dishId = row.get(ORDERDISH.DISHID);
            Double storedUnitCost = row.get(ORDERDISH_UNIT_COST);
            double firstCost = storedUnitCost != null
                    ? storedUnitCost
                    : dishId != null && dishId > 0
                            ? (row.get(DISH.FIRSTCOST) != null ? row.get(DISH.FIRSTCOST) : 0.0)
                            : (row.get(DISH_SET_FIRST_COST) != null ? row.get(DISH_SET_FIRST_COST) : 0.0);
            total += firstCost * qty;
        }
        return total;
    }

    private static class OrderLineItem {
        final Integer dishId;
        final Integer setId;
        final String name;
        final Double price;
        final Integer qty;

        OrderLineItem(Integer dishId, Integer setId, String name, Double price, Integer qty) {
            this.dishId = dishId;
            this.setId = setId;
            this.name = name;
            this.price = price;
            this.qty = qty;
        }
    }

    public Map<String, Object> getOrderKitchenPrintPayload(
            int orderId,
            String paymentType,
            Double deliveryCostOverride,
            String deliveryPhone,
            String deliveryAddress
    ) {
        OrderRecord order = dsl.selectFrom(ORDER)
                .where(ORDER.ORDERID.eq(orderId))
                .fetchOne();

        if (order == null) {
            throw new RuntimeException("Заказ с id " + orderId + " не найден");
        }
        ensureOrderActive(orderId);

        List<OrderLineItem> rows = loadOrderLineItems(orderId);
        if (rows.isEmpty()) {
            throw new RuntimeException("В заказе нет позиций для печати");
        }

        List<Map<String, Object>> items = rows.stream().map(r -> {
            String name = r.name != null ? r.name : "Позиция";
            Integer qty = r.qty != null ? r.qty : 0;
            Double price = r.price != null ? r.price : 0.0;
            double sum = qty * price;

            Map<String, Object> item = new HashMap<>();
            item.put("name", name);
            item.put("quantity", qty);
            item.put("price", price);
            item.put("sum", sum);
            return item;
        }).toList();

        double computedTotal = items.stream()
                .mapToDouble(i -> ((Number) i.get("sum")).doubleValue())
                .sum();

        double itemsTotal = computedTotal;
        double total = order.getAmount() != null && order.getAmount() > 0
                ? order.getAmount()
                : computedTotal;
        boolean isDelivery = Boolean.TRUE.equals(order.getType());
        double deliveryCost = 0.0;
        Record deliveryData = dsl.select(DELIVERY_PHONE_FIELD, DELIVERY_ADDRESS_FIELD)
                .from(ORDER)
                .where(ORDER.ORDERID.eq(orderId))
                .fetchOne();
        String dbDeliveryPhone = deliveryData != null ? deliveryData.get(DELIVERY_PHONE_FIELD) : null;
        String dbDeliveryAddress = deliveryData != null ? deliveryData.get(DELIVERY_ADDRESS_FIELD) : null;
        Record paymentData = dsl.select(PAYMENT_TYPE_FIELD, IS_PAID_FIELD)
                .from(ORDER)
                .where(ORDER.ORDERID.eq(orderId))
                .fetchOne();
        String dbPaymentType = paymentData != null ? paymentData.get(PAYMENT_TYPE_FIELD) : null;
        Boolean dbIsPaid = paymentData != null ? paymentData.get(IS_PAID_FIELD) : null;
        if (isDelivery) {
            deliveryCost = Math.max(0.0, total - itemsTotal);
            if (deliveryCostOverride != null && deliveryCostOverride >= 0) {
                deliveryCost = deliveryCostOverride;
            }
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", orderId);
        payload.put("createdAt", order.getCreatedAt() != null ? order.getCreatedAt().toString() : null);
        payload.put("items", items);
        payload.put("total", total);
        payload.put("isDelivery", isDelivery);
        payload.put("deliveryCost", deliveryCost);
        String resolvedPayment = paymentType != null && !paymentType.trim().isEmpty()
                ? normalizePaymentType(paymentType)
                : normalizePaymentType(dbPaymentType);
        if (Boolean.FALSE.equals(dbIsPaid) && (paymentType == null || paymentType.trim().isEmpty())) {
            resolvedPayment = "unpaid";
        }
        payload.put("paymentType", resolvedPayment);
        payload.put(
                "deliveryPhone",
                deliveryPhone != null && !deliveryPhone.trim().isEmpty()
                        ? deliveryPhone.trim()
                        : dbDeliveryPhone
        );
        payload.put(
                "deliveryAddress",
                deliveryAddress != null && !deliveryAddress.trim().isEmpty()
                        ? deliveryAddress.trim()
                        : dbDeliveryAddress
        );
        return payload;
    }

    private String normalizePaymentType(String value) {
        String raw = value == null ? "" : value.trim().toLowerCase();
        return switch (raw) {
            case "cash", "transfer", "unpaid" -> raw;
            default -> "cash";
        };
    }

    private OrderRecord lockOrder(int orderId) {
        OrderRecord order = dsl.selectFrom(ORDER)
                .where(ORDER.ORDERID.eq(orderId))
                .forUpdate()
                .fetchOne();
        if (order == null) {
            throw new OrderNotFoundException(orderId);
        }
        return order;
    }

    private OrderRecord lockOrderForMutation(int orderId) {
        Integer shiftId = dsl.select(ORDER.SHIFTID)
                .from(ORDER)
                .where(ORDER.ORDERID.eq(orderId))
                .fetchOne(ORDER.SHIFTID);
        if (shiftId == null) {
            throw new OrderNotFoundException(orderId);
        }
        lockOpenShift(shiftId);
        return lockOrder(orderId);
    }

    private void lockOpenShift(int shiftId) {
        var shift = dsl.select(Shift.SHIFT.ID, Shift.SHIFT.ENDTIME)
                .from(Shift.SHIFT)
                .where(Shift.SHIFT.ID.eq(shiftId))
                .forShare()
                .fetchOne();
        if (shift == null) {
            throw new OrderStateConflictException("Смена заказа не найдена");
        }
        if (shift.get(Shift.SHIFT.ENDTIME) != null) {
            throw new OrderStateConflictException(
                    "Смена уже закрыта; заказы закрытой смены нельзя создавать или изменять"
            );
        }
    }

    private void ensureEditableOrder(OrderRecord order, Integer expectedVersion) {
        int orderId = order.getOrderid();
        ensureOrderActive(orderId);
        if (Boolean.TRUE.equals(order.getIsPaid())) {
            throw new OrderStateConflictException(
                    "Оплаченный заказ нельзя редактировать или отменять без оформления возврата"
            );
        }
        if (order.getDateIssue() != null) {
            throw new OrderStateConflictException("Выданный заказ нельзя редактировать или отменять");
        }

        var shift = dsl.select(Shift.SHIFT.ENDTIME)
                .from(Shift.SHIFT)
                .where(Shift.SHIFT.ID.eq(order.getShiftid()))
                .fetchOne();
        if (shift == null) {
            throw new OrderStateConflictException("Смена заказа не найдена");
        }
        if (shift.get(Shift.SHIFT.ENDTIME) != null) {
            throw new OrderStateConflictException("Заказ закрытой смены нельзя редактировать или отменять");
        }

        int currentVersion = currentOrderVersion(orderId);
        if (expectedVersion != null && expectedVersion != currentVersion) {
            throw new OrderStateConflictException(
                    "Заказ уже был изменён. Обновите данные и повторите операцию"
            );
        }
    }

    private void ensureOrderActive(int orderId) {
        LocalDateTime cancelledAt = dsl.select(CANCELLED_AT_FIELD)
                .from(ORDER)
                .where(ORDER.ORDERID.eq(orderId))
                .fetchOne(CANCELLED_AT_FIELD);
        if (cancelledAt != null) {
            throw new OrderStateConflictException("Заказ уже отменён");
        }
    }

    private int currentOrderVersion(int orderId) {
        Integer version = dsl.select(VERSION_FIELD)
                .from(ORDER)
                .where(ORDER.ORDERID.eq(orderId))
                .fetchOne(VERSION_FIELD);
        return version != null ? version : 0;
    }

    private List<ValidatedOrderItem> validateOrderItems(List<OrderDishDTO> requestedItems) {
        if (requestedItems == null || requestedItems.isEmpty()) {
            throw new InvalidOrderRequestException("Заказ должен содержать хотя бы одну позицию");
        }
        if (requestedItems.size() > 200) {
            throw new InvalidOrderRequestException("В заказе не может быть больше 200 позиций");
        }

        java.util.ArrayList<ValidatedOrderItem> result = new java.util.ArrayList<>();
        for (OrderDishDTO requested : requestedItems) {
            if (requested == null) {
                throw new InvalidOrderRequestException("Позиция заказа не может быть пустой");
            }
            Integer dishId = requested.getDishID();
            Integer setId = requested.getSetId();
            boolean hasDish = dishId != null && dishId > 0;
            boolean hasSet = setId != null && setId > 0;
            if (hasDish == hasSet) {
                throw new InvalidOrderRequestException(
                        "Для каждой позиции нужно указать либо dishID, либо setId"
                );
            }
            if (requested.getQty() <= 0 || requested.getQty() > 1000) {
                throw new InvalidOrderRequestException("Количество позиции должно быть от 1 до 1000");
            }

            Double price;
            Double firstCost;
            if (hasDish) {
                var dish = dsl.select(DISH.PRICE, DISH.FIRSTCOST)
                        .from(DISH)
                        .where(DISH.DISHID.eq(dishId))
                        .fetchOne();
                if (dish == null) {
                    throw new InvalidOrderRequestException("Блюдо не найдено: " + dishId);
                }
                price = dish.get(DISH.PRICE);
                firstCost = dish.get(DISH.FIRSTCOST);
            } else {
                var set = dsl.select(DISH_SET_PRICE, DISH_SET_FIRST_COST)
                        .from(DISH_SET)
                        .where(DISH_SET_ID.eq(setId))
                        .fetchOne();
                if (set == null) {
                    throw new InvalidOrderRequestException("Набор не найден: " + setId);
                }
                price = set.get(DISH_SET_PRICE);
                firstCost = set.get(DISH_SET_FIRST_COST);
            }

            double normalizedPrice = price != null && Double.isFinite(price) && price >= 0 ? price : 0.0;
            double normalizedFirstCost = firstCost != null
                    && Double.isFinite(firstCost)
                    && firstCost >= 0
                    ? firstCost
                    : 0.0;
            BigDecimal lineTotal = BigDecimal.valueOf(normalizedPrice)
                    .multiply(BigDecimal.valueOf(requested.getQty()));
            result.add(new ValidatedOrderItem(
                    hasDish ? dishId : null,
                    hasSet ? setId : null,
                    requested.getQty(),
                    normalizedPrice,
                    normalizedFirstCost,
                    lineTotal
            ));
        }
        return result;
    }

    private double currentDeliveryCost(int orderId, OrderRecord order) {
        if (!Boolean.TRUE.equals(order.getType())) {
            return 0.0;
        }
        double itemsTotal = loadOrderLineItems(orderId).stream()
                .mapToDouble(item -> safeDouble(item.price, 0.0) * (item.qty != null ? item.qty : 0))
                .sum();
        return Math.max(0.0, safeDouble(order.getAmount(), 0.0) - itemsTotal);
    }

    private double normalizeNonNegative(Double value, String fieldName) {
        if (value == null) {
            return 0.0;
        }
        if (!Double.isFinite(value) || value < 0) {
            throw new InvalidOrderRequestException(fieldName + " должна быть неотрицательным числом");
        }
        return value;
    }

    private double safeDouble(Double value, double fallback) {
        return value != null && Double.isFinite(value) ? value : fallback;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record ValidatedOrderItem(
            Integer dishId,
            Integer setId,
            int qty,
            double unitPrice,
            double unitCost,
            BigDecimal lineTotal
    ) {
    }

}
