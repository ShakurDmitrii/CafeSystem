package com.shakur.cafehelp.Service;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Keeps physical quantity and monetary value of a product balance in sync.
 * All quantities and unit costs are expressed in the product's base unit.
 */
@Service
public class InventoryValuationService {
    private static final Table<?> PRODUCT_WAREHOUSE = DSL.table(DSL.name("sales", "productwarehouse"));
    private static final Field<Integer> WAREHOUSE_ID = DSL.field(DSL.name("warehouseid"), Integer.class);
    private static final Field<Integer> PRODUCT_ID = DSL.field(DSL.name("productid"), Integer.class);
    private static final Field<Double> QUANTITY = DSL.field(DSL.name("quantity"), Double.class);
    private static final Field<BigDecimal> INVENTORY_VALUE = DSL.field(DSL.name("inventory_value"), BigDecimal.class);
    private static final Field<BigDecimal> AVERAGE_UNIT_COST = DSL.field(DSL.name("average_unit_cost"), BigDecimal.class);
    private static final Field<Boolean> VALUATION_INITIALIZED = DSL.field(DSL.name("valuation_initialized"), Boolean.class);
    private static final Field<LocalDateTime> VALUATION_UPDATED_AT = DSL.field(DSL.name("valuation_updated_at"), LocalDateTime.class);

    private static final Table<?> STOCK_MOVEMENTS = DSL.table(DSL.name("sales", "stock_movements"));
    private static final Field<LocalDateTime> MOVEMENT_DATE = DSL.field(DSL.name("movement_date"), LocalDateTime.class);
    private static final Field<Integer> MOVEMENT_DOCUMENT_ID = DSL.field(DSL.name("document_id"), Integer.class);
    private static final Field<Integer> MOVEMENT_WAREHOUSE_ID = DSL.field(DSL.name("warehouse_id"), Integer.class);
    private static final Field<Integer> MOVEMENT_PRODUCT_ID = DSL.field(DSL.name("product_id"), Integer.class);
    private static final Field<BigDecimal> MOVEMENT_QTY_IN = DSL.field(DSL.name("qty_in"), BigDecimal.class);
    private static final Field<BigDecimal> MOVEMENT_QTY_OUT = DSL.field(DSL.name("qty_out"), BigDecimal.class);
    private static final Field<BigDecimal> MOVEMENT_UNIT_COST = DSL.field(DSL.name("unit_cost"), BigDecimal.class);
    private static final Field<BigDecimal> MOVEMENT_AMOUNT = DSL.field(DSL.name("amount"), BigDecimal.class);
    private static final Field<LocalDateTime> MOVEMENT_CREATED_AT = DSL.field(DSL.name("created_at"), LocalDateTime.class);
    private static final Field<String> MOVEMENT_TYPE = DSL.field(DSL.name("movement_type"), String.class);
    private static final Field<String> SOURCE_TYPE = DSL.field(DSL.name("source_type"), String.class);
    private static final Field<Integer> SOURCE_ID = DSL.field(DSL.name("source_id"), Integer.class);
    private static final Field<String> CREATED_BY = DSL.field(DSL.name("created_by"), String.class);
    private static final Field<String> REASON = DSL.field(DSL.name("reason"), String.class);
    private static final Table<?> PRODUCT = DSL.table(DSL.name("sales", "product"));
    private static final Field<Integer> CARD_PRODUCT_ID = DSL.field(DSL.name("productid"), Integer.class);
    private static final Field<BigDecimal> CARD_PRICE = DSL.field(DSL.name("productprice"), BigDecimal.class);
    private static final Field<BigDecimal> CARD_UNIT_FACTOR = DSL.field(DSL.name("unit_factor"), BigDecimal.class);
    private static final Table<?> PREPARATION_WAREHOUSE = DSL.table(DSL.name("sales", "preparationwarehouse"));
    private static final Field<Integer> PREP_WAREHOUSE_ID = DSL.field(DSL.name("warehouseid"), Integer.class);
    private static final Field<Integer> PREPARATION_ID = DSL.field(DSL.name("preparationid"), Integer.class);
    private static final Field<Double> PREP_QUANTITY = DSL.field(DSL.name("quantity"), Double.class);
    private static final Field<BigDecimal> PREP_INVENTORY_VALUE = DSL.field(DSL.name("inventory_value"), BigDecimal.class);
    private static final Field<BigDecimal> PREP_AVERAGE_UNIT_COST = DSL.field(DSL.name("average_unit_cost"), BigDecimal.class);
    private static final Field<Boolean> PREP_VALUATION_INITIALIZED = DSL.field(DSL.name("valuation_initialized"), Boolean.class);
    private static final Field<LocalDateTime> PREP_VALUATION_UPDATED_AT = DSL.field(DSL.name("valuation_updated_at"), LocalDateTime.class);
    private static final Table<?> PREPARATION_MOVEMENTS = DSL.table(DSL.name("sales", "preparation_stock_movements"));

