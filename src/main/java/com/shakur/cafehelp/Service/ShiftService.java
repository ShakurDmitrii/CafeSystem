package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.DishDTO;
import com.shakur.cafehelp.DTO.ShiftDTO;
import jooqdata.tables.Dish;
import jooqdata.tables.Order;
import jooqdata.tables.Orderdish;
import jooqdata.tables.Shift;
import jooqdata.tables.records.ShiftRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static jooqdata.tables.Dish.DISH;

@Service
public class ShiftService {
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
                    return dto;
                }).toList();
    }

    // Создание новой смены
    public ShiftDTO createShift(ShiftDTO dto) {
        ShiftRecord record = dsl.newRecord(Shift.SHIFT);
        record.setData(dto.data);
        record.setExpenses(dto.expenses);
        record.setProfit(dto.profit);
        record.setIncome(dto.income); // добавлено
        record.setStarttime(dto.startTime);
        record.setEndtime(dto.endTime);
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
        return dsl.select(Dish.DISH.DISHID, Dish.DISH.DISHNAME, Dish.DISH.PRICE)
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
                    return dto;
                })
                .toList();
    }


}
