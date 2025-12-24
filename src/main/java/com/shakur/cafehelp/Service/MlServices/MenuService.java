package com.shakur.cafehelp.Service.MlServices;

import com.shakur.cafehelp.DTO.MlDTO.RollMenuItemDTO;
import jooqdata.tables.records.DishRecord;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import static jooqdata.tables.Dish.DISH;

@Service
@RequiredArgsConstructor
public class  MenuService {

    private final DSLContext dsl;

    /**
     * Получить все блюда (роллы) из меню
     */
    public List<RollMenuItemDTO> getAllMenuItems() {
        return dsl.selectFrom(DISH)
                .where(DISH.PRICE.isNotNull())
                .and(DISH.DISHNAME.isNotNull())
                .fetch()
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Получить блюдо по ID
     */
    public RollMenuItemDTO getMenuItemById(Integer dishId) {
        var record = dsl.selectFrom(DISH)
                .where(DISH.DISHID.eq(dishId))
                .fetchOne();

        return record != null ? mapToDTO(record) : null;
    }

    /**
     * Получить состав блюда (ингредиенты)
     */
    public List<String> getDishIngredients(Integer dishId) {
        return dsl.select(jooqdata.tables.Product.PRODUCT.PRODUCTNAME)
                .from(jooqdata.tables.Techproduct.TECHPRODUCT)
                .join(jooqdata.tables.Product.PRODUCT)
                .on(jooqdata.tables.Product.PRODUCT.PRODUCTID
                        .eq(jooqdata.tables.Techproduct.TECHPRODUCT.PRODUCTID))
                .where(jooqdata.tables.Techproduct.TECHPRODUCT.DISHID.eq(dishId))
                .fetch()
                .map(record -> record.get(jooqdata.tables.Product.PRODUCT.PRODUCTNAME));
    }

    /**
     * Получить популярные блюда за период
     */
    public List<RollMenuItemDTO> getPopularDishes(int days, int limit) {
        var sinceDate = java.time.LocalDate.now().minusDays(days);

        return dsl.select(DISH.fields())
                .from(DISH)
                .join(jooqdata.tables.Orderdish.ORDERDISH)
                .on(DISH.DISHID.eq(jooqdata.tables.Orderdish.ORDERDISH.DISHID))
                .join(jooqdata.tables.Order.ORDER)
                .on(jooqdata.tables.Orderdish.ORDERDISH.ORDERID
                        .eq(jooqdata.tables.Order.ORDER.ORDERID))
                .where(jooqdata.tables.Order.ORDER.DATE.greaterOrEqual(sinceDate))
                .and(jooqdata.tables.Order.ORDER.STATUS.eq(true))
                .groupBy(DISH.DISHID, DISH.DISHNAME, DISH.PRICE,
                        DISH.FIRSTCOST, DISH.CATEGORY)
                .orderBy(org.jooq.impl.DSL.sum(jooqdata.tables.Orderdish.ORDERDISH.QTY).desc())
                .limit(limit)
                .fetch()
                .stream()
                .map(record -> mapToDTO(record.into(DISH)))
                .collect(Collectors.toList());
    }



    /**
     * Получить блюда по категории
     */
    public List<RollMenuItemDTO> getDishesByCategory(String category) {
        return dsl.selectFrom(DISH)
                .where(DISH.CATEGORY.eq(category))
                .and(DISH.PRICE.isNotNull())
                .fetch()
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Маппинг Record -> DTO
     */
    private RollMenuItemDTO mapToDTO(DishRecord record) {
        // Получаем состав блюда
        List<String> ingredients = getDishIngredients(record.getDishid());

        return RollMenuItemDTO.builder()
                .id(String.valueOf(record.getDishid()))
                .name(record.getDishname())
                .description("") // У вас нет description в таблице
                .ingredients(ingredients)
                .category(record.getCategory())
                .price(record.getPrice())
                .cost(record.getFirstcost())
                .preparationTime(10) // Дефолтное значение
                .isAvailable(true)
                .popularityScore(0.0)
                .build();
    }

    /**
     * Получить все категории блюд
     */
    public List<String> getAllCategories() {
        return dsl.selectDistinct(DISH.CATEGORY)
                .from(DISH)
                .where(DISH.CATEGORY.isNotNull())
                .fetch()
                .map(record -> record.get(DISH.CATEGORY));
    }
}