    private final DSLContext dsl;
    private final WareHouseService wareHouseService;

    public InventoryValuationService(DSLContext dsl, WareHouseService wareHouseService) {
        this.dsl = dsl;
        this.wareHouseService = wareHouseService;
    }

    @Transactional
    public ValuationChange receive(int warehouseId, int productId, BigDecimal quantity, BigDecimal receiptValue) {
        requirePositive(quantity, "Количество прихода");
        requireNonNegative(receiptValue, "Стоимость прихода");
        requireWarehouses(warehouseId);

        InventoryValuationCalculator.Balance current = lockBalance(warehouseId, productId);
        InventoryValuationCalculator.Balance updated = InventoryValuationCalculator.receive(current, quantity, receiptValue);
        wareHouseService.setProductQuantity(warehouseId, productId, updated.quantity().doubleValue());
        persistBalance(warehouseId, productId, updated);
        BigDecimal receiptUnitCost = receiptValue.divide(quantity, InventoryValuationCalculator.SCALE, RoundingMode.HALF_UP);
        return new ValuationChange(quantity, receiptValue, receiptUnitCost, updated);
    }

    @Transactional
    public ValuationChange issueExact(int warehouseId, int productId, BigDecimal quantity) {
        return issue(warehouseId, productId, quantity, false);
    }

    @Transactional
    public ValuationChange issueAvailable(int warehouseId, int productId, BigDecimal requestedQuantity) {
        return issue(warehouseId, productId, requestedQuantity, true);
    }

    @Transactional
    public ValuationChange transfer(int fromWarehouseId, int toWarehouseId, int productId, BigDecimal quantity) {
        requirePositive(quantity, "Количество перемещения");
        if (fromWarehouseId == toWarehouseId) {
            throw new IllegalArgumentException("Склады перемещения должны отличаться");
        }
        requireWarehouses(fromWarehouseId, toWarehouseId);

        InventoryValuationCalculator.Balance source;
        InventoryValuationCalculator.Balance destination;
        if (fromWarehouseId < toWarehouseId) {
            source = lockBalance(fromWarehouseId, productId);
            destination = lockBalance(toWarehouseId, productId);
        } else {
            destination = lockBalance(toWarehouseId, productId);
            source = lockBalance(fromWarehouseId, productId);
        }
        InventoryValuationCalculator.Issue issued = InventoryValuationCalculator.issue(source, quantity, false);
        InventoryValuationCalculator.Balance received = InventoryValuationCalculator.receive(
                destination,
                issued.quantity(),
                issued.value()
        );

        if (!wareHouseService.moveProduct(fromWarehouseId, toWarehouseId, productId, quantity.doubleValue())) {
            throw new IllegalArgumentException("Не удалось переместить продукт");
        }
        persistBalance(fromWarehouseId, productId, issued.remaining());
        persistBalance(toWarehouseId, productId, received);
        return new ValuationChange(issued.quantity(), issued.value(), source.averageUnitCost(), issued.remaining());
    }

