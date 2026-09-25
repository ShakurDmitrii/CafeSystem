package com.shakur.cafehelp.Service.MlServices;

import com.shakur.cafehelp.DTO.MlDTO.SalesRecordDTO;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private static final Field<LocalDateTime> ORDER_CANCELLED_AT =
            DSL.field(DSL.name("cancelled_at"), LocalDateTime.class);

    private final DSLContext dsl;
    private final MenuService menuService;

    /**
     * Получить историю продаж для ML обучения
     * @param startDate начальная дата
     * @param endDate конечная дата
     * @return список записей о продажах
     */
    public List<SalesRecordDTO> getSalesForML(LocalDate startDate, LocalDate endDate) {
        Map<Integer, List<String>> ingredientsByDish = new java.util.HashMap<>();
        return dsl.select(ORDER.DATE, ORDER.ORDERID, DISH.DISHID, DISH.DISHNAME,
                        DISH.PRICE, DISH.FIRSTCOST, ORDERDISH.QTY)
                .from(ORDER)
                .join(ORDERDISH).on(ORDER.ORDERID.eq(ORDERDISH.ORDERID))
                .join(DISH).on(ORDERDISH.DISHID.eq(DISH.DISHID))
                .where(ORDER.DATE.between(startDate, endDate))
                .and(ORDER.STATUS.eq(true))
                .and(ORDER_CANCELLED_AT.isNull())
                .orderBy(ORDER.DATE, ORDER.ORDERID, ORDERDISH.ID)
                .fetch(record -> {
                    Integer dishId = record.get(DISH.DISHID);
                    int quantity = record.get(ORDERDISH.QTY);
                    Double price = record.get(DISH.PRICE);
                    Double cost = record.get(DISH.FIRSTCOST);
                    // Legacy orders have no recipe/price snapshots. These are CURRENT reference values.
                    return SalesRecordDTO.builder()
                            .rollId(String.valueOf(dishId))
                            .rollName(record.get(DISH.DISHNAME))
                            .ingredients(ingredientsByDish.computeIfAbsent(dishId, menuService::getDishIngredients))
                            .saleDate(record.get(ORDER.DATE))
                            .quantity(quantity)
                            .pricePerUnit(price)
                            .unitCost(cost)
                            .totalAmount(price == null ? null : quantity * price)
                            .totalCost(cost == null ? null : quantity * cost)
                            .locationId("default_location")
                            .build();
                });
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
                .and(ORDER_CANCELLED_AT.isNull())
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
                .and(ORDER_CANCELLED_AT.isNull())
                .groupBy(DISH.DISHID, DISH.DISHNAME)
                .fetch()
                .stream()
                .collect(Collectors.toMap(
                        record -> record.get(DISH.DISHID).toString(),
                        record -> record.get("total_qty", Integer.class)
                ));
    }

    /**
     * Оптимизированный запрос для больших объемов данных
     */
    public List<SalesRecordDTO> getSalesForMLOptimized(LocalDate startDate, LocalDate endDate) {
        return getSalesForML(startDate, endDate);
    }
}
