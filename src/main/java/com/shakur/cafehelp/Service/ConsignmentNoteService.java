package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.ConsProductDTO;
import com.shakur.cafehelp.DTO.ConsignmentNoteDTO;
import com.shakur.cafehelp.DTO.MovementRequestDTO;
import jooqdata.tables.Consignmentnote;
import jooqdata.tables.Consproduct;
import jooqdata.tables.records.ConsignmentnoteRecord;
import jooqdata.tables.records.ConsproductRecord;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static jooqdata.Tables.PRODUCT;
import static jooqdata.tables.Consignmentnote.CONSIGNMENTNOTE;
import static jooqdata.tables.Consproduct.CONSPRODUCT;

@Service
public class ConsignmentNoteService {
    private static final Table<?> INVENTORY_DOCUMENTS = DSL.table(DSL.name("sales", "inventory_documents"));
    private static final Field<String> INVENTORY_DOC_TYPE = DSL.field(DSL.name("doc_type"), String.class);
    private static final Field<String> INVENTORY_COMMENT = DSL.field(DSL.name("comment"), String.class);

    private final DSLContext dsl;
    private final MovementService movementService;

    public ConsignmentNoteService(DSLContext dsl, MovementService movementService) {
        this.dsl = dsl;
        this.movementService = movementService;
    }

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

    public boolean isPosted(int consignmentId) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(INVENTORY_DOCUMENTS)
                        .where(INVENTORY_DOC_TYPE.eq("receipt")
                                .and(INVENTORY_COMMENT.eq(buildMovementComment(consignmentId))))
        );
    }

    @Transactional
    public void postConsignmentNote(int consignmentId, int warehouseId) {
        if (warehouseId <= 0) {
            throw new IllegalArgumentException("Warehouse ID is required");
        }
        if (isPosted(consignmentId)) {
            throw new IllegalStateException("Накладная уже проведена");
        }

        ConsignmentnoteRecord note = dsl.selectFrom(CONSIGNMENTNOTE)
                .where(CONSIGNMENTNOTE.CONSIGNMENTID.eq(consignmentId))
                .fetchOne();
        if (note == null) {
            throw new RuntimeException("ConsignmentNote not found " + consignmentId);
        }

        List<ConsproductRecord> lines = dsl.selectFrom(CONSPRODUCT)
                .where(CONSPRODUCT.CONSIGNMENTID.eq(consignmentId))
                .fetch();
        if (lines.isEmpty()) {
            throw new IllegalStateException("В накладной нет товаров для проведения");
        }

        for (ConsproductRecord line : lines) {
            MovementRequestDTO movement = new MovementRequestDTO();
            movement.setDocType("receipt");
            movement.setDocDate(note.getDate() != null ? note.getDate().atStartOfDay() : null);
            movement.setToWarehouseId(warehouseId);
            movement.setProductId(line.getProductid());
            movement.setQuantity(line.getQuantity());
            movement.setSupplierId(note.getSupplierid());
            movement.setUnitPrice(line.getGross());
            movement.setComment(buildMovementComment(consignmentId));
            movement.setCreatedBy("consignment-ui");

            if (movementService.createMovement(movement) == null) {
                throw new IllegalStateException("Не удалось провести накладную");
            }
        }
    }

    @Transactional
    public boolean deleteConsignmentNote(int consignmentId) {
        if (isPosted(consignmentId)) {
            throw new IllegalStateException("Проведенную накладную удалять нельзя");
        }
        dsl.deleteFrom(CONSPRODUCT)
                .where(CONSPRODUCT.CONSIGNMENTID.eq(consignmentId))
                .execute();

        int deleted = dsl.deleteFrom(CONSIGNMENTNOTE)
                .where(CONSIGNMENTNOTE.CONSIGNMENTID.eq(consignmentId))
                .execute();
        return deleted > 0;
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

    private String buildMovementComment(int consignmentId) {
        return "consignment-note:" + consignmentId;
    }


}