    @Transactional
    public ValuationChange issueAvailableAndRecord(
            int warehouseId,
            int productId,
            BigDecimal requestedQuantity,
            String sourceType,
            Integer sourceId,
            String createdBy
    ) {
        ValuationChange change = issueAvailable(warehouseId, productId, requestedQuantity);
        if (change.quantity().signum() > 0) {
            recordMovement(warehouseId, productId, change, "sale_writeoff", sourceType, sourceId, createdBy);
        }
        return change;
    }

    @Transactional
    public ValuationChange issueExactAndRecord(
            int warehouseId,
            int productId,
            BigDecimal quantity,
            String movementType,
            String sourceType,
            Integer sourceId,
            String createdBy
    ) {
        ValuationChange change = issueExact(warehouseId, productId, quantity);
        recordMovement(warehouseId, productId, change, movementType, sourceType, sourceId, createdBy);
        return change;
    }

    public BigDecimal getAverageUnitCost(int warehouseId, int productId) {
        BigDecimal result = dsl.select(AVERAGE_UNIT_COST)
                .from(PRODUCT_WAREHOUSE)
                .where(WAREHOUSE_ID.eq(warehouseId))
                .and(PRODUCT_ID.eq(productId))
                .fetchOne(AVERAGE_UNIT_COST);
        return result != null ? result : BigDecimal.ZERO;
    }

    @Transactional
    public ValuationChange receivePreparation(
            int warehouseId,
            int preparationId,
            BigDecimal quantity,
            BigDecimal productionValue,
            String sourceType,
            Integer sourceId,
            String createdBy
    ) {
        requirePositive(quantity, "Количество заготовки");
        requireNonNegative(productionValue, "Стоимость производства");
        requireWarehouses(warehouseId);
        InventoryValuationCalculator.Balance current = lockPreparationBalance(warehouseId, preparationId);
        InventoryValuationCalculator.Balance updated = InventoryValuationCalculator.receive(
                current, quantity, productionValue
        );
        if (!wareHouseService.adjustPreparationQuantity(warehouseId, preparationId, quantity.doubleValue())) {
            throw new IllegalArgumentException("Не удалось оприходовать заготовку");
        }
        persistPreparationBalance(warehouseId, preparationId, updated);
        BigDecimal unitCost = productionValue.divide(
                quantity, InventoryValuationCalculator.SCALE, RoundingMode.HALF_UP
        );
        ValuationChange change = new ValuationChange(quantity, productionValue, unitCost, updated);
        recordPreparationMovement(
                warehouseId, preparationId, change, true, "production_receipt", sourceType, sourceId, createdBy
        );
        return change;
    }

    @Transactional
    public ValuationChange issuePreparationExact(
            int warehouseId,
            int preparationId,
            BigDecimal quantity,
            String sourceType,
            Integer sourceId,
            String createdBy
    ) {
        return issuePreparation(
                warehouseId, preparationId, quantity, false, sourceType, sourceId, createdBy
        );
    }

    @Transactional
    public ValuationChange issuePreparationAvailable(
            int warehouseId,
            int preparationId,
            BigDecimal quantity,
            String sourceType,
            Integer sourceId,
            String createdBy
    ) {
        return issuePreparation(
                warehouseId, preparationId, quantity, true, sourceType, sourceId, createdBy
        );
    }

