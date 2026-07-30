package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.PreparationWarehouseDTO;
import com.shakur.cafehelp.DTO.ProductWarehouseDTO;
import com.shakur.cafehelp.DTO.WareHouseDTO;
import jooqdata.tables.records.ProductwarehouseRecord;
import jooqdata.tables.records.WarehouseRecord;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jooqdata.tables.Productwarehouse;
import java.util.List;
import java.util.ArrayList;
import java.math.BigDecimal;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;

import static jooqdata.tables.Warehouse.WAREHOUSE;

@Service
public class WareHouseService {

    private final DSLContext dsl;
    private static final Field<Boolean> WAREHOUSE_IS_MAIN = DSL.field(DSL.name("is_main"), Boolean.class);
    private static final Table<?> PREPARATION_WAREHOUSE = DSL.table(DSL.name("sales", "preparationwarehouse"));
    private static final Field<Integer> PW_WAREHOUSE_ID = DSL.field(DSL.name("warehouseid"), Integer.class);
    private static final Field<Integer> PW_PREPARATION_ID = DSL.field(DSL.name("preparationid"), Integer.class);
    private static final Field<Integer> PW_ID = DSL.field(DSL.name("preparationwarehouseid"), Integer.class);
    private static final Field<Double> PW_QUANTITY = DSL.field(DSL.name("quantity"), Double.class);

    public WareHouseService(DSLContext dsl) {
        this.dsl = dsl;
    }

    // Создание склада
    public WareHouseDTO createWareHouse(WareHouseDTO dto) {
        WarehouseRecord record = dsl.newRecord(WAREHOUSE);
        record.setWarehousename(dto.getWarehouseName());


        record.store();

        dto.setWarehouseId(record.getWarehouseid());
        dto.setMain(false);
        return dto;
    }

    // Получить все склады
    public List<WareHouseDTO> getAll() {
        return dsl.select(WAREHOUSE.WAREHOUSEID, WAREHOUSE.WAREHOUSENAME, WAREHOUSE_IS_MAIN)
                .from(WAREHOUSE)
                .fetch()
                .stream()
                .map(record -> {
                    WareHouseDTO wh = new WareHouseDTO();
                    wh.setWarehouseId(record.get(WAREHOUSE.WAREHOUSEID));
                    wh.setWarehouseName(record.get(WAREHOUSE.WAREHOUSENAME));
                    wh.setMain(Boolean.TRUE.equals(record.get(WAREHOUSE_IS_MAIN)));

                    return wh;
                }).toList();
    }

    // Получить склад по ID
    public WareHouseDTO getById(int id) {
        var record = dsl.select(WAREHOUSE.WAREHOUSEID, WAREHOUSE.WAREHOUSENAME, WAREHOUSE_IS_MAIN)
                .from(WAREHOUSE)
                .where(WAREHOUSE.WAREHOUSEID.eq(id))
                .fetchOne();

        if (record == null) return null;

        WareHouseDTO wh = new WareHouseDTO();
        wh.setWarehouseId(record.get(WAREHOUSE.WAREHOUSEID));
        wh.setWarehouseName(record.get(WAREHOUSE.WAREHOUSENAME));
        wh.setMain(Boolean.TRUE.equals(record.get(WAREHOUSE_IS_MAIN)));

        return wh;
    }

    @Transactional
    // Обновление склада
    public WareHouseDTO updateWareHouse(int id, WareHouseDTO dto) {
        WarehouseRecord record = dsl.selectFrom(WAREHOUSE)
                .where(WAREHOUSE.WAREHOUSEID.eq(id))
                .fetchOne();

        if (record == null) return null;

        record.setWarehousename(dto.getWarehouseName());


        record.store();

        dto.setWarehouseId(record.getWarehouseid());
        Boolean isMain = dsl.select(WAREHOUSE_IS_MAIN)
                .from(WAREHOUSE)
                .where(WAREHOUSE.WAREHOUSEID.eq(id))
                .fetchOne(WAREHOUSE_IS_MAIN);
        dto.setMain(Boolean.TRUE.equals(isMain));
        return dto;
    }

