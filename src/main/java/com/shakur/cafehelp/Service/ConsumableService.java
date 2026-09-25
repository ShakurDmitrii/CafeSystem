package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.ConsumableRuleDTO;
import com.shakur.cafehelp.DTO.OrderConsumableDTO;
import com.shakur.cafehelp.DTO.OrderDishDTO;
import com.shakur.cafehelp.exception.InvalidOrderRequestException;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ConsumableService {
    private static final int SCALE = 6;
    private static final Table<?> PRODUCT = DSL.table(DSL.name("sales", "product"));
    private static final Field<Integer> PRODUCT_ID = DSL.field(DSL.name("productid"), Integer.class);
    private static final Field<String> PRODUCT_NAME = DSL.field(DSL.name("productname"), String.class);
    private static final Field<String> ITEM_TYPE = DSL.field(DSL.name("item_type"), String.class);
    private static final Field<String> UNIT = DSL.field(DSL.name("unit"), String.class);
    private static final Field<String> BASE_UNIT = DSL.field(DSL.name("base_unit"), String.class);
    private static final Field<BigDecimal> UNIT_FACTOR = DSL.field(DSL.name("unit_factor"), BigDecimal.class);

    private static final Table<?> RULE = DSL.table(DSL.name("sales", "consumable_rule"));
    private static final Field<Integer> RULE_PRODUCT_ID = DSL.field(DSL.name("product_id"), Integer.class);
    private static final Field<String> BASIS = DSL.field(DSL.name("basis"), String.class);
    private static final Field<BigDecimal> DEFAULT_QUANTITY = DSL.field(DSL.name("default_quantity"), BigDecimal.class);
    private static final Field<BigDecimal> TRIGGER_QUANTITY = DSL.field(DSL.name("trigger_quantity"), BigDecimal.class);
    private static final Field<Integer> CATEGORY_ID = DSL.field(DSL.name("dish_category_id"), Integer.class);
    private static final Field<Boolean> ACTIVE = DSL.field(DSL.name("active"), Boolean.class);
    private static final Field<LocalDateTime> UPDATED_AT = DSL.field(DSL.name("updated_at"), LocalDateTime.class);

    private static final Table<?> CATEGORY = DSL.table(DSL.name("sales", "dish_category"));
    private static final Field<Integer> DC_ID = DSL.field(DSL.name("category_id"), Integer.class);
    private static final Field<String> DC_NAME = DSL.field(DSL.name("name"), String.class);

    private static final Table<?> ORDER_CONSUMABLE = DSL.table(DSL.name("sales", "order_consumable"));
    private static final Field<Long> OC_ID = DSL.field(DSL.name("id"), Long.class);
    private static final Field<Integer> OC_ORDER_ID = DSL.field(DSL.name("order_id"), Integer.class);
    private static final Field<Integer> OC_PRODUCT_ID = DSL.field(DSL.name("product_id"), Integer.class);
    private static final Field<BigDecimal> OC_SUGGESTED = DSL.field(DSL.name("suggested_quantity"), BigDecimal.class);
    private static final Field<BigDecimal> OC_ACTUAL = DSL.field(DSL.name("actual_quantity"), BigDecimal.class);
    private static final Field<BigDecimal> OC_SURCHARGE = DSL.field(DSL.name("surcharge_amount"), BigDecimal.class);
    private static final Field<BigDecimal> OC_INVENTORY_COST = DSL.field(DSL.name("inventory_cost"), BigDecimal.class);
    private static final Field<String> OC_NAME = DSL.field(DSL.name("product_name_snapshot"), String.class);
    private static final Field<String> OC_UNIT = DSL.field(DSL.name("base_unit_snapshot"), String.class);
    private static final Field<Boolean> OC_MANUAL = DSL.field(DSL.name("manual_override"), Boolean.class);
    private static final Field<String> OC_CREATED_BY = DSL.field(DSL.name("created_by"), String.class);
    private static final Field<LocalDateTime> OC_CREATED_AT = DSL.field(DSL.name("created_at"), LocalDateTime.class);
    private static final Field<LocalDateTime> OC_UPDATED_AT = DSL.field(DSL.name("updated_at"), LocalDateTime.class);

    private final DSLContext dsl;
    private final WareHouseService wareHouseService;
    private final InventoryValuationService valuationService;

    public ConsumableService(DSLContext dsl, WareHouseService wareHouseService, InventoryValuationService valuationService) {
        this.dsl = dsl;
        this.wareHouseService = wareHouseService;
        this.valuationService = valuationService;
    }

    public List<ConsumableRuleDTO> getCatalog() {
        Field<String> categoryName = DC_NAME.as("category_name");
        return dsl.select(
                        PRODUCT_ID, PRODUCT_NAME, ITEM_TYPE, UNIT, BASE_UNIT, UNIT_FACTOR,
                        BASIS, DEFAULT_QUANTITY, TRIGGER_QUANTITY, CATEGORY_ID, ACTIVE, categoryName
                )
                .from(PRODUCT)
                .leftJoin(RULE).on(RULE_PRODUCT_ID.eq(PRODUCT_ID))
                .leftJoin(CATEGORY).on(DC_ID.eq(CATEGORY_ID))
                .where(ITEM_TYPE.in("consumable", "packaging"))
                .orderBy(PRODUCT_NAME.asc())
                .fetch(record -> toRuleDto(record, categoryName));
    }

    @Transactional
    public ConsumableRuleDTO configure(int productId, ConsumableRuleDTO dto) {
        if (dto == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Настройки расходника обязательны");
        String itemType = normalizeItemType(dto.getItemType());
        if (!"ingredient".equals(itemType)) {
            Table<?> techProduct = DSL.table(DSL.name("sales", "techproduct"));
            Field<Integer> recipeProductId = DSL.field(DSL.name("productid"), Integer.class);
            if (dsl.fetchExists(dsl.selectOne().from(techProduct).where(recipeProductId.eq(productId)))) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Продукт используется в рецепте. Сначала удалите его из техкарт"
                );
            }
        }
        int updated = dsl.update(PRODUCT)
                .set(ITEM_TYPE, itemType)
                .where(PRODUCT_ID.eq(productId))
                .execute();
        if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Складской товар не найден");

        if ("ingredient".equals(itemType)) {
            dsl.deleteFrom(RULE).where(RULE_PRODUCT_ID.eq(productId)).execute();
            dto.setProductId(productId);
            dto.setItemType(itemType);
            dto.setActive(false);
            return dto;
        }

        String basis = normalizeBasis(dto.getBasis());
        BigDecimal defaultQuantity = nonNegative(dto.getDefaultQuantity(), "Количество по умолчанию");
        BigDecimal triggerQuantity = positive(dto.getTriggerQuantity(), "Шаг правила");
        Boolean active = dto.getActive() == null || dto.getActive();
        Integer categoryId = "per_menu_item".equals(basis) ? dto.getDishCategoryId() : null;
        if (categoryId != null && !dsl.fetchExists(dsl.selectOne().from(CATEGORY).where(DC_ID.eq(categoryId)))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Категория блюд не найдена");
        }
        dsl.insertInto(RULE)
                .columns(RULE_PRODUCT_ID, BASIS, DEFAULT_QUANTITY, TRIGGER_QUANTITY, CATEGORY_ID, ACTIVE, UPDATED_AT)
                .values(productId, basis, defaultQuantity, triggerQuantity, categoryId, active, LocalDateTime.now())
                .onConflict(RULE_PRODUCT_ID)
                .doUpdate()
                .set(BASIS, basis)
                .set(DEFAULT_QUANTITY, defaultQuantity)
                .set(TRIGGER_QUANTITY, triggerQuantity)
                .set(CATEGORY_ID, categoryId)
                .set(ACTIVE, active)
                .set(UPDATED_AT, LocalDateTime.now())
                .execute();
        return getCatalog().stream()
                .filter(row -> row.getProductId() == productId)
                .findFirst()
                .orElseThrow();
    }

    public PreparedConsumables prepare(
            Integer requestedPersonCount,
            List<OrderDishDTO> orderItems,
            List<OrderConsumableDTO> requestedConsumables,
            String createdBy
    ) {
        int personCount = requestedPersonCount != null ? requestedPersonCount : 1;
        if (personCount < 1 || personCount > 1000) {
            throw new InvalidOrderRequestException("Количество персон должно быть от 1 до 1000");
        }

        Map<Integer, ConsumableRuleDTO> catalog = new LinkedHashMap<>();
        for (ConsumableRuleDTO row : getCatalog()) catalog.put(row.getProductId(), row);
        Map<Integer, OrderConsumableDTO> requested = new LinkedHashMap<>();
        if (requestedConsumables != null) {
            if (requestedConsumables.size() > 200) {
                throw new InvalidOrderRequestException("В заказе не может быть больше 200 расходников");
            }
            for (OrderConsumableDTO row : requestedConsumables) {
                if (row == null || row.getProductId() == null || requested.put(row.getProductId(), row) != null) {
                    throw new InvalidOrderRequestException("Расходники заказа содержат пустую или повторяющуюся позицию");
                }
            }
        }

        List<PreparedConsumable> rows = new ArrayList<>();
        BigDecimal surchargeTotal = BigDecimal.ZERO;
        for (ConsumableRuleDTO product : catalog.values()) {
            boolean ruleActive = Boolean.TRUE.equals(product.getActive()) && product.getBasis() != null;
            OrderConsumableDTO override = requested.remove(product.getProductId());
            if (!ruleActive && override == null) continue;
            BigDecimal suggested = ruleActive
                    ? calculateSuggested(product, personCount, orderItems)
                    : BigDecimal.ZERO.setScale(SCALE);
            BigDecimal actual = override != null && override.getActualQuantity() != null
                    ? nonNegative(override.getActualQuantity(), "Фактическое количество расходника")
                    : suggested;
            BigDecimal surcharge = override != null && override.getSurchargeAmount() != null
                    ? nonNegative(override.getSurchargeAmount(), "Доплата за расходник").setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2);
            if (actual.compareTo(new BigDecimal("1000000")) > 0 || surcharge.compareTo(new BigDecimal("1000000")) > 0) {
                throw new InvalidOrderRequestException("Количество или доплата за расходник превышает допустимый предел");
            }
            boolean manual = override != null && (actual.compareTo(suggested) != 0 || surcharge.signum() > 0);
            if (actual.signum() > 0 || surcharge.signum() > 0 || suggested.signum() > 0) {
                rows.add(new PreparedConsumable(
                        product.getProductId(), product.getProductName(), product.getItemType(), product.getBaseUnit(),
                        suggested, actual, surcharge, manual, normalizeCreatedBy(createdBy)
                ));
                surchargeTotal = surchargeTotal.add(surcharge);
            }
        }
        if (!requested.isEmpty()) {
            throw new InvalidOrderRequestException("Выбран товар, который не является расходником");
        }
        return new PreparedConsumables(personCount, List.copyOf(rows), surchargeTotal.setScale(2, RoundingMode.HALF_UP));
    }

    public List<OrderConsumableDTO> toDtos(PreparedConsumables prepared) {
        return prepared.rows().stream().map(row -> {
            OrderConsumableDTO dto = new OrderConsumableDTO();
            dto.setProductId(row.productId());
            dto.setProductName(row.productName());
            dto.setItemType(row.itemType());
            dto.setBaseUnit(row.baseUnit());
            dto.setSuggestedQuantity(row.suggestedQuantity());
            dto.setActualQuantity(row.actualQuantity());
            dto.setSurchargeAmount(row.surchargeAmount());
            dto.setManualOverride(row.manualOverride());
            return dto;
        }).toList();
    }

    @Transactional
    public void replaceForOrder(int orderId, PreparedConsumables prepared) {
        dsl.deleteFrom(ORDER_CONSUMABLE).where(OC_ORDER_ID.eq(orderId)).execute();
        LocalDateTime now = LocalDateTime.now();
        for (PreparedConsumable row : prepared.rows()) {
            dsl.insertInto(ORDER_CONSUMABLE)
                    .columns(
                            OC_ORDER_ID, OC_PRODUCT_ID, OC_SUGGESTED, OC_ACTUAL, OC_SURCHARGE,
                            OC_NAME, OC_UNIT, OC_MANUAL, OC_CREATED_BY, OC_CREATED_AT, OC_UPDATED_AT
                    )
                    .values(
                            orderId, row.productId(), row.suggestedQuantity(), row.actualQuantity(), row.surchargeAmount(),
                            row.productName(), row.baseUnit(), row.manualOverride(), row.createdBy(), now, now
                    )
                    .execute();
        }
    }

    public List<OrderConsumableDTO> getForOrder(int orderId) {
        return getForOrders(List.of(orderId)).getOrDefault(orderId, List.of());
    }

    public Map<Integer, List<OrderConsumableDTO>> getForOrders(List<Integer> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) return Map.of();
        Map<Integer, List<OrderConsumableDTO>> result = new LinkedHashMap<>();
        dsl.select(
                        OC_ORDER_ID,
                        OC_PRODUCT_ID, OC_NAME, OC_UNIT, OC_SUGGESTED, OC_ACTUAL,
                        OC_SURCHARGE, OC_INVENTORY_COST, OC_MANUAL, ITEM_TYPE
                )
                .from(ORDER_CONSUMABLE)
                .join(PRODUCT).on(PRODUCT_ID.eq(OC_PRODUCT_ID))
                .where(OC_ORDER_ID.in(orderIds))
                .orderBy(OC_ORDER_ID.asc(), OC_ID.asc())
                .fetch()
                .forEach(record -> {
                    result.computeIfAbsent(record.get(OC_ORDER_ID), ignored -> new ArrayList<>())
                            .add(toOrderConsumableDto(record));
                });
        return result;
    }

    private OrderConsumableDTO toOrderConsumableDto(Record record) {
        OrderConsumableDTO dto = new OrderConsumableDTO();
        dto.setProductId(record.get(OC_PRODUCT_ID));
        dto.setProductName(record.get(OC_NAME));
        dto.setItemType(record.get(ITEM_TYPE));
        dto.setBaseUnit(record.get(OC_UNIT));
        dto.setSuggestedQuantity(record.get(OC_SUGGESTED));
        dto.setActualQuantity(record.get(OC_ACTUAL));
        dto.setSurchargeAmount(record.get(OC_SURCHARGE));
        dto.setInventoryCost(record.get(OC_INVENTORY_COST));
        dto.setManualOverride(record.get(OC_MANUAL));
        return dto;
    }

    @Transactional
    public void writeOffForOrder(int orderId) {
        Integer warehouseId = wareHouseService.getMainWarehouseId();
        if (warehouseId == null) return;
        var rows = dsl.select(OC_ID, OC_PRODUCT_ID, OC_ACTUAL)
                .from(ORDER_CONSUMABLE)
                .where(OC_ORDER_ID.eq(orderId))
                .orderBy(OC_ID.asc())
                .forUpdate()
                .fetch();
        for (Record row : rows) {
            BigDecimal quantity = row.get(OC_ACTUAL);
            if (quantity == null || quantity.signum() <= 0) continue;
            InventoryValuationService.ValuationChange change = valuationService.issueAvailableAndRecord(
                    warehouseId, row.get(OC_PRODUCT_ID), quantity, "order_consumable", orderId, "order-service"
            );
            dsl.update(ORDER_CONSUMABLE)
                    .set(OC_INVENTORY_COST, change.value())
                    .set(OC_UPDATED_AT, LocalDateTime.now())
                    .where(OC_ID.eq(row.get(OC_ID)))
                    .execute();
        }
    }

    private BigDecimal calculateSuggested(ConsumableRuleDTO rule, int personCount, List<OrderDishDTO> items) {
        long menuItemCount = "per_menu_item".equals(rule.getBasis())
                ? countMenuItems(items, rule.getDishCategoryId())
                : 0;
        return calculateRuleQuantity(
                rule.getBasis(), rule.getDefaultQuantity(), rule.getTriggerQuantity(), personCount, menuItemCount
        );
    }

    public static BigDecimal calculateRuleQuantity(
            String basis,
            BigDecimal defaultQuantity,
            BigDecimal triggerQuantity,
            int personCount,
            long menuItemCount
    ) {
        BigDecimal basisCount = switch (basis != null ? basis : "") {
            case "per_person" -> BigDecimal.valueOf(personCount);
            case "per_order" -> BigDecimal.ONE;
            case "per_menu_item" -> BigDecimal.valueOf(menuItemCount);
            default -> BigDecimal.ZERO;
        };
        if (basisCount.signum() <= 0) return BigDecimal.ZERO.setScale(SCALE);
        BigDecimal trigger = triggerQuantity != null && triggerQuantity.signum() > 0
                ? triggerQuantity
                : BigDecimal.ONE;
        BigDecimal blocks = basisCount.divide(trigger, 0, RoundingMode.CEILING);
        return blocks.multiply(defaultQuantity != null ? defaultQuantity : BigDecimal.ZERO)
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    private long countMenuItems(List<OrderDishDTO> items, Integer categoryId) {
        if (items == null) return 0;
        long total = 0;
        Field<Integer> dishCategoryId = DSL.field(DSL.name("category_id"), Integer.class);
        Table<?> dish = DSL.table(DSL.name("sales", "dish"));
        Field<Integer> dishIdField = DSL.field(DSL.name("dishid"), Integer.class);
        Table<?> setItem = DSL.table(DSL.name("sales", "dish_set_item"));
        Field<Integer> setIdField = DSL.field(DSL.name("set_id"), Integer.class);
        Field<Integer> setDishId = DSL.field(DSL.name("dish_id"), Integer.class);
        Field<Integer> setQty = DSL.field(DSL.name("qty"), Integer.class);
        for (OrderDishDTO item : items) {
            if (item == null || item.getQty() <= 0) continue;
            if (item.getDishID() != null && item.getDishID() > 0) {
                if (categoryId == null || dsl.fetchExists(
                        dsl.selectOne().from(dish)
                                .where(dishIdField.eq(item.getDishID()))
                                .and(dishCategoryId.eq(categoryId))
                )) total += item.getQty();
            } else if (item.getSetId() != null && item.getSetId() > 0) {
                if (categoryId == null) {
                    total += item.getQty();
                } else {
                    Integer nested = dsl.select(DSL.coalesce(DSL.sum(setQty), 0))
                            .from(setItem)
                            .join(dish).on(dishIdField.eq(setDishId))
                            .where(setIdField.eq(item.getSetId()))
                            .and(dishCategoryId.eq(categoryId))
                            .fetchOne(0, Integer.class);
                    total += (long) (nested != null ? nested : 0) * item.getQty();
                }
            }
        }
        return total;
    }

    private ConsumableRuleDTO toRuleDto(Record record, Field<String> categoryName) {
        ConsumableRuleDTO dto = new ConsumableRuleDTO();
        dto.setProductId(record.get(PRODUCT_ID));
        dto.setProductName(record.get(PRODUCT_NAME));
        dto.setItemType(record.get(ITEM_TYPE));
        dto.setUnit(record.get(UNIT));
        dto.setBaseUnit(record.get(BASE_UNIT));
        dto.setUnitFactor(record.get(UNIT_FACTOR));
        dto.setBasis(record.get(BASIS));
        dto.setDefaultQuantity(record.get(DEFAULT_QUANTITY));
        dto.setTriggerQuantity(record.get(TRIGGER_QUANTITY));
        dto.setDishCategoryId(record.get(CATEGORY_ID));
        dto.setDishCategoryName(record.get(categoryName));
        dto.setActive(record.get(ACTIVE));
        return dto;
    }

    private String normalizeItemType(String value) {
        String normalized = value == null ? "ingredient" : value.trim().toLowerCase(Locale.ROOT);
        if (!List.of("ingredient", "consumable", "packaging").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неизвестный тип складского товара");
        }
        return normalized;
    }

    private String normalizeBasis(String value) {
        String normalized = value == null ? "per_order" : value.trim().toLowerCase(Locale.ROOT);
        if (!List.of("per_person", "per_order", "per_menu_item").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неизвестное правило комплектации");
        }
        return normalized;
    }

    private BigDecimal nonNegative(BigDecimal value, String label) {
        BigDecimal normalized = value != null ? value : BigDecimal.ZERO;
        if (normalized.signum() < 0) throw new InvalidOrderRequestException(label + " не может быть отрицательным");
        return normalized.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal positive(BigDecimal value, String label) {
        if (value == null || value.signum() <= 0) throw new InvalidOrderRequestException(label + " должен быть больше нуля");
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private String normalizeCreatedBy(String value) {
        if (value == null || value.isBlank()) return "order-api";
        String normalized = value.trim();
        return normalized.length() <= 100 ? normalized : normalized.substring(0, 100);
    }

    public record PreparedConsumables(int personCount, List<PreparedConsumable> rows, BigDecimal surchargeTotal) {}

    public record PreparedConsumable(
            int productId,
            String productName,
            String itemType,
            String baseUnit,
            BigDecimal suggestedQuantity,
            BigDecimal actualQuantity,
            BigDecimal surchargeAmount,
            boolean manualOverride,
            String createdBy
    ) {}
}
