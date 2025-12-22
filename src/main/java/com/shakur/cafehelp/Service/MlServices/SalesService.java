package com.shakur.cafehelp.Service.MlServices;

import com.shakur.cafehelp.DTO.MlDTO.SalesRecordDTO;
import jooqdata.tables.Order;
import jooqdata.tables.records.OrderRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

import com.shakur.cafehelp.DTO.MlDTO.SalesRecordDTO;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


import static jooqdata.tables.Dish.DISH;
import static jooqdata.tables.Order.ORDER;
import static jooqdata.tables.Orderdish.ORDERDISH;
import static jooqdata.tables.Product.PRODUCT;
import static jooqdata.tables.Techproduct.TECHPRODUCT;

@Service
@RequiredArgsConstructor
public class SalesService {

    private final DSLContext dsl;

    /**
     * Получить историю продаж для ML обучения
     * @param startDate начальная дата
     * @param endDate конечная дата
     * @return список записей о продажах
     */
    public List<SalesRecordDTO> getSalesForML(LocalDate startDate, LocalDate endDate) {
        // 1. Получаем все завершенные заказы за период
        var orders = dsl.selectFrom(ORDER)
                .where(ORDER.DATE.between(startDate, endDate))
                .and(ORDER.STATUS.eq(true)) // только завершенные
                .fetch();

        return orders.stream()
                .flatMap(order -> {
                    // 2. Для каждого заказа получаем его позиции
                    var orderItems = dsl.selectFrom(ORDERDISH)
                            .where(ORDERDISH.ORDERID.eq(order.getOrderid()))
                            .fetch();

                    return orderItems.stream().map(orderItem -> {
                        // 3. Получаем информацию о блюде
                        var dish = dsl.selectFrom(DISH)
                                .where(DISH.DISHID.eq(orderItem.getDishid()))
                                .fetchOne();

                        if (dish == null) {
                            return null;
                        }

                        // 4. Получаем состав блюда (ингредиенты)
                        var ingredients = dsl.select(PRODUCT.PRODUCTNAME)
                                .from(TECHPRODUCT)
                                .join(PRODUCT).on(PRODUCT.PRODUCTID.eq(TECHPRODUCT.PRODUCTID))
                                .where(TECHPRODUCT.DISHID.eq(dish.getDishid()))
                                .fetch()
                                .map(record -> record.get(PRODUCT.PRODUCTNAME));

                        // 5. Создаем DTO для ML
                        return SalesRecordDTO.builder()
                                .rollId(String.valueOf(dish.getDishid()))
                                .rollName(dish.getDishname())
                                .ingredients(ingredients)
                                .saleDate(order.getDate())
                                .quantity(orderItem.getQty())
                                .totalAmount(calculateTotal(orderItem, dish))
                                .pricePerUnit(dish.getPrice())
                                .locationId(getLocationFromOrder(order))
                                .build();
                    });
                })
                .filter(record -> record != null)
                .collect(Collectors.toList());
    }

    /**
     * Получить популярные ингредиенты на основе продаж
     * @param days за сколько дней
     * @param limit ограничение количества
     * @return список популярных ингредиентов
     */
    public List<String> getPopularIngredients(int days, int limit) {
        LocalDate sinceDate = LocalDate.now().minusDays(days);

        return dsl.select(PRODUCT.PRODUCTNAME)
                .from(ORDER)
                .join(ORDERDISH).on(ORDER.ORDERID.eq(ORDERDISH.ORDERID))
                .join(DISH).on(ORDERDISH.DISHID.eq(DISH.DISHID))
                .join(TECHPRODUCT).on(DISH.DISHID.eq(TECHPRODUCT.DISHID))
                .join(PRODUCT).on(TECHPRODUCT.PRODUCTID.eq(PRODUCT.PRODUCTID))
                .where(ORDER.DATE.greaterOrEqual(sinceDate))
                .and(ORDER.STATUS.eq(true))
                .groupBy(PRODUCT.PRODUCTNAME)
                .orderBy(org.jooq.impl.DSL.sum(ORDERDISH.QTY).desc())
                .limit(limit)
                .fetch()
                .map(record -> record.get(PRODUCT.PRODUCTNAME));
    }