    /**
     * Ручная корректировка остатка заготовки. Количество и стоимость меняются вместе,
     * иначе следующая выработка смешает новую себестоимость со старой стоимостью
     * уже списанного остатка.
     */
    @Transactional
    public ValuationChange adjustPreparationAtCurrentCost(
            int warehouseId,
            int preparationId,
            BigDecimal delta,
            String sourceType,
            Integer sourceId,
            String createdBy
    ) {
        if (delta == null || delta.signum() == 0) {
            throw new IllegalArgumentException("Изменение количества не может быть нулевым");
        }
        requireWarehouses(warehouseId);
        InventoryValuationCalculator.Balance current = lockPreparationBalance(warehouseId, preparationId);
        BigDecimal target = current.quantity().add(delta);
        if (target.signum() < 0) {
            throw new IllegalArgumentException("Недостаточно заготовки на складе");
        }
        InventoryValuationCalculator.Adjustment adjustment = InventoryValuationCalculator.adjust(
                current, delta, loadLastPreparationUnitCost(preparationId)
        );

        // Остаток считается от неотрицательного количества, поэтому физическое
        // количество выставляем в целевое значение, а не прибавляем delta к «минусу».
        double physicalQuantity = loadPreparationPhysicalQuantity(warehouseId, preparationId);
        double physicalDelta = target.doubleValue() - physicalQuantity;
        if (Math.abs(physicalDelta) > 0.000001d
                && !wareHouseService.adjustPreparationQuantity(warehouseId, preparationId, physicalDelta)) {
            throw new IllegalArgumentException("Не удалось скорректировать количество заготовки");
        }
        persistPreparationBalance(warehouseId, preparationId, adjustment.updated());

        ValuationChange change = new ValuationChange(
                adjustment.quantity(), adjustment.value(), adjustment.unitCost(), adjustment.updated()
        );
        recordPreparationMovement(
                warehouseId, preparationId, change, adjustment.incoming(),
                "inventory_adjustment", sourceType, sourceId, createdBy
        );
        return change;
    }

    private double loadPreparationPhysicalQuantity(int warehouseId, int preparationId) {
        Double quantity = dsl.select(DSL.coalesce(DSL.sum(PREP_QUANTITY), BigDecimal.ZERO))
                .from(PREPARATION_WAREHOUSE)
                .where(PREP_WAREHOUSE_ID.eq(warehouseId))
                .and(PREPARATION_ID.eq(preparationId))
                .fetchOne(0, Double.class);
        return quantity != null ? quantity : 0.0;
    }

    private BigDecimal loadLastPreparationUnitCost(int preparationId) {
        Field<Integer> preparationField = DSL.field(DSL.name("preparation_id"), Integer.class);
        BigDecimal unitCost = dsl.select(MOVEMENT_UNIT_COST)
                .from(PREPARATION_MOVEMENTS)
                .where(preparationField.eq(preparationId))
                .and(MOVEMENT_TYPE.eq("production_receipt"))
                .and(MOVEMENT_UNIT_COST.gt(BigDecimal.ZERO))
                .orderBy(MOVEMENT_DATE.desc(), DSL.field(DSL.name("id")).desc())
                .limit(1)
                .fetchOne(MOVEMENT_UNIT_COST);
        return unitCost != null ? unitCost : BigDecimal.ZERO;
    }