    // Удаление склада
    public boolean deleteWareHouse(int id) {
        int deleted = dsl.deleteFrom(WAREHOUSE)
                .where(WAREHOUSE.WAREHOUSEID.eq(id))
                .execute();
        return deleted > 0;
    }

    public Integer getMainWarehouseId() {
        Record1<Integer> main = dsl.select(WAREHOUSE.WAREHOUSEID)
                .from(WAREHOUSE)
                .where(WAREHOUSE_IS_MAIN.eq(true))
                .orderBy(WAREHOUSE.WAREHOUSEID.asc())
                .limit(1)
                .fetchOne();
        if (main != null && main.value1() != null) {
            return main.value1();
        }
        return dsl.select(WAREHOUSE.WAREHOUSEID)
                .from(WAREHOUSE)
                .orderBy(WAREHOUSE.WAREHOUSEID.asc())
                .limit(1)
                .fetchOne(WAREHOUSE.WAREHOUSEID);
    }

    @Transactional
    public boolean setMainWarehouse(int warehouseId) {
        int exists = dsl.selectCount()
                .from(WAREHOUSE)
                .where(WAREHOUSE.WAREHOUSEID.eq(warehouseId))
                .fetchOne(0, Integer.class);
        if (exists == 0) return false;

        dsl.update(WAREHOUSE)
                .set(WAREHOUSE_IS_MAIN, false)
                .execute();
        dsl.update(WAREHOUSE)
                .set(WAREHOUSE_IS_MAIN, true)
                .where(WAREHOUSE.WAREHOUSEID.eq(warehouseId))
                .execute();
        return true;
    }


    // Добавление продуктов на существующий склад
    @Transactional
    public void addProductsToWarehouse(int warehouseId, List<ProductWarehouseDTO> products) {
        if (products == null || products.isEmpty()) return;
        if (!lockWarehouses(warehouseId)) {
            throw new IllegalArgumentException("Склад не найден: " + warehouseId);
        }
        Field<BigDecimal> PRODUCT_UNIT_FACTOR = DSL.field(DSL.name("unit_factor"), BigDecimal.class);
        var PRODUCT = DSL.table(DSL.name("sales", "product"));
        var PRODUCT_ID = DSL.field(DSL.name("productid"), Integer.class);

        for (ProductWarehouseDTO pw : products) {
            if (pw == null || pw.getProductId() <= 0) {
                throw new IllegalArgumentException("Не указан продукт для склада");
            }
            Double displayQuantity = pw.getQuantity();
            if (displayQuantity == null || !Double.isFinite(displayQuantity) || displayQuantity < 0) {
                throw new IllegalArgumentException("Количество продукта должно быть конечным неотрицательным числом");
            }
            BigDecimal factor;
            try {
                factor = dsl.select(PRODUCT_UNIT_FACTOR)
                        .from(PRODUCT)
                        .where(PRODUCT_ID.eq(pw.getProductId()))
                        .fetchOne(PRODUCT_UNIT_FACTOR);
            } catch (Exception ignored) {
                factor = BigDecimal.ONE;
            }
            if (factor == null || factor.compareTo(BigDecimal.ZERO) <= 0) factor = BigDecimal.ONE;
            double qtyBase = displayQuantity * factor.doubleValue();
            if (!Double.isFinite(qtyBase) || qtyBase < 0) {
                throw new IllegalArgumentException("Количество продукта после пересчёта некорректно");
            }

            ProductwarehouseRecord record = dsl.newRecord(Productwarehouse.PRODUCTWAREHOUSE);
            record.setWarehouseid(warehouseId);
            record.setProductid(pw.getProductId());
            record.setQuantity(qtyBase);
            record.store();
        }
    }

