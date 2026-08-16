package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.SupplierDTO;
import jooqdata.tables.Product;
import jooqdata.tables.Supplier;
import jooqdata.tables.records.SupplierRecord;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

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
                }).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Поставщик не найден"));
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

    @Transactional
    public SupplierDTO create(SupplierDTO supplierDTO) {
        String name = requireName(supplierDTO != null ? supplierDTO.getSupplierName() : null);
        ensureNameIsUnique(name, null);
        SupplierRecord record = dsl.newRecord(Supplier.SUPPLIER);
        record.setSuppliername(name);
        record.setCommunication(normalizeCommunication(supplierDTO.getCommunication()));
        record.store();
        SupplierDTO responseDTO = new SupplierDTO();
        responseDTO.setSupplierID(record.getSupplierid());
        responseDTO.setSupplierName(record.getSuppliername());
        responseDTO.setCommunication(record.getCommunication());

        return responseDTO;
    }

    @Transactional
    public SupplierDTO update(int id, SupplierDTO supplierDTO) {
        SupplierRecord record = dsl.selectFrom(Supplier.SUPPLIER)
                .where(Supplier.SUPPLIER.SUPPLIERID.eq(id))
                .fetchOptional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Поставщик не найден"));

        String name = requireName(supplierDTO != null ? supplierDTO.getSupplierName() : null);
        ensureNameIsUnique(name, id);
        record.setSuppliername(name);
        record.setCommunication(normalizeCommunication(supplierDTO.getCommunication()));
        record.store();

        SupplierDTO responseDTO = new SupplierDTO();
        responseDTO.setSupplierID(record.getSupplierid());
        responseDTO.setSupplierName(record.getSuppliername());
        responseDTO.setCommunication(record.getCommunication());
        return responseDTO;
    }

    @Transactional
    public SupplierDTO delete(int id) {
        SupplierDTO deletedSupplier = dsl.selectFrom(Supplier.SUPPLIER)
                .where(Supplier.SUPPLIER.SUPPLIERID.eq(id))
                .fetchOne(record -> {
                    SupplierDTO dto = new SupplierDTO();
                    dto.supplierID = record.getSupplierid();
                    dto.supplierName = record.getSuppliername();
                    dto.communication = record.getCommunication();
                    return dto;
                });
        if (deletedSupplier == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Поставщик не найден");
        }

        boolean usedByProduct = dsl.fetchExists(
                dsl.selectOne().from(Product.PRODUCT)
                        .where(Product.PRODUCT.SUPPLIERID.eq(id))
        );
        boolean usedByProductLink = dsl.fetchExists(
                dsl.selectOne().from(DSL.table(DSL.name("sales", "product_supplier")))
                        .where(DSL.field(DSL.name("supplier_id"), Integer.class).eq(id))
        );
        boolean usedByDocument = dsl.fetchExists(
                dsl.selectOne().from(DSL.table(DSL.name("sales", "consignmentnote")))
                        .where(DSL.field(DSL.name("supplierid"), Integer.class).eq(id))
        );
        if (usedByProduct || usedByProductLink || usedByDocument) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Поставщик используется в товарах или приходных документах"
            );
        }

        dsl.deleteFrom(Supplier.SUPPLIER)
                .where(Supplier.SUPPLIER.SUPPLIERID.eq(id))
                .execute();

        return deletedSupplier;
    }

    private String requireName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Название поставщика обязательно");
        }
        return name;
    }

    private String normalizeCommunication(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        return value.isEmpty() ? null : value;
    }

    private void ensureNameIsUnique(String name, Integer currentId) {
        var condition = DSL.lower(Supplier.SUPPLIER.SUPPLIERNAME)
                .eq(name.toLowerCase(Locale.ROOT));
        if (currentId != null) {
            condition = condition.and(Supplier.SUPPLIER.SUPPLIERID.ne(currentId));
        }
        if (dsl.fetchExists(dsl.selectOne().from(Supplier.SUPPLIER).where(condition))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Поставщик с таким названием уже существует");
        }
    }
}
