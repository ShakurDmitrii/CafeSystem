package com.shakur.cafehelp.Service.MlServices;

import com.shakur.cafehelp.DTO.MlDTO.IngredientDTO;
import jooqdata.tables.records.ProductRecord;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import static jooqdata.tables.Product.PRODUCT;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final DSLContext dsl;

    /**
     * Получить все ингредиенты
     */
    public List<IngredientDTO> getAllIngredients() {
        return dsl.selectFrom(PRODUCT)
                .where(PRODUCT.PRODUCTNAME.isNotNull())
                .fetch()
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Получить ингредиент по ID
     */
    public IngredientDTO getIngredientById(Integer productId) {
        var record = dsl.selectFrom(PRODUCT)
                .where(PRODUCT.PRODUCTID.eq(productId))
                .fetchOne();

        return record != null ? mapToDTO(record) : null;
    }

    /**
     * Получить ингредиенты по названию (поиск)
     */
    public List<IngredientDTO> searchIngredients(String query) {
        return dsl.selectFrom(PRODUCT)
                .where(PRODUCT.PRODUCTNAME.containsIgnoreCase(query))
                .fetch()
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Получить ингредиенты по категории (если будет таблица категорий)
     */
    public List<IngredientDTO> getIngredientsByCategory(String category) {
        // У вас нет категории в таблице Product, но можно добавить если нужно
        return dsl.selectFrom(PRODUCT)
                .where(PRODUCT.PRODUCTNAME.isNotNull())
                .fetch()
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Получить ингредиенты с низким запасом
     */
    public List<IngredientDTO> getLowStockIngredients(Double threshold) {
        // У вас нет currentStock в таблице, но можно добавить логику если появится
        return getAllIngredients(); // временно возвращаем все
    }

    /**
     * Получить ингредиенты по цене (дешевые/дорогие)
     */
    public List<IngredientDTO> getIngredientsByPriceRange(Double minPrice, Double maxPrice) {
        var query = dsl.selectFrom(PRODUCT)
                .where(PRODUCT.PRODUCTNAME.isNotNull());

        if (minPrice != null) {
            query = query.and(PRODUCT.PRODUCTPRICE.ge(BigDecimal.valueOf(minPrice)));
        }
        if (maxPrice != null) {
            query = query.and(PRODUCT.PRODUCTPRICE.le(BigDecimal.valueOf(maxPrice)));
        }

        return query.fetch()
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Маппинг Record -> DTO
     */
    private IngredientDTO mapToDTO(ProductRecord record) {
        Double price = record.getProductprice() != null
                ? record.getProductprice().doubleValue()
                : 0.0;

        return IngredientDTO.builder()
                .id(String.valueOf(record.getProductid()))
                .name(record.getProductname())
                .displayName(record.getProductname())
                .category(getIngredientCategory(record.getProductname())) // Определяем категорию по названию
                .unit("кг") // Дефолтное значение
                .costPerUnit(price)
                .currentStock(0.0) // Нет данных о запасах
                .minStockLevel(0.0)
                .isActive(true)
                .isSeasonal(false)
                .allergies(getAllergiesByIngredient(record.getProductname()))
                .build();
    }

    /**
     * Определить категорию ингредиента по названию
     */
    private String getIngredientCategory(String productName) {
        if (productName == null) return "other";

        String lowerName = productName.toLowerCase();

        if (lowerName.contains("лосос") || lowerName.contains("тунец") ||
                lowerName.contains("угор") || lowerName.contains("икра")) {
            return "fish";
        } else if (lowerName.contains("рис") || lowerName.contains("нори")) {
            return "base";
        } else if (lowerName.contains("авокадо") || lowerName.contains("огурец") ||
                lowerName.contains("перец") || lowerName.contains("салат")) {
            return "vegetable";
        } else if (lowerName.contains("сыр") || lowerName.contains("майонез") ||
                lowerName.contains("соус")) {
            return "sauce";
        } else {
            return "other";
        }
    }

    /**
     * Определить аллергены по ингредиенту
     */
    private List<String> getAllergiesByIngredient(String productName) {
        if (productName == null) return List.of();

        String lowerName = productName.toLowerCase();
        List<String> allergies = new java.util.ArrayList<>();

        if (lowerName.contains("лосос") || lowerName.contains("тунец") ||
                lowerName.contains("рыб") || lowerName.contains("икра")) {
            allergies.add("fish");
        }
        if (lowerName.contains("соя") || lowerName.contains("соевый")) {
            allergies.add("soy");
        }
        if (lowerName.contains("глютен") || lowerName.contains("пшениц")) {
            allergies.add("gluten");
        }

        return allergies;
    }

    /**
     * Получить статистику использования ингредиентов
     */
    public List<IngredientUsageDTO> getIngredientUsageStatistics(int days) {
        var sinceDate = java.time.LocalDate.now().minusDays(days);

        return dsl.select(
                        PRODUCT.PRODUCTID,
                        PRODUCT.PRODUCTNAME,
                        org.jooq.impl.DSL.sum(jooqdata.tables.Orderdish.ORDERDISH.QTY).as("total_used")
                )
                .from(PRODUCT)
                .join(jooqdata.tables.Techproduct.TECHPRODUCT)
                .on(PRODUCT.PRODUCTID.eq(jooqdata.tables.Techproduct.TECHPRODUCT.PRODUCTID))
                .join(jooqdata.tables.Orderdish.ORDERDISH)
                .on(jooqdata.tables.Techproduct.TECHPRODUCT.DISHID
                        .eq(jooqdata.tables.Orderdish.ORDERDISH.DISHID))
                .join(jooqdata.tables.Order.ORDER)
                .on(jooqdata.tables.Orderdish.ORDERDISH.ORDERID
                        .eq(jooqdata.tables.Order.ORDER.ORDERID))
                .where(jooqdata.tables.Order.ORDER.DATE.greaterOrEqual(sinceDate))
                .and(jooqdata.tables.Order.ORDER.STATUS.eq(true))
                .groupBy(PRODUCT.PRODUCTID, PRODUCT.PRODUCTNAME)
                .orderBy(org.jooq.impl.DSL.sum(jooqdata.tables.Orderdish.ORDERDISH.QTY).desc())
                .fetch()
                .stream()
                .map(record -> IngredientUsageDTO.builder()
                        .ingredientId(record.get(PRODUCT.PRODUCTID))
                        .ingredientName(record.get(PRODUCT.PRODUCTNAME))
                        .totalUsed(record.get("total_used", Integer.class))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * DTO для статистики использования ингредиентов
     */
    @lombok.Builder
    @lombok.Data
    public static class IngredientUsageDTO {
        private Integer ingredientId;
        private String ingredientName;
        private Integer totalUsed;
    }
}