    // Получение всех продуктов на складе
    public List<ProductWarehouseDTO> getProductsOnWarehouse(int warehouseId) {
        return dsl.select(
                        DSL.min(Productwarehouse.PRODUCTWAREHOUSE.PRODUCTWAREHOUSEID).as("productwarehouseid"),
                        Productwarehouse.PRODUCTWAREHOUSE.PRODUCTID,
                        Productwarehouse.PRODUCTWAREHOUSE.WAREHOUSEID,
                        DSL.sum(Productwarehouse.PRODUCTWAREHOUSE.QUANTITY).as("quantity")
                )
                .from(Productwarehouse.PRODUCTWAREHOUSE)
                .where(Productwarehouse.PRODUCTWAREHOUSE.WAREHOUSEID.eq(warehouseId))
                .groupBy(
                        Productwarehouse.PRODUCTWAREHOUSE.PRODUCTID,
                        Productwarehouse.PRODUCTWAREHOUSE.WAREHOUSEID
                )
                .orderBy(Productwarehouse.PRODUCTWAREHOUSE.PRODUCTID.asc())
                .fetch()
                .stream()
                .map(r -> {
                    ProductWarehouseDTO dto = new ProductWarehouseDTO();
                    dto.setProductWarehouseId(r.get("productwarehouseid", Integer.class));
                    dto.setProductId(r.get(Productwarehouse.PRODUCTWAREHOUSE.PRODUCTID));
                    dto.setWarehouseId(r.get(Productwarehouse.PRODUCTWAREHOUSE.WAREHOUSEID));
                    dto.setQuantity(r.get("quantity", Double.class) != null ? r.get("quantity", Double.class) : 0.0);
                    return dto;
                })
                .toList();
    }

    public List<PreparationWarehouseDTO> getPreparationsOnWarehouse(int warehouseId) {
        return dsl.select(PW_ID, PW_PREPARATION_ID, PW_WAREHOUSE_ID, PW_QUANTITY)
                .from(PREPARATION_WAREHOUSE)
                .where(PW_WAREHOUSE_ID.eq(warehouseId))
                .orderBy(PW_ID.asc())
                .fetch(record -> {
                    PreparationWarehouseDTO dto = new PreparationWarehouseDTO();
                    dto.setPreparationWarehouseId(record.get(PW_ID));
                    dto.setPreparationId(record.get(PW_PREPARATION_ID));
                    dto.setWarehouseId(record.get(PW_WAREHOUSE_ID));
                    dto.setQuantity(record.get(PW_QUANTITY) != null ? record.get(PW_QUANTITY) : 0.0);
                    return dto;
                });
    }

    /** Изменить количество продукта на складе (положительный delta — добавить, отрицательный — списать) */
    @Transactional
    public boolean adjustQuantity(int warehouseId, int productId, double delta) {
        if (!Double.isFinite(delta) || !lockWarehouses(warehouseId)) {
            return false;
        }
        List<ProductwarehouseRecord> records = dsl.selectFrom(Productwarehouse.PRODUCTWAREHOUSE)
                .where(Productwarehouse.PRODUCTWAREHOUSE.WAREHOUSEID.eq(warehouseId))
                .and(Productwarehouse.PRODUCTWAREHOUSE.PRODUCTID.eq(productId))
                .orderBy(Productwarehouse.PRODUCTWAREHOUSE.PRODUCTWAREHOUSEID.asc())
                .forUpdate()
                .fetch();

        if (records.isEmpty()) return false;

        if (delta >= 0) {
            ProductwarehouseRecord first = records.get(0);
            double current = first.getQuantity() != null ? first.getQuantity() : 0.0;
            first.setQuantity(current + delta);
            first.store();
            return true;
        }

        double needToSubtract = -delta;
        double available = records.stream()
                .map(r -> r.getQuantity() != null ? r.getQuantity() : 0.0)
                .reduce(0.0, Double::sum);

        if (needToSubtract > available + 1e-6) return false;

        for (ProductwarehouseRecord record : records) {
            if (needToSubtract <= 0) break;
            double current = record.getQuantity() != null ? record.getQuantity() : 0.0;
            if (current <= 0) continue;

            double take = Math.min(current, needToSubtract);
            record.setQuantity(current - take);
            record.store();
            needToSubtract -= take;
        }

        return true;
    }