    @Transactional
    public RevaluationResult revalue(
            int warehouseId,
            int productId,
            BigDecimal newAverageUnitCost,
            String reason,
            String createdBy
    ) {
        requireNonNegative(newAverageUnitCost, "Средняя стоимость");
        String normalizedReason = reason != null ? reason.trim() : "";
        if (normalizedReason.isEmpty()) {
            throw new IllegalArgumentException("Укажите причину переоценки");
        }
        if (normalizedReason.length() > 1000) {
            throw new IllegalArgumentException("Причина переоценки не должна превышать 1000 символов");
        }
        String normalizedCreatedBy = normalizeCreatedBy(createdBy);
        requireWarehouses(warehouseId);
        InventoryValuationCalculator.Balance current = lockBalance(warehouseId, productId);
        if (current.quantity().signum() <= 0) {
            throw new IllegalArgumentException("На складе нет остатка для переоценки");
        }

        BigDecimal newValue = current.quantity()
                .multiply(newAverageUnitCost)
                .setScale(InventoryValuationCalculator.SCALE, RoundingMode.HALF_UP);
        InventoryValuationCalculator.Balance updated = InventoryValuationCalculator.balance(current.quantity(), newValue);
        persistBalance(warehouseId, productId, updated);

        BigDecimal valueDelta = updated.value().subtract(current.value());
        LocalDateTime now = LocalDateTime.now();
        dsl.insertInto(STOCK_MOVEMENTS)
                .columns(
                        MOVEMENT_DATE, MOVEMENT_DOCUMENT_ID, MOVEMENT_WAREHOUSE_ID, MOVEMENT_PRODUCT_ID,
                        MOVEMENT_QTY_IN, MOVEMENT_QTY_OUT, MOVEMENT_UNIT_COST, MOVEMENT_AMOUNT,
                        MOVEMENT_CREATED_AT, MOVEMENT_TYPE, SOURCE_TYPE, SOURCE_ID, CREATED_BY, REASON
                )
                .values(
                        now, null, warehouseId, productId,
                        BigDecimal.ZERO, BigDecimal.ZERO, updated.averageUnitCost(), valueDelta,
                        now, "revaluation", "product", productId, normalizedCreatedBy, normalizedReason
                )
                .execute();
        return new RevaluationResult(current, updated, valueDelta);
    }

    @Transactional
    public ValuationChange adjustAtCurrentCost(
            int warehouseId,
            int productId,
            BigDecimal delta,
            String sourceType,
            Integer sourceId,
            String reason,
            String createdBy
    ) {
        if (delta == null || delta.signum() == 0) {
            throw new IllegalArgumentException("Изменение количества не может быть нулевым");
        }
        requireWarehouses(warehouseId);
        InventoryValuationCalculator.Balance current = lockBalance(warehouseId, productId);
        BigDecimal target = current.quantity().add(delta);
        if (target.signum() < 0) {
            throw new IllegalArgumentException("Недостаточно продукта на складе");
        }
        return setPhysicalQuantityLocked(
                warehouseId, productId, current, target, sourceType, sourceId, reason, createdBy
        );
    }

    @Transactional
    public ValuationChange setPhysicalQuantity(
            int warehouseId,
            int productId,
            BigDecimal targetQuantity,
            String sourceType,
            Integer sourceId,
            String reason,
            String createdBy
    ) {
        requireNonNegative(targetQuantity, "Фактическое количество");
        requireWarehouses(warehouseId);
        InventoryValuationCalculator.Balance current = lockBalance(warehouseId, productId);
        return setPhysicalQuantityLocked(
                warehouseId, productId, current, targetQuantity, sourceType, sourceId, reason, createdBy
        );
    }

    private ValuationChange setPhysicalQuantityLocked(
            int warehouseId,
            int productId,
            InventoryValuationCalculator.Balance current,
            BigDecimal targetQuantity,
            String sourceType,
            Integer sourceId,
            String reason,
            String createdBy
    ) {
        BigDecimal delta = targetQuantity.subtract(current.quantity());
        if (delta.signum() == 0) {
            return new ValuationChange(BigDecimal.ZERO, BigDecimal.ZERO, current.averageUnitCost(), current);
        }

        boolean incoming = delta.signum() > 0;
        BigDecimal changedQuantity = delta.abs();
        BigDecimal unitCost = current.averageUnitCost().signum() > 0
                ? current.averageUnitCost()
                : loadDefaultBaseUnitCost(productId);
        BigDecimal changedValue = changedQuantity.multiply(unitCost)
                .setScale(InventoryValuationCalculator.SCALE, RoundingMode.HALF_UP);
        InventoryValuationCalculator.Balance updated = incoming
                ? InventoryValuationCalculator.receive(current, changedQuantity, changedValue)
                : InventoryValuationCalculator.issue(current, changedQuantity, false).remaining();

        wareHouseService.setProductQuantity(warehouseId, productId, updated.quantity().doubleValue());
        persistBalance(warehouseId, productId, updated);
        ValuationChange change = new ValuationChange(changedQuantity, changedValue, unitCost, updated);
        recordAdjustmentMovement(
                warehouseId, productId, change, incoming, sourceType, sourceId, reason, createdBy
        );
        return change;
    }

