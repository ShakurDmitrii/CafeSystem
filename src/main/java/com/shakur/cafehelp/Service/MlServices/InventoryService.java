package com.shakur.cafehelp.Service.MlServices;

import com.shakur.cafehelp.DTO.MlDTO.IngredientDTO;
import com.shakur.cafehelp.DTO.ProductDTO;
import com.shakur.cafehelp.DTO.ProductWarehouseDTO;
import com.shakur.cafehelp.Service.ProductService;
import com.shakur.cafehelp.Service.WareHouseService;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static jooqdata.tables.Product.PRODUCT;

@Service
@RequiredArgsConstructor
public class InventoryService {
    private static final Field<LocalDateTime> ORDER_CANCELLED_AT =
            DSL.field(DSL.name("cancelled_at"), LocalDateTime.class);

    private final DSLContext dsl;
    private final ProductService productService;
    private final WareHouseService wareHouseService;

    /**
     * Получить все ингредиенты
     */
    public List<IngredientDTO> getAllIngredients() {
        return getAllIngredients(null);
    }

    public List<IngredientDTO> getAllIngredients(Integer requestedWarehouseId) {
        Integer warehouseId = requestedWarehouseId != null ? requestedWarehouseId : wareHouseService.getMainWarehouseId();
        if (requestedWarehouseId != null && wareHouseService.getById(requestedWarehouseId) == null) {
            throw new IllegalArgumentException("Склад не найден");
        }
        var stocks = warehouseId == null ? java.util.Map.<Integer, ProductWarehouseDTO>of()
                : wareHouseService.getProductsOnWarehouse(warehouseId).stream().collect(Collectors.toMap(
                        ProductWarehouseDTO::getProductId, stock -> stock));
        return productService.getProducts().stream()
                .filter(product -> product.getProductName() != null && !product.getProductName().isBlank())
                .filter(product -> !"consumable".equalsIgnoreCase(product.getItemType()))
                .map(product -> mapStockToDTO(product, stocks.get(product.getProductId()), warehouseId))
                .toList();
    }

    /**
     * Получить ингредиент по ID
     */
    public IngredientDTO getIngredientById(Integer productId) {
        return getAllIngredients().stream().filter(item -> item.getId().equals(String.valueOf(productId)))
                .findFirst().orElse(null);
    }

    /**
     * Получить ингредиенты по названию (поиск)
     */
    public List<IngredientDTO> searchIngredients(String query) {
        String normalized = query == null ? "" : query.toLowerCase(java.util.Locale.ROOT);
        return getAllIngredients().stream()
                .filter(item -> item.getName().toLowerCase(java.util.Locale.ROOT).contains(normalized)).toList();
    }

    /**
     * Получить ингредиенты по категории (если будет таблица категорий)
     */
    public List<IngredientDTO> getIngredientsByCategory(String category) {
        return getAllIngredients().stream().filter(item -> item.getCategory().equalsIgnoreCase(category)).toList();
    }

    /**
     * Получить ингредиенты с низким запасом
     */
    public List<IngredientDTO> getLowStockIngredients(Double threshold) {
        return getAllIngredients().stream().filter(item -> item.getCurrentStock() != null
                && item.getCurrentStock() <= (threshold == null ? 0 : threshold)).toList();
    }

    /**
     * Получить ингредиенты по цене (дешевые/дорогие)
     */
    public List<IngredientDTO> getIngredientsByPriceRange(Double minPrice, Double maxPrice) {
        return getAllIngredients().stream().filter(item -> item.getCostPerUnit() != null)
                .filter(item -> minPrice == null || item.getCostPerUnit() >= minPrice)
                .filter(item -> maxPrice == null || item.getCostPerUnit() <= maxPrice).toList();
    }

    /**
     * Маппинг Record -> DTO
     */
    private IngredientDTO mapStockToDTO(ProductDTO product, ProductWarehouseDTO stock, Integer warehouseId) {
        double quantity = stock != null && stock.getQuantity() != null ? stock.getQuantity() : 0;
        BigDecimal factor = product.getUnitFactor();
        Double price = null;
        String source = "unavailable";
        if (quantity > 0 && stock.getInventoryValue() != null) {
            price = stock.getInventoryValue().divide(BigDecimal.valueOf(quantity), 8,
                    java.math.RoundingMode.HALF_UP).doubleValue();
            source = "warehouse_average";
        } else if (product.getProductPrice() != null && factor != null && factor.signum() > 0) {
            price = product.getProductPrice().divide(factor, 8, java.math.RoundingMode.HALF_UP).doubleValue();
            source = "default_price_no_stock";
        }

        return IngredientDTO.builder()
                .id(String.valueOf(product.getProductId()))
                .name(product.getProductName())
                .displayName(product.getProductName())
                .category(getIngredientCategory(product.getProductName()))
                .unit(product.getBaseUnit())
                .costPerUnit(price)
                .currentStock(warehouseId == null ? null : quantity)
                .warehouseId(warehouseId)
                .costSource(source)
                .minStockLevel(0.0)
                .isActive(true)
                .isSeasonal(false)
                .allergies(getAllergiesByIngredient(product.getProductName()))
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
                .and(ORDER_CANCELLED_AT.isNull())
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
