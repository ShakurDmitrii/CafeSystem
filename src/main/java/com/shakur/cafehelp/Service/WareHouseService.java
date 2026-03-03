package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.ProductWarehouseDTO;
import com.shakur.cafehelp.DTO.WareHouseDTO;
import jooqdata.tables.records.ProductwarehouseRecord;
import jooqdata.tables.records.WarehouseRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jooqdata.tables.Productwarehouse;
import java.util.List;
import java.util.ArrayList;
import java.math.BigDecimal;
import org.jooq.Field;
import org.jooq.impl.DSL;

import static jooqdata.tables.Warehouse.WAREHOUSE;

@Service
public class WareHouseService {

    private final DSLContext dsl;

    public WareHouseService(DSLContext dsl) {
        this.dsl = dsl;
    }

    // Создание склада
    public WareHouseDTO createWareHouse(WareHouseDTO dto) {
        WarehouseRecord record = dsl.newRecord(WAREHOUSE);
        record.setWarehousename(dto.getWarehouseName());


        record.store();

        dto.setWarehouseId(record.getWarehouseid());
        return dto;
    }

    // Получить все склады
    public List<WareHouseDTO> getAll() {
        return dsl.selectFrom(WAREHOUSE)
                .fetch()
                .stream()
                .map(record -> {
                    WareHouseDTO wh = new WareHouseDTO();
                    wh.setWarehouseId(record.getWarehouseid());
                    wh.setWarehouseName(record.getWarehousename());

                    return wh;
                }).toList();
    }

    // Получить склад по ID
    public WareHouseDTO getById(int id) {
        WarehouseRecord record = dsl.selectFrom(WAREHOUSE)
                .where(WAREHOUSE.WAREHOUSEID.eq(id))
                .fetchOne();

        if (record == null) return null;

        WareHouseDTO wh = new WareHouseDTO();
        wh.setWarehouseId(record.getWarehouseid());
        wh.setWarehouseName(record.getWarehousename());

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
        return dto;
    }

    // Удаление склада
    public boolean deleteWareHouse(int id) {
        int deleted = dsl.deleteFrom(WAREHOUSE)
                .where(WAREHOUSE.WAREHOUSEID.eq(id))
                .execute();
        return deleted > 0;
    }


    // Добавление продуктов на существующий склад
    @Transactional
    public void addProductsToWarehouse(int warehouseId, List<ProductWarehouseDTO> products) {
        if (products == null || products.isEmpty()) return;
        Field<BigDecimal> PRODUCT_UNIT_FACTOR = DSL.field(DSL.name("unit_factor"), BigDecimal.class);
        var PRODUCT = DSL.table(DSL.name("sales", "product"));
        var PRODUCT_ID = DSL.field(DSL.name("productid"), Integer.class);

        for (ProductWarehouseDTO pw : products) {
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
            double qtyBase = (pw.getQuantity() != null ? pw.getQuantity() : 0.0) * factor.doubleValue();

            ProductwarehouseRecord record = dsl.newRecord(Productwarehouse.PRODUCTWAREHOUSE);
            record.setWarehouseid(warehouseId);
            record.setProductid(pw.getProductId());
            record.setQuantity(qtyBase);
            record.store();
        }
    }

    // Получение всех продуктов на складе
    public List<ProductWarehouseDTO> getProductsOnWarehouse(int warehouseId) {
        return dsl.selectFrom(Productwarehouse.PRODUCTWAREHOUSE)
                .where(Productwarehouse.PRODUCTWAREHOUSE.WAREHOUSEID.eq(warehouseId))
                .fetch()
                .stream()
                .map(r -> {
                    ProductWarehouseDTO dto = new ProductWarehouseDTO();
                    dto.setProductWarehouseId(r.getProductwarehouseid());
                    dto.setProductId(r.getProductid());
                    dto.setWarehouseId(r.getWarehouseid());
                    dto.setQuantity(r.getQuantity() != null ? r.getQuantity() : 0.0);
                    return dto;
                })
                .toList();
    }

    /** Изменить количество продукта на складе (положительный delta — добавить, отрицательный — списать) */
    @Transactional
    public boolean adjustQuantity(int warehouseId, int productId, double delta) {
        List<ProductwarehouseRecord> records = dsl.selectFrom(Productwarehouse.PRODUCTWAREHOUSE)
                .where(Productwarehouse.PRODUCTWAREHOUSE.WAREHOUSEID.eq(warehouseId))
                .and(Productwarehouse.PRODUCTWAREHOUSE.PRODUCTID.eq(productId))
                .orderBy(Productwarehouse.PRODUCTWAREHOUSE.PRODUCTWAREHOUSEID.asc())
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

        if (available <= 0) return true;

        if (needToSubtract > available) needToSubtract = available;

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

    /** Перемещение количества товара между складами */
    @Transactional
    public boolean moveProduct(int fromWarehouseId, int toWarehouseId, int productId, double quantity) {
        if (quantity <= 0 || fromWarehouseId == toWarehouseId) return false;

        List<ProductwarehouseRecord> fromRecords = dsl.selectFrom(Productwarehouse.PRODUCTWAREHOUSE)
                .where(Productwarehouse.PRODUCTWAREHOUSE.WAREHOUSEID.eq(fromWarehouseId))
                .and(Productwarehouse.PRODUCTWAREHOUSE.PRODUCTID.eq(productId))
                .orderBy(Productwarehouse.PRODUCTWAREHOUSE.PRODUCTWAREHOUSEID.asc())
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

}