    private BigDecimal loadDefaultBaseUnitCost(int productId) {
        var record = dsl.select(CARD_PRICE, CARD_UNIT_FACTOR)
                .from(PRODUCT)
                .where(CARD_PRODUCT_ID.eq(productId))
                .fetchOne();
        if (record == null) return BigDecimal.ZERO;
        BigDecimal price = record.get(CARD_PRICE) != null ? record.get(CARD_PRICE) : BigDecimal.ZERO;
        BigDecimal factor = record.get(CARD_UNIT_FACTOR) != null && record.get(CARD_UNIT_FACTOR).signum() > 0
                ? record.get(CARD_UNIT_FACTOR)
                : BigDecimal.ONE;
        return price.divide(factor, InventoryValuationCalculator.SCALE, RoundingMode.HALF_UP);
    }

    private void recordAdjustmentMovement(
            int warehouseId,
            int productId,
            ValuationChange change,
            boolean incoming,
            String sourceType,
            Integer sourceId,
            String reason,
            String createdBy
    ) {
        LocalDateTime now = LocalDateTime.now();
        dsl.insertInto(STOCK_MOVEMENTS)
                .columns(
                        MOVEMENT_DATE, MOVEMENT_DOCUMENT_ID, MOVEMENT_WAREHOUSE_ID, MOVEMENT_PRODUCT_ID,
                        MOVEMENT_QTY_IN, MOVEMENT_QTY_OUT, MOVEMENT_UNIT_COST, MOVEMENT_AMOUNT,
                        MOVEMENT_CREATED_AT, MOVEMENT_TYPE, SOURCE_TYPE, SOURCE_ID, CREATED_BY, REASON
                )
                .values(
                        now, null, warehouseId, productId,
                        incoming ? change.quantity() : BigDecimal.ZERO,
                        incoming ? BigDecimal.ZERO : change.quantity(),
                        change.unitCost(), change.value(), now,
                        "inventory_adjustment", sourceType, sourceId, createdBy, reason
                )
                .execute();
    }

    private ValuationChange issue(int warehouseId, int productId, BigDecimal requestedQuantity, boolean allowPartial) {
        requirePositive(requestedQuantity, "Количество списания");
        requireWarehouses(warehouseId);
        InventoryValuationCalculator.Balance current = lockBalance(warehouseId, productId);
        InventoryValuationCalculator.Issue issued = InventoryValuationCalculator.issue(current, requestedQuantity, allowPartial);
        wareHouseService.setProductQuantity(warehouseId, productId, issued.remaining().quantity().doubleValue());
        persistBalance(warehouseId, productId, issued.remaining());
        return new ValuationChange(issued.quantity(), issued.value(), current.averageUnitCost(), issued.remaining());
    }

