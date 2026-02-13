package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.TechProductDTO;

import jooqdata.Tables;
import jooqdata.tables.Techproduct;
import jooqdata.tables.records.TechproductRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TechProductService {
    private final DSLContext dsl;

    public TechProductService(DSLContext dsl) {
        this.dsl = dsl;
    }

    // ===============================
    // CREATE
    // ===============================
    public TechProductDTO create(TechProductDTO techProduct) {
        TechproductRecord record = dsl.newRecord(Techproduct.TECHPRODUCT);
        record.setDishid(techProduct.getDishId());
        record.setProductid(techProduct.getProductId());
        record.setWeight(techProduct.getWeight());
        record.setWaste(techProduct.getWaste());
        record.store();

        techProduct.setTechProductId(record.getTechproductid());
        return techProduct;
    }

    // ===============================
    // READ ONE
    // ===============================
    public TechProductDTO getById(int id) {
        TechproductRecord record = dsl.fetchOne(Tables.TECHPRODUCT, Tables.TECHPRODUCT.TECHPRODUCTID.eq(id));
        if (record == null) return null;
        return recordToDTO(record);
    }

    // ===============================
    // READ ALL BY DISH
    // ===============================
    public List<TechProductDTO> getByDishId(int dishId) {
        List<TechproductRecord> records = dsl.fetch(Tables.TECHPRODUCT, Tables.TECHPRODUCT.DISHID.eq(dishId));
        return records.stream()
                .map(this::recordToDTO)
                .collect(Collectors.toList());
    }

    // ===============================
    // UPDATE
    // ===============================
    public TechProductDTO update(int id, TechProductDTO techProduct) {
        TechproductRecord record = dsl.fetchOne(Tables.TECHPRODUCT, Tables.TECHPRODUCT.TECHPRODUCTID.eq(id));
        if (record == null) return null;

        record.setProductid(techProduct.getProductId());
        record.setWeight(techProduct.getWeight());
        record.setWaste(techProduct.getWaste());
        record.store();

        return recordToDTO(record);
    }

    // ===============================
    // DELETE
    // ===============================
        public boolean delete(int id) {
            int deleted = dsl.deleteFrom(Tables.TECHPRODUCT)
                    .where(Tables.TECHPRODUCT.TECHPRODUCTID.eq(id))
                    .execute();
            return deleted > 0;
        }

    // ===============================
    // HELPER: конвертация в DTO
    // ===============================
    private TechProductDTO recordToDTO(TechproductRecord record) {
        TechProductDTO dto = new TechProductDTO();
        dto.setTechProductId(record.getTechproductid());
        dto.setDishId(record.getDishid());
        dto.setProductId(record.getProductid());
        dto.setWeight(record.getWeight());
        dto.setWaste(record.getWaste());
        return dto;
    }

}
