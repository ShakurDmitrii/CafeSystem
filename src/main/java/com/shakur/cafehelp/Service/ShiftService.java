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

    private final DSLContext dsl;

    public ShiftService(DSLContext dsl) {
        this.dsl = dsl;
    }

    // Получить все смены
    public List<ShiftDTO> findAllShifts() {
        return dsl.selectFrom(Shift.SHIFT)
                .fetch()
                .stream()
                .map(record -> {
                    ShiftDTO dto = new ShiftDTO();
                    dto.shiftId = record.getId();
                    dto.data = record.getData();
                    dto.expenses = record.getExpenses();
                    dto.profit = record.getProfit();
                    dto.income = record.getIncome(); // добавлено
                    dto.startTime = record.getStarttime();
                    dto.endTime = record.getEndtime();
                    dto.personCode = record.getPersoncode() != null ? record.getPersoncode() : 0;
                    return dto;
                }).toList();
    }

    // Создание новой смены
    public ShiftDTO createShift(ShiftDTO dto) {
        if (dto.personCode != 0) {
            boolean hasOpenShift = dsl.fetchExists(
                    dsl.selectOne()
                            .from(Shift.SHIFT)
                            .where(Shift.SHIFT.PERSONCODE.eq(dto.personCode))
                            .and(Shift.SHIFT.ENDTIME.isNull())
            );
            if (hasOpenShift) {
                throw new RuntimeException("У сотрудника уже есть открытая смена");
            }
        }

        ShiftRecord record = dsl.newRecord(Shift.SHIFT);
        record.setData(dto.data);
        record.setExpenses(dto.expenses);
        record.setProfit(dto.profit);
        record.setIncome(dto.income); // добавлено
        record.setStarttime(dto.startTime);
        record.setEndtime(dto.endTime);
        record.setPersoncode(dto.personCode);
        record.store(); // id присвоится автоматически базой

        dto.shiftId = record.getId(); // получаем сгенерированный id
        return dto;
    }

    // Обновление смены
    public ShiftDTO updateShift(int shiftId, ShiftDTO dto) {
        int rows = dsl.update(Shift.SHIFT)
                .set(Shift.SHIFT.DATA, dto.data)
                .set(Shift.SHIFT.EXPENSES, dto.expenses)
                .set(Shift.SHIFT.PROFIT, dto.profit)
                .set(Shift.SHIFT.INCOME, dto.income)
                .set(Shift.SHIFT.STARTTIME, dto.startTime)
                .set(Shift.SHIFT.ENDTIME, dto.endTime)
                .set(Shift.SHIFT.PERSONCODE, dto.personCode == 0 ? null : dto.personCode)
                .where(Shift.SHIFT.ID.eq(shiftId))
                .execute();

        if (rows != 1) {
            throw new RuntimeException("Update affected " + rows + " rows");
        }

        // Возвращаем DTO с тем же shiftId
        dto.shiftId = shiftId;
        return dto;
    }


    // Получение смены по ID
    public ShiftDTO getShiftById(int id) {
        return dsl.selectFrom(Shift.SHIFT)
                .where(Shift.SHIFT.ID.eq(id))
                .fetchOptional()
                .map(shiftRecord -> {
                    ShiftDTO dto = new ShiftDTO();
                    dto.shiftId = shiftRecord.getId();
                    dto.data = shiftRecord.getData();
                    dto.expenses = shiftRecord.getExpenses();
                    dto.profit = shiftRecord.getProfit();
                    dto.income = shiftRecord.getIncome(); // добавлено
                    dto.startTime = shiftRecord.getStarttime();
                    dto.endTime = shiftRecord.getEndtime();
                    dto.personCode = shiftRecord.getPersoncode() != null ? shiftRecord.getPersoncode() : 0;
                    return dto;
                }).orElseThrow(() -> new RuntimeException("Shift not found with id: " + id));
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

    // Закрытие смены
    public ShiftRecord closeShift(int shiftId, BigDecimal expenses) {
        // Получаем все заказы за эту смену
        var orders = dsl.selectFrom(Order.ORDER)
                .where(Order.ORDER.SHIFTID.eq(shiftId))
                .fetch();

        // Считаем income
        Double income = orders.stream().mapToDouble(order ->
                dsl.select(Orderdish.ORDERDISH.QTY, DISH.PRICE, DISH.FIRSTCOST)
                        .from(Orderdish.ORDERDISH)
                        .join(DISH).on(DISH.DISHID.eq(Orderdish.ORDERDISH.DISHID))
                        .where(Orderdish.ORDERDISH.ORDERID.eq(order.getOrderid()))
                        .fetch()
                        .stream()
                        .mapToDouble(r -> r.get(DISH.PRICE).doubleValue() * r.get(Orderdish.ORDERDISH.QTY))
                        .sum()
        ).sum();

        // Считаем себестоимость
        Double totalCost = orders.stream().mapToDouble(order ->
                dsl.select(Orderdish.ORDERDISH.QTY, DISH.FIRSTCOST)
                        .from(Orderdish.ORDERDISH)
                        .join(DISH).on(DISH.DISHID.eq(Orderdish.ORDERDISH.DISHID))
                        .where(Orderdish.ORDERDISH.ORDERID.eq(order.getOrderid()))
                        .fetch()
                        .stream()
                        .mapToDouble(r -> r.get(DISH.FIRSTCOST).doubleValue() * r.get(Orderdish.ORDERDISH.QTY))
                        .sum()
        ).sum();

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
        return dsl.select(Dish.DISH.DISHID, Dish.DISH.DISHNAME, Dish.DISH.PRICE, Orderdish.ORDERDISH.QTY )
                .from(Orderdish.ORDERDISH)
                .join(Dish.DISH)
                .on(Orderdish.ORDERDISH.DISHID.eq(Dish.DISH.DISHID))
                .where(Orderdish.ORDERDISH.ORDERID.eq(orderId))
                .fetch()
                .stream()
                .map(record -> {
                    DishDTO dto = new DishDTO();
                    dto.dishId = record.get(Dish.DISH.DISHID);
                    dto.dishName = record.get(Dish.DISH.DISHNAME);
                    dto.price = record.get(Dish.DISH.PRICE);
                    dto.qty = record.get(Orderdish.ORDERDISH.QTY);
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
            var dishRows = dsl.select(
                            Dish.DISH.DISHNAME,
                            Dish.DISH.PRICE,
                            Orderdish.ORDERDISH.QTY
                    )
                    .from(Orderdish.ORDERDISH)
                    .join(Dish.DISH).on(Dish.DISH.DISHID.eq(Orderdish.ORDERDISH.DISHID))
                    .where(Orderdish.ORDERDISH.ORDERID.eq(orderId))
                    .fetch();

            List<Map<String, Object>> items = new ArrayList<>();
            double itemsTotal = 0.0;
            for (Record dishRow : dishRows) {
                String dishName = dishRow.get(Dish.DISH.DISHNAME);
                Integer qty = dishRow.get(Orderdish.ORDERDISH.QTY) != null ? dishRow.get(Orderdish.ORDERDISH.QTY) : 0;
                Double price = dishRow.get(Dish.DISH.PRICE) != null ? dishRow.get(Dish.DISH.PRICE) : 0.0;
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


}