    /** Доступное количество продукта на складе (в базовой единице) */
    public double getAvailableQuantity(int warehouseId, int productId) {
        Double sum = dsl.select(DSL.sum(Productwarehouse.PRODUCTWAREHOUSE.QUANTITY))
                .from(Productwarehouse.PRODUCTWAREHOUSE)
                .where(Productwarehouse.PRODUCTWAREHOUSE.WAREHOUSEID.eq(warehouseId))
                .and(Productwarehouse.PRODUCTWAREHOUSE.PRODUCTID.eq(productId))
                .fetchOne(0, Double.class);
        return sum != null ? sum : 0.0;
    }

    /**
     * Списывает доступную часть требуемого количества, не уводя остаток в минус.
     * Нехватка является бизнес-событием для инвентаризационного отчёта, а не
     * ошибкой, блокирующей продажу.
     *
     * @return фактически списанное количество
     */
    @Transactional
    public double consumeAvailableQuantity(int warehouseId, int productId, double requestedQuantity) {
        if (!Double.isFinite(requestedQuantity) || requestedQuantity <= 0) {
            return 0.0;
        }
        if (!lockWarehouses(warehouseId)) {
            return 0.0;
        }

        List<ProductwarehouseRecord> records = dsl.selectFrom(Productwarehouse.PRODUCTWAREHOUSE)
                .where(Productwarehouse.PRODUCTWAREHOUSE.WAREHOUSEID.eq(warehouseId))
                .and(Productwarehouse.PRODUCTWAREHOUSE.PRODUCTID.eq(productId))
                .orderBy(Productwarehouse.PRODUCTWAREHOUSE.PRODUCTWAREHOUSEID.asc())
                .forUpdate()
                .fetch();

        double remaining = requestedQuantity;
        double consumed = 0.0;
        for (ProductwarehouseRecord record : records) {
            if (remaining <= 0) {
                break;
            }
            double current = record.getQuantity() != null ? Math.max(0.0, record.getQuantity()) : 0.0;
            if (current <= 0) {
                continue;
            }

            double take = Math.min(current, remaining);
            record.setQuantity(current - take);
            record.store();
            remaining -= take;
            consumed += take;
        }
        return consumed;
    }

    @Transactional
    public void setProductQuantity(int warehouseId, int productId, double quantity) {
        if (!Double.isFinite(quantity)) {
            throw new IllegalArgumentException("Количество должно быть конечным числом");
        }
        if (!lockWarehouses(warehouseId)) {
            throw new IllegalArgumentException("Склад не найден: " + warehouseId);
        }
        double normalizedQuantity = Math.max(0.0, quantity);
        List<ProductwarehouseRecord> records = dsl.selectFrom(Productwarehouse.PRODUCTWAREHOUSE)
                .where(Productwarehouse.PRODUCTWAREHOUSE.WAREHOUSEID.eq(warehouseId))
                .and(Productwarehouse.PRODUCTWAREHOUSE.PRODUCTID.eq(productId))
                .orderBy(Productwarehouse.PRODUCTWAREHOUSE.PRODUCTWAREHOUSEID.asc())
                .forUpdate()
                .fetch();

        if (records.isEmpty()) {
            if (normalizedQuantity <= 0) {
                return;
            }
            ProductwarehouseRecord record = dsl.newRecord(Productwarehouse.PRODUCTWAREHOUSE);
            record.setWarehouseid(warehouseId);
            record.setProductid(productId);
            record.setQuantity(normalizedQuantity);
            record.store();
            return;
        }

        ProductwarehouseRecord first = records.get(0);
        first.setQuantity(normalizedQuantity);
        first.store();

        for (int i = 1; i < records.size(); i++) {
            ProductwarehouseRecord record = records.get(i);
            if ((record.getQuantity() != null ? record.getQuantity() : 0.0) == 0.0) {
                continue;
            }
            record.setQuantity(0.0);
            record.store();
        }
    }

