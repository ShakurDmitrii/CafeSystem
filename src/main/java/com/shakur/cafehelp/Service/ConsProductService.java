package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.ConsProductDTO;
import jooqdata.tables.Consproduct;
import jooqdata.tables.records.ConsproductRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ConsProductService {

    private DSLContext dsl;

    public ConsProductService(DSLContext dsl) {
        this.dsl = dsl;
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

    public ConsProductDTO createConsProduct(ConsProductDTO consProductDTO) {
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

        // После store() автоинкрементное поле должно быть заполнено
        System.out.println("New consProductId = " + record.getConsproductid());

        ConsProductDTO responseDTO = new ConsProductDTO();
        responseDTO.consProductId = record.getConsproductid();
        responseDTO.consignmentId = record.getConsignmentid();
        responseDTO.productId = record.getProductid();
        responseDTO.GROSS = record.getGross();
        responseDTO.quantity = record.getQuantity();

        return responseDTO;
    }



    public ConsProductDTO deleteConsProduct(int consProductId) {
        boolean exist = dsl.fetchExists(
                dsl.selectOne()
                        .from(Consproduct.CONSPRODUCT)
                        .where(Consproduct.CONSPRODUCT.CONSPRODUCTID.eq(consProductId))
        );
        if (!exist) {
            throw new RuntimeException("No such Product with id " + consProductId);
        }

        ConsProductDTO deleteConsProduct = dsl.selectFrom(Consproduct.CONSPRODUCT)
                .where(Consproduct.CONSPRODUCT.CONSPRODUCTID.eq(consProductId))
                .fetchOne(record -> {
                    ConsProductDTO dto = new ConsProductDTO();
                    dto.consProductId = record.getConsproductid();
                    dto.consignmentId = record.getConsignmentid();
                    dto.productId = record.getProductid();
                    dto.GROSS = record.getGross();
                    dto.quantity = record.getQuantity();
                    return dto;
                });

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
