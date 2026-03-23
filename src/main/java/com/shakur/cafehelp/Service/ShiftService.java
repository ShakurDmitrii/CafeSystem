package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.DishDTO;
import com.shakur.cafehelp.DTO.ShiftDTO;
import jooqdata.tables.Client;
import jooqdata.tables.Dish;
import jooqdata.tables.Order;
import jooqdata.tables.Orderdish;
import jooqdata.tables.Person;
import jooqdata.tables.Shift;
import jooqdata.tables.Shiftperson;
import jooqdata.tables.records.ShiftRecord;
import org.jooq.Field;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static jooqdata.tables.Dish.DISH;

@Service
public class ShiftService {
    private static final Field<String> PAYMENT_TYPE_FIELD = DSL.field(DSL.name("payment_type"), String.class);
    private static final Field<Boolean> IS_PAID_FIELD = DSL.field(DSL.name("is_paid"), Boolean.class);
    private static final Field<Integer> ORDERDISH_SET_ID = DSL.field(DSL.name("set_id"), Integer.class);
    private static final org.jooq.Table<?> DISH_SET = DSL.table(DSL.name("sales", "dish_set"));
    private static final org.jooq.Field<Integer> DISH_SET_ID = DSL.field(DSL.name("sales", "dish_set", "setid"), Integer.class);
    private static final org.jooq.Field<String> DISH_SET_NAME = DSL.field(DSL.name("sales", "dish_set", "setname"), String.class);
    private static final org.jooq.Field<Double> DISH_SET_PRICE = DSL.field(DSL.name("sales", "dish_set", "price"), Double.class);
    private static final org.jooq.Field<Double> DISH_SET_FIRST_COST = DSL.field(DSL.name("sales", "dish_set", "first_cost"), Double.class);

    private final DSLContext dsl;

    public ShiftService(DSLContext dsl) {
        this.dsl = dsl;
    }

    // Получить все смены
    public List<ShiftDTO> findAllShifts() {
        return dsl.selectFrom(Shift.SHIFT)
                .fetch()
                .stream()
                .map(this::toShiftDTO)
                .toList();
    }

    // Создание новой смены
    public ShiftDTO createShift(ShiftDTO dto) {
        LinkedHashSet<Integer> workerIds = normalizeWorkerIds(dto);
        if (workerIds.isEmpty()) {
            throw new RuntimeException("Выберите хотя бы одного сотрудника");
        }
        validateWorkersAvailable(workerIds, null);

        ShiftRecord record = dsl.newRecord(Shift.SHIFT);
        record.setData(dto.data);
        record.setExpenses(dto.expenses);
        record.setProfit(dto.profit);
        record.setIncome(dto.income); // добавлено
        record.setStarttime(dto.startTime);
        record.setEndtime(dto.endTime);
        record.setPersoncode(workerIds.iterator().next());
        record.store(); // id присвоится автоматически базой

        syncShiftPersons(record.getId(), workerIds, record.getPersoncode());
        return getShiftById(record.getId());
    }

    // Обновление смены
    public ShiftDTO updateShift(int shiftId, ShiftDTO dto) {
        LinkedHashSet<Integer> workerIds = normalizeWorkerIds(dto);
        if (workerIds.isEmpty()) {
            throw new RuntimeException("У смены должен быть хотя бы один сотрудник");
        }
        validateWorkersAvailable(workerIds, shiftId);

        int rows = dsl.update(Shift.SHIFT)
                .set(Shift.SHIFT.DATA, dto.data)
                .set(Shift.SHIFT.EXPENSES, dto.expenses)
                .set(Shift.SHIFT.PROFIT, dto.profit)
                .set(Shift.SHIFT.INCOME, dto.income)
                .set(Shift.SHIFT.STARTTIME, dto.startTime)
                .set(Shift.SHIFT.ENDTIME, dto.endTime)
                .set(Shift.SHIFT.PERSONCODE, workerIds.iterator().next())
                .where(Shift.SHIFT.ID.eq(shiftId))
                .execute();

        if (rows != 1) {
            throw new RuntimeException("Update affected " + rows + " rows");
        }

        syncShiftPersons(shiftId, workerIds, workerIds.iterator().next());
        return getShiftById(shiftId);
    }