    @Transactional
    public boolean adjustPreparationQuantity(int warehouseId, int preparationId, double delta) {
        if (!Double.isFinite(delta) || !lockWarehouses(warehouseId)) {
            return false;
        }
        var records = dsl.select(PW_ID, PW_WAREHOUSE_ID, PW_PREPARATION_ID, PW_QUANTITY)
                .from(PREPARATION_WAREHOUSE)
                .where(PW_WAREHOUSE_ID.eq(warehouseId))
                .and(PW_PREPARATION_ID.eq(preparationId))
                .orderBy(PW_ID.asc())
                .forUpdate()
                .fetch();

        if (records.isEmpty()) {
            if (delta < 0) return false;
            dsl.insertInto(PREPARATION_WAREHOUSE)
                    .set(PW_WAREHOUSE_ID, warehouseId)
                    .set(PW_PREPARATION_ID, preparationId)
                    .set(PW_QUANTITY, delta)
                    .execute();
            return true;
        }

        if (delta >= 0) {
            var first = records.get(0);
            double current = first.get(PW_QUANTITY) != null ? first.get(PW_QUANTITY) : 0.0;
            dsl.update(PREPARATION_WAREHOUSE)
                    .set(PW_QUANTITY, current + delta)
                    .where(PW_ID.eq(first.get(PW_ID)))
                    .execute();
            return true;
        }

        double available = records.stream()
                .map(r -> r.get(PW_QUANTITY) != null ? r.get(PW_QUANTITY) : 0.0)
                .reduce(0.0, Double::sum);
        if (available + 1e-6 < -delta) return false;

        double remaining = -delta;
        for (var record : records) {
            if (remaining <= 0) break;
            double current = record.get(PW_QUANTITY) != null ? record.get(PW_QUANTITY) : 0.0;
            if (current <= 0) continue;
            double take = Math.min(current, remaining);
            dsl.update(PREPARATION_WAREHOUSE)
                    .set(PW_QUANTITY, current - take)
                    .where(PW_ID.eq(record.get(PW_ID)))
                    .execute();
            remaining -= take;
        }
        return true;
    }

    public double getAvailablePreparationQuantity(int warehouseId, int preparationId) {
        Double sum = dsl.select(DSL.sum(PW_QUANTITY))
                .from(PREPARATION_WAREHOUSE)
                .where(PW_WAREHOUSE_ID.eq(warehouseId))
                .and(PW_PREPARATION_ID.eq(preparationId))
                .fetchOne(0, Double.class);
        return sum != null ? sum : 0.0;
    }

    /**
     * Списывает доступную часть заготовки. Если заготовки меньше требуемого,
     * остаток становится нулевым, а продажа продолжается.
     *
     * @return фактически списанное количество
     */
    @Transactional
    public double consumeAvailablePreparationQuantity(int warehouseId, int preparationId, double requestedQuantity) {
        if (!Double.isFinite(requestedQuantity) || requestedQuantity <= 0) {
            return 0.0;
        }
        if (!lockWarehouses(warehouseId)) {
            return 0.0;
        }

        var records = dsl.select(PW_ID, PW_QUANTITY)
                .from(PREPARATION_WAREHOUSE)
                .where(PW_WAREHOUSE_ID.eq(warehouseId))
                .and(PW_PREPARATION_ID.eq(preparationId))
                .orderBy(PW_ID.asc())
                .forUpdate()
                .fetch();

        double remaining = requestedQuantity;
        double consumed = 0.0;
        for (var record : records) {
            if (remaining <= 0) {
                break;
            }
            double current = record.get(PW_QUANTITY) != null ? Math.max(0.0, record.get(PW_QUANTITY)) : 0.0;
            if (current <= 0) {
                continue;
            }

            double take = Math.min(current, remaining);
            dsl.update(PREPARATION_WAREHOUSE)
                    .set(PW_QUANTITY, current - take)
                    .where(PW_ID.eq(record.get(PW_ID)))
                    .execute();
            remaining -= take;
            consumed += take;
        }
        return consumed;
    }

