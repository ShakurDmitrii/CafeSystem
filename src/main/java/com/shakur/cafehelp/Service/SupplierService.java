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
    public SupplierDTO create(SupplierDTO dto){
        SupplierRecord record = dsl.fetchOne(Supplier.SUPPLIER);
        assert record != null;
        record.setSupplierid(dto.supplierID);
        record.setSuppliername(dto.supplierName);
        record.setCommunication(dto.communication);
        record.store();
        return dto;
    }
}