    // Получение смены по ID
    public ShiftDTO getShiftById(int id) {
        return dsl.selectFrom(Shift.SHIFT)
                .where(Shift.SHIFT.ID.eq(id))
                .fetchOptional()
                .map(this::toShiftDTO)
                .orElseThrow(() -> new RuntimeException("Shift not found with id: " + id));
    }

    // Открытие смены
    public ShiftRecord openShift(int personCode) {
        ShiftRecord shift = dsl.newRecord(Shift.SHIFT);
        shift.setData(LocalDate.now());
        shift.setPersoncode(personCode);
        shift.setStarttime(LocalTime.now());
        shift.store();
        return shift;
    }

    private List<OrderLineItem> loadOrderLineItems(int orderId) {
        Field<Double> dishPriceField = Dish.DISH.PRICE.as("dish_price");
        Field<Double> dishFirstCostField = Dish.DISH.FIRSTCOST.as("dish_first_cost");
        Field<String> setNameField = DISH_SET_NAME.as("set_name");
        Field<Double> setPriceField = DISH_SET_PRICE.as("set_price");
        Field<Double> setFirstCostField = DISH_SET_FIRST_COST.as("set_first_cost");

        return dsl.select(
                        Orderdish.ORDERDISH.DISHID,
                        ORDERDISH_SET_ID,
                        Orderdish.ORDERDISH.QTY,
                        Dish.DISH.DISHNAME,
                        dishPriceField,
                        dishFirstCostField,
                        setNameField,
                        setPriceField,
                        setFirstCostField
                )
                .from(Orderdish.ORDERDISH)
                .leftJoin(Dish.DISH).on(Dish.DISH.DISHID.eq(Orderdish.ORDERDISH.DISHID))
                .leftJoin(DISH_SET).on(DISH_SET_ID.eq(ORDERDISH_SET_ID))
                .where(Orderdish.ORDERDISH.ORDERID.eq(orderId))
                .fetch(record -> {
                    Integer dishId = record.get(Orderdish.ORDERDISH.DISHID);
                    Integer setId = record.get(ORDERDISH_SET_ID);
                    int qty = record.get(Orderdish.ORDERDISH.QTY) != null ? record.get(Orderdish.ORDERDISH.QTY) : 0;
                    boolean isDish = dishId != null && dishId > 0;
                    return new OrderLineItem(
                            dishId,
                            setId,
                            isDish ? record.get(Dish.DISH.DISHNAME) : record.get(setNameField),
                            isDish ? record.get(dishPriceField) : record.get(setPriceField),
                            isDish ? record.get(dishFirstCostField) : record.get(setFirstCostField),
                            qty
                    );
                });
    }

    private record OrderLineItem(
            Integer dishId,
            Integer setId,
            String name,
            Double price,
            Double firstCost,
            int qty
    ) {}

    // Закрытие смены
    public ShiftRecord closeShift(int shiftId, BigDecimal expenses) {
        // Получаем все заказы за эту смену
        var orders = dsl.selectFrom(Order.ORDER)
                .where(Order.ORDER.SHIFTID.eq(shiftId))
                .fetch();

        Double income = orders.stream()
                .mapToDouble(order -> {
                    Double amount = order.getAmount();
                    if (amount != null && amount > 0) {
                        return amount;
                    }
                    return loadOrderLineItems(order.getOrderid()).stream()
                            .mapToDouble(item -> {
                                double price = item.price() != null ? item.price() : 0.0;
                                return price * item.qty();
                            })
                            .sum();
                })
                .sum();

        Double totalCost = orders.stream()
                .mapToDouble(order -> loadOrderLineItems(order.getOrderid()).stream()
                        .mapToDouble(item -> {
                            double firstCost = item.firstCost() != null ? item.firstCost() : 0.0;
                            return firstCost * item.qty();
                        })
                        .sum())
                .sum();

        BigDecimal profit = BigDecimal.valueOf(income - totalCost - expenses.doubleValue());

        // Обновляем запись смены
        dsl.update(Shift.SHIFT)
                .set(Shift.SHIFT.ENDTIME, LocalTime.now())
                .set(Shift.SHIFT.INCOME, income)
                .set(Shift.SHIFT.EXPENSES, expenses)
                .set(Shift.SHIFT.PROFIT, profit)
                .where(Shift.SHIFT.ID.eq(shiftId))
                .execute();

        return dsl.fetchOne(Shift.SHIFT, Shift.SHIFT.ID.eq(shiftId));
    }

