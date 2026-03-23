package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.ConsProductDTO;
import jooqdata.tables.Consproduct;
import jooqdata.tables.records.ConsproductRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class ConsProductService {

    private final DSLContext dsl;
    private final ConsignmentNoteService consignmentNoteService;

    public ConsProductService(DSLContext dsl, ConsignmentNoteService consignmentNoteService) {
        this.dsl = dsl;
        this.consignmentNoteService = consignmentNoteService;
    }

    public List<ConsProductDTO> getConsProduct() {
        return dsl.selectFrom(Consproduct.CONSPRODUCT)
                .fetch()
                .stream()
                .map(record ->{
                    ConsProductDTO consProductDTO = new ConsProductDTO();
                    consProductDTO.consignmentId = record.getConsignmentid();
                    consProductDTO.productId = record.getProductid();
                    consProductDTO.GROSS = record.getGross();
                    consProductDTO.quantity = record.getQuantity();
                    return consProductDTO;
                }).toList();
    }
    public ConsProductDTO getConsProductById(int id) {
        // Получаем все записи с данным productId
        List<ConsproductRecord> records = dsl.selectFrom(Consproduct.CONSPRODUCT)
                .where(Consproduct.CONSPRODUCT.PRODUCTID.eq(id))
                .fetch();

        if (records.isEmpty()) {
            return null;
        }

        // Суммируем quantity из всех записей
        int totalQuantity = records.stream()
                .map(ConsproductRecord::getQuantity)
                .filter(Objects::nonNull)
                .mapToInt(q -> q.intValue())  // Исправлено здесь
                .sum();

        // Берем первую запись для остальных полей
        ConsproductRecord firstRecord = records.get(0);

        ConsProductDTO consProductDTO = new ConsProductDTO();
        consProductDTO.consignmentId = firstRecord.getConsignmentid();
        consProductDTO.productId = firstRecord.getProductid();
        consProductDTO.GROSS = firstRecord.getGross();
        consProductDTO.quantity = (double) totalQuantity;

        return consProductDTO;
    }

    public ConsProductDTO createConsProduct(ConsProductDTO consProductDTO) {
        if (consProductDTO.consignmentId <= 0) {
            throw new IllegalArgumentException("consignmentId is required");
        }
        if (consignmentNoteService.isPosted(consProductDTO.consignmentId)) {
            throw new IllegalStateException("Проведенную накладную нельзя редактировать");
        }

        ConsproductRecord record = dsl.newRecord(Consproduct.CONSPRODUCT);

        // Обязательно устанавливаем только нужные поля
        record.setConsignmentid(consProductDTO.consignmentId);
        record.setProductid(consProductDTO.productId);
        record.setGross(consProductDTO.GROSS);
        record.setQuantity(consProductDTO.quantity);

        // Сохраняем запись
        int result = record.store(); // вернёт количество вставленных строк
        if (result != 1) {
            throw new RuntimeException("Не удалось вставить ConsProduct");
        }

        ConsProductDTO responseDTO = new ConsProductDTO();
        responseDTO.consProductId = record.getConsproductid();
        responseDTO.consignmentId = record.getConsignmentid();
        responseDTO.productId = record.getProductid();
        responseDTO.GROSS = record.getGross();
        responseDTO.quantity = record.getQuantity();

        return responseDTO;
    }



    public ConsProductDTO deleteConsProduct(int consProductId) {
        ConsproductRecord existingRecord = dsl.selectFrom(Consproduct.CONSPRODUCT)
                .where(Consproduct.CONSPRODUCT.CONSPRODUCTID.eq(consProductId))
                .fetchOne();

        if (existingRecord == null) {
            throw new RuntimeException("No such Product with id " + consProductId);
        }
        if (consignmentNoteService.isPosted(existingRecord.getConsignmentid())) {
            throw new IllegalStateException("Проведенную накладную нельзя редактировать");
        }

        ConsProductDTO deleteConsProduct = new ConsProductDTO();
        deleteConsProduct.consProductId = existingRecord.getConsproductid();
        deleteConsProduct.consignmentId = existingRecord.getConsignmentid();
        deleteConsProduct.productId = existingRecord.getProductid();
        deleteConsProduct.GROSS = existingRecord.getGross();
        deleteConsProduct.quantity = existingRecord.getQuantity();

        dsl.deleteFrom(Consproduct.CONSPRODUCT)
                .where(Consproduct.CONSPRODUCT.CONSPRODUCTID.eq(consProductId))
                .execute();

        return deleteConsProduct;
    }


    public List<ConsProductDTO> getConsProductByConsId(int consId) {
        return dsl.selectFrom(Consproduct.CONSPRODUCT)
                .where(Consproduct.CONSPRODUCT.CONSIGNMENTID.eq(consId))
                .fetch()
                .stream()
                .map(record ->{
                    ConsProductDTO dto = new ConsProductDTO();
                    dto.consignmentId = record.getConsignmentid();
                    dto.productId = record.getProductid();
                    dto.GROSS = record.getGross();
                    dto.quantity = record.getQuantity();
                    return dto;
                }).toList();
    }

}
