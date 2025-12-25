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

        for (ProductWarehouseDTO pw : products) {
            ProductwarehouseRecord record = dsl.newRecord(Productwarehouse.PRODUCTWAREHOUSE);
            record.setWarehouseid(warehouseId);
            record.setProductid(pw.getProductId());
            record.setQuantity(pw.getQuantity());
            record.store();
        }
    }

    // Можно добавить метод для получения всех продуктов на складе
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
                    return dto;
                })
                .toList();
    }

}