    public List<DishDTO> getDishesByOrderId(int orderId) {
        return loadOrderLineItems(orderId).stream()
                .map(item -> {
                    DishDTO dto = new DishDTO();
                    dto.dishId = item.dishId() != null ? item.dishId() : 0;
                    dto.dishName = item.name();
                    dto.price = item.price();
                    dto.firstCost = item.firstCost();
                    dto.qty = item.qty();
                    return dto;
                })
                .toList();
    }

    public Map<String, Object> buildZReport(int shiftId) {
        ShiftRecord shift = dsl.selectFrom(Shift.SHIFT)
                .where(Shift.SHIFT.ID.eq(shiftId))
                .fetchOne();
        if (shift == null) {
            throw new RuntimeException("Смена с id " + shiftId + " не найдена");
        }

        Set<String> workers = new LinkedHashSet<>();
        if (shift.getPersoncode() != null) {
            String mainWorker = dsl.select(Person.PERSON.NAME)
                    .from(Person.PERSON)
                    .where(Person.PERSON.PERSONID.eq(shift.getPersoncode()))
                    .fetchOne(Person.PERSON.NAME);
            if (mainWorker != null && !mainWorker.isBlank()) {
                workers.add(mainWorker);
            } else {
                workers.add("ID " + shift.getPersoncode());
            }
        }

        List<String> shiftPersonWorkers = dsl.select(Person.PERSON.NAME)
                .from(Shiftperson.SHIFTPERSON)
                .join(Person.PERSON).on(Person.PERSON.PERSONID.eq(Shiftperson.SHIFTPERSON.PERSONID))
                .where(Shiftperson.SHIFTPERSON.SHIFTID.eq(shiftId))
                .fetch(Person.PERSON.NAME);
        for (String w : shiftPersonWorkers) {
            if (w != null && !w.isBlank()) {
                workers.add(w);
            }
        }

        var orderRows = dsl.select(
                        Order.ORDER.ORDERID,
                        Order.ORDER.CREATED_AT,
                        Order.ORDER.STATUS,
                        Order.ORDER.TYPE,
                        Order.ORDER.AMOUNT,
                        Order.ORDER.TIMEDELAY,
                        Order.ORDER.CLIENTID,
                        PAYMENT_TYPE_FIELD,
                        IS_PAID_FIELD
                )
                .from(Order.ORDER)
                .where(Order.ORDER.SHIFTID.eq(shiftId))
                .orderBy(Order.ORDER.ORDERID.asc())
                .fetch();

        List<Map<String, Object>> orders = new ArrayList<>();
        double totalRevenue = 0.0;
        double totalDeliveryExpense = 0.0;
        double totalItemsAmount = 0.0;
        int totalDishesCount = 0;
        Map<String, Map<String, Object>> positionStats = new HashMap<>();

        for (Record orderRow : orderRows) {
            Integer orderId = orderRow.get(Order.ORDER.ORDERID);
            Integer clientId = orderRow.get(Order.ORDER.CLIENTID);
            Record clientRow = null;
            if (clientId != null) {
                clientRow = dsl.select(Client.CLIENT.FULLNAME, Client.CLIENT.NUMBER)
                        .from(Client.CLIENT)
                        .where(Client.CLIENT.CLIENTID.eq(clientId))
                        .fetchOne();
            }
            List<Map<String, Object>> items = new ArrayList<>();
            double itemsTotal = 0.0;
            for (OrderLineItem itemRow : loadOrderLineItems(orderId)) {
                String dishName = itemRow.name();
                Integer qty = itemRow.qty();
                Double price = itemRow.price() != null ? itemRow.price() : 0.0;
                double lineTotal = price * qty;
                itemsTotal += lineTotal;
                totalDishesCount += qty;
                Map<String, Object> stats = positionStats.computeIfAbsent(
                        dishName != null ? dishName : "Без названия",
                        k -> {
                            Map<String, Object> m = new HashMap<>();
                            m.put("dishName", k);
                            m.put("qty", 0);
                            m.put("amount", 0.0);
                            return m;
                        }
                );
                stats.put("qty", ((Integer) stats.get("qty")) + qty);
                stats.put("amount", ((Double) stats.get("amount")) + lineTotal);

                Map<String, Object> item = new HashMap<>();
                item.put("dishName", dishName);
                item.put("qty", qty);
                item.put("price", price);
                item.put("sum", lineTotal);
                items.add(item);
            }

            Double orderAmount = orderRow.get(Order.ORDER.AMOUNT) != null ? orderRow.get(Order.ORDER.AMOUNT) : itemsTotal;
            boolean isDelivery = Boolean.TRUE.equals(orderRow.get(Order.ORDER.TYPE));
            double deliveryExpense = isDelivery ? Math.max(0.0, orderAmount - itemsTotal) : 0.0;

            totalRevenue += orderAmount;
            totalItemsAmount += itemsTotal;
            totalDeliveryExpense += deliveryExpense;

            Map<String, Object> orderData = new HashMap<>();
            orderData.put("orderId", orderId);
            orderData.put("createdAt", orderRow.get(Order.ORDER.CREATED_AT) != null ? orderRow.get(Order.ORDER.CREATED_AT).toString() : null);
            orderData.put("status", orderRow.get(Order.ORDER.STATUS));
            orderData.put("isDelivery", isDelivery);
            orderData.put("paymentType", orderRow.get(PAYMENT_TYPE_FIELD));
            orderData.put("isPaid", orderRow.get(IS_PAID_FIELD));
            orderData.put("itemsTotal", itemsTotal);
            orderData.put("deliveryExpense", deliveryExpense);
            orderData.put("orderAmount", orderAmount);
            orderData.put("delayMinutes", orderRow.get(Order.ORDER.TIMEDELAY) != null ? orderRow.get(Order.ORDER.TIMEDELAY) : 0.0);
            orderData.put("clientName", clientRow != null ? clientRow.get(Client.CLIENT.FULLNAME) : null);
            orderData.put("clientPhone", clientRow != null ? clientRow.get(Client.CLIENT.NUMBER) : null);
            orderData.put("items", items);
            orders.add(orderData);
        }

        List<Map<String, Object>> topPositions = positionStats.values().stream()
                .sorted(Comparator.comparing((Map<String, Object> m) -> (Integer) m.get("qty")).reversed())
                .toList();

        Map<String, Object> totals = new HashMap<>();
        totals.put("ordersCount", orders.size());
        totals.put("dishesCount", totalDishesCount);
        totals.put("itemsAmount", totalItemsAmount);
        totals.put("deliveryExpense", totalDeliveryExpense);
        totals.put("revenue", totalRevenue);
        totals.put("delayedOrdersCount", orders.stream().filter(o -> ((Double) o.get("delayMinutes")) > 0).count());

        Map<String, Object> report = new HashMap<>();
        report.put("reportType", "Z_REPORT");
        report.put("shiftId", shiftId);
        report.put("date", shift.getData() != null ? shift.getData().toString() : null);
        report.put("startTime", shift.getStarttime() != null ? shift.getStarttime().toString() : null);
        report.put("endTime", shift.getEndtime() != null ? shift.getEndtime().toString() : null);
        report.put("workers", new ArrayList<>(workers));
        report.put("orders", orders);
        report.put("topPositions", topPositions);
        report.put("totals", totals);
        return report;
    }

