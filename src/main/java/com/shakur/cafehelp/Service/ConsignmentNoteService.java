package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.ConsProductDTO;
import com.shakur.cafehelp.DTO.ConsignmentNoteDTO;
import jooqdata.tables.Consignmentnote;
import jooqdata.tables.Consproduct;
import jooqdata.tables.records.ConsignmentnoteRecord;
import org.jooq.DSLContext;
import org.jooq.Result;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static jooqdata.Tables.PRODUCT;
import static jooqdata.tables.Consignmentnote.CONSIGNMENTNOTE;
import static jooqdata.tables.Consproduct.CONSPRODUCT;

@Service
public class ConsignmentNoteService {
    private DSLContext  dsl;
    public ConsignmentNoteService(DSLContext dsl) {this.dsl = dsl;}

    public ConsignmentNoteDTO createConsignmentNote(ConsignmentNoteDTO dto) {
        ConsignmentnoteRecord record = dsl.newRecord(CONSIGNMENTNOTE);

        record.setSupplierid(dto.supplierId);
        record.setDate(dto.date);
        record.setAmount(dto.amount);
        record.store();
        dto.consignmentId = record.getConsignmentid();

        return dto;
    }

    @Transactional
    public void updateAmount(Integer consignmentId, double amount) {
        dsl.update(CONSIGNMENTNOTE)
                .set(CONSIGNMENTNOTE.AMOUNT, amount)
                .where(CONSIGNMENTNOTE.CONSIGNMENTID.eq(consignmentId))
                .execute();
    }


    public ConsignmentNoteDTO getConsignmentNoteById(int id) {
        return dsl.selectFrom(CONSIGNMENTNOTE)
                .where(CONSIGNMENTNOTE.CONSIGNMENTID.eq(id))
                .fetchOptional()
                .map(record ->{
                    ConsignmentNoteDTO dto = new ConsignmentNoteDTO();
                    dto.consignmentId = record.getConsignmentid();
                    dto.supplierId = record.getSupplierid();
                    dto.date = record.getDate();
                    dto.amount = record.getAmount();
                    return dto;
                }).orElseThrow(() -> new RuntimeException("ConsignmentNote not found " + id));
    }
    public ConsignmentNoteDTO getConsignmentNoteBySupplierId(int id) {
        return dsl.selectFrom(CONSIGNMENTNOTE)
                .where(CONSIGNMENTNOTE.SUPPLIERID.eq(id))
                .fetchOptional()
                .map(record ->{
                    ConsignmentNoteDTO dto = new ConsignmentNoteDTO();
                    dto.consignmentId = record.getConsignmentid();
                    dto.supplierId = record.getSupplierid();
                    dto.date = record.getDate();
                    dto.amount = record.getAmount();
                    return dto;
                }).orElseThrow(() -> new RuntimeException("ConsignmentNote not found " + id));
    }
    public List<ConsignmentNoteDTO> getConsignmentNotes() {
        return dsl.selectFrom(CONSIGNMENTNOTE)
                .fetch()
                .stream()
                .map(record->
                {
                    ConsignmentNoteDTO dto = new ConsignmentNoteDTO();
                    dto.consignmentId = record.getConsignmentid();
                    dto.supplierId = record.getSupplierid();
                    dto.amount = record.getAmount();
                    dto.date = record.getDate();
                    return dto;
                }).toList();
    }

    public List<ConsignmentNoteDTO> getAllConsignmentNotes() {
        return dsl.selectFrom(CONSIGNMENTNOTE)
                .fetch()
                .stream()
                .map(record ->{
                    ConsignmentNoteDTO dto = new ConsignmentNoteDTO();
                    dto.consignmentId = record.getConsignmentid();
                    dto.supplierId = record.getSupplierid();
                    dto.date = record.getDate();
                    dto.amount = record.getAmount();
                    return dto;
                }).toList();
    }

    public ConsignmentNoteDTO getConsignmentWithProducts(int consignmentId) {
        // Получаем накладную
        ConsignmentNoteDTO noteDto = dsl.selectFrom(CONSIGNMENTNOTE)
                .where(CONSIGNMENTNOTE.CONSIGNMENTID.eq(consignmentId))
                .fetchOptional()
                .map(record -> {
                    ConsignmentNoteDTO dto = new ConsignmentNoteDTO();
                    dto.consignmentId = record.getConsignmentid();
                    dto.supplierId = record.getSupplierid();
                    dto.date = record.getDate();
                    dto.amount = record.getAmount(); // берём уже рассчитанную сумму
                    return dto;
                }).orElseThrow(() -> new RuntimeException("ConsignmentNote not found " + consignmentId));

        // Получаем товары накладной с названием из таблицы Product
        List<ConsProductDTO> products = dsl.select(
                        CONSPRODUCT.CONSPRODUCTID,
                        CONSPRODUCT.CONSIGNMENTID,
                        CONSPRODUCT.PRODUCTID,
                        CONSPRODUCT.GROSS,
                        CONSPRODUCT.QUANTITY,
                        PRODUCT.PRODUCTNAME
                )
                .from(CONSPRODUCT)
                .join(PRODUCT).on(CONSPRODUCT.PRODUCTID.eq(PRODUCT.PRODUCTID))
                .where(CONSPRODUCT.CONSIGNMENTID.eq(consignmentId))
                .fetch()
                .map(record -> {
                    ConsProductDTO dto = new ConsProductDTO();
                    dto.consProductId = record.get(CONSPRODUCT.CONSPRODUCTID);
                    dto.productId = record.get(CONSPRODUCT.PRODUCTID);
                    dto.quantity = record.get(CONSPRODUCT.QUANTITY);
                    dto.GROSS = record.get(CONSPRODUCT.GROSS);
                    dto.productName = record.get(PRODUCT.PRODUCTNAME);
                    return dto;
                });

        noteDto.items = products;

        return noteDto;
    }


}