    /** Перемещение количества товара между складами */
    @Transactional
    public boolean moveProduct(int fromWarehouseId, int toWarehouseId, int productId, double quantity) {
        if (!Double.isFinite(quantity) || quantity <= 0 || fromWarehouseId == toWarehouseId) return false;
        if (!lockWarehouses(fromWarehouseId, toWarehouseId)) return false;

        List<ProductwarehouseRecord> fromRecords = dsl.selectFrom(Productwarehouse.PRODUCTWAREHOUSE)
                .where(Productwarehouse.PRODUCTWAREHOUSE.WAREHOUSEID.eq(fromWarehouseId))
                .and(Productwarehouse.PRODUCTWAREHOUSE.PRODUCTID.eq(productId))
                .orderBy(Productwarehouse.PRODUCTWAREHOUSE.PRODUCTWAREHOUSEID.asc())
                .forUpdate()
                .fetch();

        if (fromRecords.isEmpty()) return false;

        double available = fromRecords.stream()
                .map(r -> r.getQuantity() != null ? r.getQuantity() : 0.0)
                .reduce(0.0, Double::sum);

        if (available < quantity) return false;

        double remaining = quantity;
        List<ProductwarehouseRecord> updatedFrom = new ArrayList<>();
        for (ProductwarehouseRecord record : fromRecords) {
            if (remaining <= 0) break;
            double current = record.getQuantity() != null ? record.getQuantity() : 0.0;
            if (current <= 0) continue;

            double take = Math.min(current, remaining);
            record.setQuantity(current - take);
            updatedFrom.add(record);
            remaining -= take;
        }

        for (ProductwarehouseRecord record : updatedFrom) {
            record.store();
        }

        ProductwarehouseRecord toRecord = dsl.selectFrom(Productwarehouse.PRODUCTWAREHOUSE)
                .where(Productwarehouse.PRODUCTWAREHOUSE.WAREHOUSEID.eq(toWarehouseId))
                .and(Productwarehouse.PRODUCTWAREHOUSE.PRODUCTID.eq(productId))
                .orderBy(Productwarehouse.PRODUCTWAREHOUSE.PRODUCTWAREHOUSEID.asc())
                .limit(1)
                .forUpdate()
                .fetchOne();

        if (toRecord == null) {
            toRecord = dsl.newRecord(Productwarehouse.PRODUCTWAREHOUSE);
            toRecord.setWarehouseid(toWarehouseId);
            toRecord.setProductid(productId);
            toRecord.setQuantity(quantity);
        } else {
            double toCurrent = toRecord.getQuantity() != null ? toRecord.getQuantity() : 0.0;
            toRecord.setQuantity(toCurrent + quantity);
        }
        toRecord.store();

        return true;
    }

    private boolean lockWarehouses(int... warehouseIds) {
        List<Integer> ids = java.util.Arrays.stream(warehouseIds)
                .boxed()
                .distinct()
                .sorted()
                .toList();
        if (ids.isEmpty()) {
            return false;
        }

        List<Integer> lockedIds = dsl.select(WAREHOUSE.WAREHOUSEID)
                .from(WAREHOUSE)
                .where(WAREHOUSE.WAREHOUSEID.in(ids))
                .orderBy(WAREHOUSE.WAREHOUSEID.asc())
                .forUpdate()
                .fetch(WAREHOUSE.WAREHOUSEID);
        return lockedIds.size() == ids.size();
    }

}