    private ShiftDTO toShiftDTO(ShiftRecord record) {
        ShiftDTO dto = new ShiftDTO();
        dto.shiftId = record.getId();
        dto.data = record.getData();
        dto.expenses = record.getExpenses();
        dto.profit = record.getProfit();
        dto.income = record.getIncome();
        dto.startTime = record.getStarttime();
        dto.endTime = record.getEndtime();
        dto.personCode = record.getPersoncode() != null ? record.getPersoncode() : 0;

        List<Integer> personIds = loadWorkerIds(record.getId(), record.getPersoncode());
        List<String> personNames = loadWorkerNames(personIds);
        dto.personIds = personIds;
        dto.personNames = personNames;
        dto.personName = !personNames.isEmpty() ? personNames.get(0) : null;
        return dto;
    }

    private LinkedHashSet<Integer> normalizeWorkerIds(ShiftDTO dto) {
        LinkedHashSet<Integer> workerIds = new LinkedHashSet<>();
        if (dto == null) {
            return workerIds;
        }
        if (dto.personCode >= 0) {
            workerIds.add(dto.personCode);
        }
        if (dto.personIds != null) {
            dto.personIds.stream()
                    .filter(id -> id != null && id >= 0)
                    .forEach(workerIds::add);
        }
        return workerIds;
    }