    private InventoryValuationCalculator.Balance lockBalance(int warehouseId, int productId) {
        var record = dsl.select(QUANTITY, INVENTORY_VALUE, VALUATION_INITIALIZED)
                .from(PRODUCT_WAREHOUSE)
                .where(WAREHOUSE_ID.eq(warehouseId))
                .and(PRODUCT_ID.eq(productId))
                .forUpdate()
                .fetchOne();
        if (record == null) {
            return InventoryValuationCalculator.balance(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        BigDecimal quantity = BigDecimal.valueOf(record.get(QUANTITY) != null ? Math.max(0, record.get(QUANTITY)) : 0);
        if (!Boolean.TRUE.equals(record.get(VALUATION_INITIALIZED))) {
            BigDecimal openingValue = quantity.multiply(loadDefaultBaseUnitCost(productId))
                    .setScale(InventoryValuationCalculator.SCALE, RoundingMode.HALF_UP);
            InventoryValuationCalculator.Balance opening = InventoryValuationCalculator.balance(quantity, openingValue);
            persistBalance(warehouseId, productId, opening);
            return opening;
        }
        BigDecimal value = record.get(INVENTORY_VALUE) != null ? record.get(INVENTORY_VALUE).max(BigDecimal.ZERO) : BigDecimal.ZERO;
        return InventoryValuationCalculator.balance(quantity, value);
    }

    private InventoryValuationCalculator.Balance lockPreparationBalance(int warehouseId, int preparationId) {
        var record = dsl.select(PREP_QUANTITY, PREP_INVENTORY_VALUE)
                .from(PREPARATION_WAREHOUSE)
                .where(PREP_WAREHOUSE_ID.eq(warehouseId))
                .and(PREPARATION_ID.eq(preparationId))
                .forUpdate()
                .fetchOne();
        if (record == null) {
            return InventoryValuationCalculator.balance(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        BigDecimal quantity = BigDecimal.valueOf(
                record.get(PREP_QUANTITY) != null ? Math.max(0, record.get(PREP_QUANTITY)) : 0
        );
        BigDecimal value = record.get(PREP_INVENTORY_VALUE) != null
                ? record.get(PREP_INVENTORY_VALUE).max(BigDecimal.ZERO)
                : BigDecimal.ZERO;
        return InventoryValuationCalculator.balance(quantity, value);
    }

    private void persistBalance(int warehouseId, int productId, InventoryValuationCalculator.Balance balance) {
        int updated = dsl.update(PRODUCT_WAREHOUSE)
                .set(INVENTORY_VALUE, balance.value())
                .set(AVERAGE_UNIT_COST, balance.averageUnitCost())
                .set(VALUATION_INITIALIZED, true)
                .set(VALUATION_UPDATED_AT, LocalDateTime.now())
                .where(WAREHOUSE_ID.eq(warehouseId))
                .and(PRODUCT_ID.eq(productId))
                .execute();
        if (updated == 0 && balance.quantity().signum() > 0) {
            throw new IllegalStateException("Остаток продукта не создан");
        }
    }

    private void persistPreparationBalance(
            int warehouseId,
            int preparationId,
            InventoryValuationCalculator.Balance balance
    ) {
        int updated = dsl.update(PREPARATION_WAREHOUSE)
                .set(PREP_INVENTORY_VALUE, balance.value())
                .set(PREP_AVERAGE_UNIT_COST, balance.averageUnitCost())
                .set(PREP_VALUATION_INITIALIZED, true)
                .set(PREP_VALUATION_UPDATED_AT, LocalDateTime.now())
                .where(PREP_WAREHOUSE_ID.eq(warehouseId))
                .and(PREPARATION_ID.eq(preparationId))
                .execute();
        if (updated == 0 && balance.quantity().signum() > 0) {
            throw new IllegalStateException("Остаток заготовки не создан");
        }
    }

    private ValuationChange issuePreparation(
            int warehouseId,
            int preparationId,
            BigDecimal requestedQuantity,
            boolean allowPartial,
            String sourceType,
            Integer sourceId,
            String createdBy
    ) {
        requirePositive(requestedQuantity, "Количество списания заготовки");
        requireWarehouses(warehouseId);
        InventoryValuationCalculator.Balance current = lockPreparationBalance(warehouseId, preparationId);
        InventoryValuationCalculator.Issue issued = InventoryValuationCalculator.issue(
                current, requestedQuantity, allowPartial
        );
        boolean adjusted;
        if (allowPartial) {
            double consumed = wareHouseService.consumeAvailablePreparationQuantity(
                    warehouseId, preparationId, requestedQuantity.doubleValue()
            );
            adjusted = Math.abs(consumed - issued.quantity().doubleValue()) < 0.000001d;
        } else {
            adjusted = wareHouseService.adjustPreparationQuantity(
                    warehouseId, preparationId, -issued.quantity().doubleValue()
            );
        }
        if (!adjusted) {
            throw new IllegalArgumentException("Не удалось списать заготовку");
        }
        persistPreparationBalance(warehouseId, preparationId, issued.remaining());
        ValuationChange change = new ValuationChange(
                issued.quantity(), issued.value(), current.averageUnitCost(), issued.remaining()
        );
        if (change.quantity().signum() > 0) {
            recordPreparationMovement(
                    warehouseId, preparationId, change, false, "writeoff", sourceType, sourceId, createdBy
            );
        }
        return change;
    }

    private void recordPreparationMovement(
            int warehouseId,
            int preparationId,
            ValuationChange change,
            boolean incoming,
            String movementType,
            String sourceType,
            Integer sourceId,
            String createdBy
    ) {
        Field<Integer> warehouseField = DSL.field(DSL.name("warehouse_id"), Integer.class);
        Field<Integer> preparationField = DSL.field(DSL.name("preparation_id"), Integer.class);
        LocalDateTime now = LocalDateTime.now();
        dsl.insertInto(PREPARATION_MOVEMENTS)
                .columns(
                        MOVEMENT_DATE, warehouseField, preparationField,
                        MOVEMENT_QTY_IN, MOVEMENT_QTY_OUT, MOVEMENT_UNIT_COST, MOVEMENT_AMOUNT,
                        MOVEMENT_TYPE, SOURCE_TYPE, SOURCE_ID, CREATED_BY, MOVEMENT_CREATED_AT
                )
                .values(
                        now, warehouseId, preparationId,
                        incoming ? change.quantity() : BigDecimal.ZERO,
                        incoming ? BigDecimal.ZERO : change.quantity(),
                        change.unitCost(), change.value(), movementType, sourceType, sourceId, createdBy, now
                )
                .execute();
    }

    private void recordMovement(
            int warehouseId,
            int productId,
            ValuationChange change,
            String movementType,
            String sourceType,
            Integer sourceId,
            String createdBy
    ) {
        LocalDateTime now = LocalDateTime.now();
        dsl.insertInto(STOCK_MOVEMENTS)
                .columns(
                        MOVEMENT_DATE, MOVEMENT_DOCUMENT_ID, MOVEMENT_WAREHOUSE_ID, MOVEMENT_PRODUCT_ID,
                        MOVEMENT_QTY_IN, MOVEMENT_QTY_OUT, MOVEMENT_UNIT_COST, MOVEMENT_AMOUNT,
                        MOVEMENT_CREATED_AT, MOVEMENT_TYPE, SOURCE_TYPE, SOURCE_ID, CREATED_BY
                )
                .values(
                        now, null, warehouseId, productId,
                        BigDecimal.ZERO, change.quantity(), change.unitCost(), change.value(),
                        now, movementType, sourceType, sourceId, createdBy
                )
                .execute();
    }

    private void requirePositive(BigDecimal value, String label) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(label + " должно быть больше нуля");
        }
    }

    private void requireNonNegative(BigDecimal value, String label) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(label + " не может быть отрицательной");
        }
    }

    private void requireWarehouses(int... warehouseIds) {
        if (!wareHouseService.lockWarehousesForInventory(warehouseIds)) {
            throw new IllegalArgumentException("Склад не найден");
        }
    }

    private String normalizeCreatedBy(String createdBy) {
        if (createdBy == null || createdBy.isBlank()) return "owner";
        String normalized = createdBy.trim();
        return normalized.length() <= 100 ? normalized : normalized.substring(0, 100);
    }

    public record ValuationChange(
            BigDecimal quantity,
            BigDecimal value,
            BigDecimal unitCost,
            InventoryValuationCalculator.Balance balanceAfter
    ) {
    }

    public record RevaluationResult(
            InventoryValuationCalculator.Balance before,
            InventoryValuationCalculator.Balance after,
            BigDecimal valueDelta
    ) {
    }
}
