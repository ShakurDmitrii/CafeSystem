package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.SupplierDTO;
import jooqdata.tables.Product;
import jooqdata.tables.Supplier;
import jooqdata.tables.records.ProductRecord;
import jooqdata.tables.records.SupplierRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SupplierService {
    private DSLContext dsl;
    public SupplierService(DSLContext dsl) {this.dsl = dsl;}

    public SupplierDTO getSupplierById(int id) {
        return dsl.selectFrom(Supplier.SUPPLIER)
                .where(Supplier.SUPPLIER.SUPPLIERID.eq(id))
                .fetchOptional()
                .map(supplierRecord -> {
                    SupplierDTO supplierDTO = new SupplierDTO();
                    supplierDTO.supplierID = supplierRecord.getSupplierid();
                    supplierDTO.supplierName = supplierRecord.getSuppliername();
                    supplierDTO.communication = supplierRecord.getCommunication();
                return supplierDTO;
                }).orElseThrow(() -> new RuntimeException("Supplier Not Found"));
    }
    public List<SupplierDTO> getAllSuppliers() {
        return dsl.selectFrom(Supplier.SUPPLIER)
                .fetch()
                .stream()
                .map(record -> {
                    SupplierDTO dto = new SupplierDTO();
                    dto.supplierID = record.getSupplierid();
                    dto.supplierName = record.getSuppliername();
                    dto.communication = record.getCommunication();
                    return dto;
                }).toList();
    }

    public SupplierDTO create(SupplierDTO supplierDTO) {
        SupplierRecord record = dsl.newRecord(Supplier.SUPPLIER);

        // Устанавливаем ТОЛЬКО поля, которые вводит пользователь
        record.setSuppliername(supplierDTO.getSupplierName());
        record.setCommunication(supplierDTO.getCommunication());


        // Сохраняем запись
        record.store();

        // Возвращаем DTO с сгенерированным ID
        SupplierDTO responseDTO = new SupplierDTO();
        responseDTO.setSupplierID(record.getSupplierid()); // Получаем сгенерированный ID
        responseDTO.setSupplierName(record.getSuppliername());
        responseDTO.setCommunication(record.getCommunication());

        return responseDTO;
    }

    public SupplierDTO delete(int id) {
        // Проверяем существование поставщика
        boolean exists = dsl.fetchExists(
                dsl.selectOne()
                        .from(Supplier.SUPPLIER)
                        .where(Supplier.SUPPLIER.SUPPLIERID.eq(id))
        );

        if (!exists) {
            throw new RuntimeException("Supplier not Found");
        }

        // Получаем данные перед удалением
        SupplierDTO deletedSupplier = dsl.selectFrom(Supplier.SUPPLIER)
                .where(Supplier.SUPPLIER.SUPPLIERID.eq(id))
                .fetchOne(record -> {
                    SupplierDTO dto = new SupplierDTO();
                    dto.supplierID = record.getSupplierid();
                    dto.supplierName = record.getSuppliername();
                    dto.communication = record.getCommunication();
                    return dto;
                });

        // Выполняем удаление
        dsl.deleteFrom(Supplier.SUPPLIER)
                .where(Supplier.SUPPLIER.SUPPLIERID.eq(id))
                .execute();

        return deletedSupplier;
    }
}