    private void syncShiftPersons(int shiftId, Set<Integer> workerIds, Integer mainWorkerId) {
        dsl.deleteFrom(Shiftperson.SHIFTPERSON)
                .where(Shiftperson.SHIFTPERSON.SHIFTID.eq(shiftId))
                .execute();

        workerIds.stream()
                .filter(id -> mainWorkerId == null || !mainWorkerId.equals(id))
                .forEach(id -> dsl.insertInto(Shiftperson.SHIFTPERSON)
                        .set(Shiftperson.SHIFTPERSON.SHIFTID, shiftId)
                        .set(Shiftperson.SHIFTPERSON.PERSONID, id)
                        .execute());
    }

    private List<Integer> loadWorkerIds(int shiftId, Integer mainWorkerId) {
        LinkedHashSet<Integer> ids = new LinkedHashSet<>();
        if (mainWorkerId != null && mainWorkerId >= 0) {
            ids.add(mainWorkerId);
        }
        ids.addAll(
                dsl.select(Shiftperson.SHIFTPERSON.PERSONID)
                        .from(Shiftperson.SHIFTPERSON)
                        .where(Shiftperson.SHIFTPERSON.SHIFTID.eq(shiftId))
                        .fetch(Shiftperson.SHIFTPERSON.PERSONID)
        );
        return new ArrayList<>(ids);
    }

    private List<String> loadWorkerNames(List<Integer> personIds) {
        if (personIds == null || personIds.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Integer, String> namesById = dsl.select(Person.PERSON.PERSONID, Person.PERSON.NAME)
                .from(Person.PERSON)
                .where(Person.PERSON.PERSONID.in(personIds))
                .fetchMap(Person.PERSON.PERSONID, Person.PERSON.NAME);

        List<String> names = new ArrayList<>();
        for (Integer personId : personIds) {
            if (personId == null) {
                continue;
            }
            String name = namesById.get(personId);
            names.add(name != null && !name.isBlank() ? name : "ID " + personId);
        }
        return names;
    }

    private void validateWorkersAvailable(Set<Integer> workerIds, Integer currentShiftId) {
        if (workerIds == null || workerIds.isEmpty()) {
            return;
        }

        LinkedHashSet<String> conflicts = new LinkedHashSet<>();
        List<ShiftRecord> openShifts = dsl.selectFrom(Shift.SHIFT)
                .where(Shift.SHIFT.ENDTIME.isNull())
                .fetch();

        for (ShiftRecord openShift : openShifts) {
            if (currentShiftId != null && currentShiftId.equals(openShift.getId())) {
                continue;
            }

            List<Integer> existingWorkerIds = loadWorkerIds(openShift.getId(), openShift.getPersoncode());
            for (Integer workerId : existingWorkerIds) {
                if (workerId != null && workerIds.contains(workerId)) {
                    conflicts.add(resolveWorkerName(workerId));
                }
            }
        }

        if (!conflicts.isEmpty()) {
            throw new RuntimeException("Уже есть открытая смена у: " + String.join(", ", conflicts));
        }
    }

    private String resolveWorkerName(Integer workerId) {
        if (workerId == null) {
            return "Неизвестный сотрудник";
        }
        String name = dsl.select(Person.PERSON.NAME)
                .from(Person.PERSON)
                .where(Person.PERSON.PERSONID.eq(workerId))
                .fetchOne(Person.PERSON.NAME);
        return name != null && !name.isBlank() ? name : "ID " + workerId;
    }


}
