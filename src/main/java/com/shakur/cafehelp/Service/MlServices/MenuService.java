package com.shakur.cafehelp.Service.MlServices;

import com.shakur.cafehelp.DTO.MlDTO.RollMenuItemDTO;
import com.shakur.cafehelp.Service.ProductService;
import com.shakur.cafehelp.Service.RecipeExpansionService;
import jooqdata.tables.records.DishRecord;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

import java.util.List;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static jooqdata.tables.Dish.DISH;

@Service
@RequiredArgsConstructor
public class  MenuService {
    private static final Field<LocalDateTime> ORDER_CANCELLED_AT =
            DSL.field(DSL.name("cancelled_at"), LocalDateTime.class);

    private final DSLContext dsl;
    private final RecipeExpansionService recipeExpansionService;
    private final ProductService productService;

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
        Map<Integer, String> productNames = productService.getProducts()
                .stream()
                .filter(product -> product.getProductName() != null)
                .collect(Collectors.toMap(
                        product -> product.getProductId(),
                        product -> product.getProductName(),
                        (left, right) -> left
                ));

        return recipeExpansionService.buildRequirementsForDish(dishId, 1)
                .keySet()
                .stream()
                .map(productNames::get)
                .filter(name -> name != null && !name.isBlank())
                .sorted((a, b) -> a.toLowerCase(Locale.ROOT).compareTo(b.toLowerCase(Locale.ROOT)))
                .toList();
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
                .and(ORDER_CANCELLED_AT.isNull())
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