    /**
     * Получить статистику продаж по роллам
     * @param startDate начальная дата
     * @param endDate конечная дата
     * @return Map<dishId, totalQuantity>
     */
    public Map<String, Integer> getSalesStatistics(LocalDate startDate, LocalDate endDate) {
        return dsl.select(
                        DISH.DISHID,
                        DISH.DISHNAME,
                        org.jooq.impl.DSL.sum(ORDERDISH.QTY).as("total_qty")
                )
                .from(ORDER)
                .join(ORDERDISH).on(ORDER.ORDERID.eq(ORDERDISH.ORDERID))
                .join(DISH).on(ORDERDISH.DISHID.eq(DISH.DISHID))
                .where(ORDER.DATE.between(startDate, endDate))
                .and(ORDER.STATUS.eq(true))
                .groupBy(DISH.DISHID, DISH.DISHNAME)
                .fetch()
                .stream()
                .collect(Collectors.toMap(
                        record -> record.get(DISH.DISHID).toString(),
                        record -> record.get("total_qty", Integer.class)
                ));
    }

    // Вспомогательные методы

    private Double calculateTotal(jooqdata.tables.records.OrderdishRecord orderItem, jooqdata.tables.records.DishRecord dish) {
        if (orderItem.getQty() != null && dish.getPrice() != null) {
            return orderItem.getQty() * dish.getPrice();
        }
        return 0.0;
    }

    private String getLocationFromOrder(OrderRecord order) {
        // Если есть информация о локации в заказе
        // Иначе возвращаем дефолтное значение
        return "default_location";
    }

    /**
     * Оптимизированный запрос для больших объемов данных
     */
    public List<SalesRecordDTO> getSalesForMLOptimized(LocalDate startDate, LocalDate endDate) {
        // Один большой запрос вместо множества маленьких
        return dsl.select(
                        ORDER.DATE,
                        ORDER.ORDERID,
                        DISH.DISHID,
                        DISH.DISHNAME,
                        DISH.PRICE,
                        ORDERDISH.QTY,
                        PRODUCT.PRODUCTNAME
                )
                .from(ORDER)
                .join(ORDERDISH).on(ORDER.ORDERID.eq(ORDERDISH.ORDERID))
                .join(DISH).on(ORDERDISH.DISHID.eq(DISH.DISHID))
                .join(TECHPRODUCT).on(DISH.DISHID.eq(TECHPRODUCT.DISHID))
                .join(PRODUCT).on(TECHPRODUCT.PRODUCTID.eq(PRODUCT.PRODUCTID))
                .where(ORDER.DATE.between(startDate, endDate))
                .and(ORDER.STATUS.eq(true))
                .orderBy(ORDER.ORDERID, DISH.DISHID)
                .fetch()
                .stream()
                .collect(Collectors.groupingBy(
                        record -> record.get(ORDER.ORDERID) + "_" + record.get(DISH.DISHID),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                records -> {
                                    var firstRecord = records.get(0);
                                    var ingredients = records.stream()
                                            .map(r -> r.get(PRODUCT.PRODUCTNAME))
                                            .distinct()
                                            .collect(Collectors.toList());

                                    return SalesRecordDTO.builder()
                                            .rollId(String.valueOf(firstRecord.get(DISH.DISHID)))
                                            .rollName(firstRecord.get(DISH.DISHNAME))
                                            .ingredients(ingredients)
                                            .saleDate(firstRecord.get(ORDER.DATE))
                                            .quantity(firstRecord.get(ORDERDISH.QTY))
                                            .totalAmount(firstRecord.get(ORDERDISH.QTY) * firstRecord.get(DISH.PRICE))
                                            .pricePerUnit(firstRecord.get(DISH.PRICE))
                                            .build();
                                }
                        )
                ))
                .values().stream()
                .collect(Collectors.toList());
    }
}