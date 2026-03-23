package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.OrderDTO;
import com.shakur.cafehelp.DTO.OrderDishDTO;
import jooqdata.tables.Order;
import jooqdata.tables.Orderdish;
import jooqdata.tables.Dish;
import jooqdata.tables.records.OrderRecord;
import jooqdata.tables.records.OrderdishRecord;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private static final Field<Integer> ORDERDISH_SET_ID = DSL.field(DSL.name("set_id"), Integer.class);
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
    private static final org.jooq.Table<?> PRODUCT = DSL.table(DSL.name("sales", "product"));
    private static final Field<Integer> PRODUCT_ID = DSL.field(DSL.name("productid"), Integer.class);
    private static final Field<java.math.BigDecimal> PRODUCT_UNIT_FACTOR = DSL.field(DSL.name("unit_factor"), java.math.BigDecimal.class);
    private static final Field<String> PRODUCT_NAME = DSL.field(DSL.name("productname"), String.class);
    private static final Field<String> PRODUCT_BASE_UNIT = DSL.field(DSL.name("base_unit"), String.class);
    private volatile Boolean baseUnitPresent = null;

    public OrderService(DSLContext dsl, WareHouseService wareHouseService, RecipeRequirementService recipeRequirementService) {
        this.dsl = dsl;
        this.wareHouseService = wareHouseService;
        this.recipeRequirementService = recipeRequirementService;
    }

    public OrderDTO createOrder(OrderDTO orderDTO) {
        LocalDateTime now = LocalDateTime.now();
        try {
            // Проверяем обязательные поля
            if (orderDTO.getShiftId() == 0) {
                throw new IllegalArgumentException("Shift ID is required");
            }

            // Создаем запись заказа
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
                    .set(ORDER.AMOUNT, orderDTO.getAmount() != null ? orderDTO.getAmount() : 0.0)
                    .set(ORDER.STATUS, orderDTO.getStatus() != null ? orderDTO.getStatus() : false)
                    .set(ORDER.TYPE, orderDTO.getType() != null ? orderDTO.getType() : false)
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

            if (orderDTO.getItems() != null) {
                for (var item : orderDTO.getItems()) {
                    if (item == null) continue;
                    int qty = item.getQty();
                    Integer dishId = item.getDishID();
                    Integer setId = item.getSetId();
                    if (qty <= 0) continue;
                    if ((dishId == null || dishId <= 0) && (setId == null || setId <= 0)) continue;

                    var insert = dsl.insertInto(ORDERDISH)
                            .set(ORDERDISH.ORDERID, orderId)
                            .set(ORDERDISH.QTY, qty);

                    if (dishId != null && dishId > 0) {
                        insert.set(ORDERDISH.DISHID, dishId);
                    } else {
                        insert.set(ORDERDISH_SET_ID, setId);
                    }

                    insert.execute();
                }
                if (paid) {
                    try {
                        applyWarehouseWriteoffForOrder(orderId);
                    } catch (RuntimeException e) {
                        System.err.println("Warehouse writeoff failed for order " + orderId + ": " + e.getMessage());
                    }
                }
            }

            // Получаем полный объект заказа
            OrderDTO createdOrder = getOrderById(orderId);


            return createdOrder;

        } catch (Exception e) {
            System.err.println("Error creating order: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to create order: " + e.getMessage(), e);
        }
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
        return dsl.select(ORDER.fields())
                .select(DELIVERY_PHONE_FIELD, DELIVERY_ADDRESS_FIELD, PAYMENT_TYPE_FIELD, IS_PAID_FIELD)
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
                    order.items = buildOrderDishDtos(record.get(ORDER.ORDERID));
                    return order;
                });
    }


    public List<OrderDTO> getOrdersByClientId(int clientId) {
        return dsl.select(ORDER.fields())
                .select(PAYMENT_TYPE_FIELD, IS_PAID_FIELD)
                .from(ORDER)
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
    public OrderDTO addTimeDelay(int orderId, Double delayMinutes) {
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
    public OrderDTO markOrderIssued(int orderId) {
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
        OrderRecord record = dsl.selectFrom(ORDER)
                .where(ORDER.ORDERID.eq(orderId))
                .fetchOne();
        if (record == null) {
            throw new RuntimeException("Заказ с id " + orderId + " не найден");
        }

        String normalizedPaymentType = normalizePaymentType(paymentType);
        boolean nextPaid = paid != null ? paid : !"unpaid".equals(normalizedPaymentType);
        boolean wasPaid = Boolean.TRUE.equals(record.get(IS_PAID_FIELD));

        dsl.update(ORDER)
                .set(PAYMENT_TYPE_FIELD, nextPaid ? normalizedPaymentType : "unpaid")
                .set(IS_PAID_FIELD, nextPaid)
                .where(ORDER.ORDERID.eq(orderId))
                .execute();

        if (!wasPaid && nextPaid) {
            try {
                applyWarehouseWriteoffForOrder(orderId);
            } catch (RuntimeException e) {
                System.err.println("Warehouse writeoff failed for order " + orderId + ": " + e.getMessage());
            }
        }

        return getOrderById(orderId);
    }

    @Transactional
    public void addDishToOrder(int orderId, int dishId, int qty) {
        System.out.println("addDishToOrder вызван с параметрами: orderId=" + orderId + ", dishId=" + dishId + ", qty=" + qty);

        boolean paid = isOrderPaid(orderId);
        if (paid) {
            try {
                applyWarehouseWriteoffForDish(dishId, qty);
            } catch (RuntimeException e) {
                System.err.println("Warehouse writeoff failed for dish " + dishId + ": " + e.getMessage());
            }
        }

        OrderdishRecord record = dsl.newRecord(Orderdish.ORDERDISH);
        record.setOrderid(orderId);
        record.setDishid(dishId);
        record.setQty(qty);
        record.store(); // безопаснее execute()
        System.out.println("Блюдо добавлено в заказ: " + record.getOrderid() + ", " + record.getDishid() + ", qty=" + record.getQty());
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

        Map<Integer, ProductInfo> productInfo = loadProductInfo(
                requiredByProduct != null ? requiredByProduct.keySet() : java.util.Set.of()
        );
        Map<Integer, PreparationInfo> preparationInfo = loadPreparationInfo(
                requiredByPreparation != null ? requiredByPreparation.keySet() : java.util.Set.of()
        );
        List<String> missing = new java.util.ArrayList<>();

        for (Map.Entry<Integer, Double> e : (requiredByProduct != null ? requiredByProduct.entrySet() : java.util.Collections.<Map.Entry<Integer, Double>>emptySet())) {
            Integer productId = e.getKey();
            double required = e.getValue() != null ? e.getValue() : 0.0;
            if (required <= 0) continue;

            double available = wareHouseService.getAvailableQuantity(warehouseId, productId);
            if (available + 1e-6 < required) {
                ProductInfo info = productInfo.get(productId);
                String name = info != null && info.name != null ? info.name : ("ID " + productId);
                String unit = info != null && info.unit != null ? info.unit : "g";
                missing.add(name + " (" + formatQty(available) + "/" + formatQty(required) + " " + unit + ")");
            }
        }

        for (Map.Entry<Integer, Double> e : (requiredByPreparation != null ? requiredByPreparation.entrySet() : java.util.Collections.<Map.Entry<Integer, Double>>emptySet())) {
            Integer preparationId = e.getKey();
            double required = e.getValue() != null ? e.getValue() : 0.0;
            if (required <= 0) continue;

            double available = wareHouseService.getAvailablePreparationQuantity(warehouseId, preparationId);
            if (available + 1e-6 < required) {
                PreparationInfo info = preparationInfo.get(preparationId);
                String name = info != null && info.name != null ? info.name : ("Заготовка ID " + preparationId);
                missing.add(name + " (" + formatQty(available) + "/" + formatQty(required) + " g)");
            }
        }

        if (!missing.isEmpty()) {
            throw new RuntimeException("Не хватает продуктов на складе: " + String.join(", ", missing));
        }

        for (Map.Entry<Integer, Double> e : (requiredByProduct != null ? requiredByProduct.entrySet() : java.util.Collections.<Map.Entry<Integer, Double>>emptySet())) {
            Integer productId = e.getKey();
            double required = e.getValue() != null ? e.getValue() : 0.0;
            if (required <= 0) continue;
            boolean ok = wareHouseService.adjustQuantity(warehouseId, productId, -required);
            if (!ok) {
                throw new RuntimeException("Не удалось списать продукт: ID " + productId);
            }
        }

        for (Map.Entry<Integer, Double> e : (requiredByPreparation != null ? requiredByPreparation.entrySet() : java.util.Collections.<Map.Entry<Integer, Double>>emptySet())) {
            Integer preparationId = e.getKey();
            double required = e.getValue() != null ? e.getValue() : 0.0;
            if (required <= 0) continue;
            boolean ok = wareHouseService.adjustPreparationQuantity(warehouseId, preparationId, -required);
            if (!ok) {
                throw new RuntimeException("Не удалось списать заготовку: ID " + preparationId);
            }
        }
    }

    private Map<Integer, ProductInfo> loadProductInfo(java.util.Set<Integer> productIds) {
        Map<Integer, ProductInfo> result = new HashMap<>();
        if (productIds == null || productIds.isEmpty()) return result;

        boolean hasBaseUnit = hasBaseUnitColumn();
        var query = hasBaseUnit
                ? dsl.select(PRODUCT_ID, PRODUCT_NAME, PRODUCT_BASE_UNIT).from(PRODUCT)
                : dsl.select(PRODUCT_ID, PRODUCT_NAME).from(PRODUCT);

        var rows = query.where(PRODUCT_ID.in(productIds)).fetch();
        for (Record r : rows) {
            Integer id = r.get(PRODUCT_ID);
            if (id == null) continue;
            String name = r.get(PRODUCT_NAME);
            String unit = hasBaseUnit ? r.get(PRODUCT_BASE_UNIT) : null;
            if (unit == null || unit.isBlank()) unit = "g";
            result.put(id, new ProductInfo(name, unit));
        }
        return result;
    }

    private Map<Integer, PreparationInfo> loadPreparationInfo(java.util.Set<Integer> preparationIds) {
        Map<Integer, PreparationInfo> result = new HashMap<>();
        if (preparationIds == null || preparationIds.isEmpty()) return result;

        var rows = dsl.select(RecipeSchema.PREPARATION_ID, RecipeSchema.PREPARATION_NAME)
                .from(RecipeSchema.PREPARATION)
                .where(RecipeSchema.PREPARATION_ID.in(preparationIds))
                .fetch();
        for (Record r : rows) {
            Integer id = r.get(RecipeSchema.PREPARATION_ID);
            if (id == null) continue;
            result.put(id, new PreparationInfo(r.get(RecipeSchema.PREPARATION_NAME)));
        }
        return result;
    }

    private boolean hasBaseUnitColumn() {
        if (baseUnitPresent != null) return baseUnitPresent;
        Integer cnt = dsl.selectCount()
                .from(DSL.table(DSL.name("information_schema", "columns")))
                .where(DSL.field(DSL.name("table_schema"), String.class).eq("sales"))
                .and(DSL.field(DSL.name("table_name"), String.class).eq("product"))
                .and(DSL.field(DSL.name("column_name"), String.class).eq("base_unit"))
                .fetchOne(0, Integer.class);
        baseUnitPresent = cnt != null && cnt > 0;
        return baseUnitPresent;
    }

    private String formatQty(double value) {
        return String.format(java.util.Locale.US, "%.2f", value);
    }

    private static class ProductInfo {
        final String name;
        final String unit;
        ProductInfo(String name, String unit) {
            this.name = name;
            this.unit = unit;
        }
    }

    private static class PreparationInfo {
        final String name;

        PreparationInfo(String name) {
            this.name = name;
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
                    Double price = dishId != null && dishId > 0
                            ? record.get(dishPriceField)
                            : record.get(setPriceField);
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
            double firstCost = dishId != null && dishId > 0
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

}
