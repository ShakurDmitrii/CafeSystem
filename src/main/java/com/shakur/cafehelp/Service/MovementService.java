package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.ProductWarehouseDTO;
import com.shakur.cafehelp.DTO.MovementDTO;
import com.shakur.cafehelp.DTO.MovementReportRowDTO;
import com.shakur.cafehelp.DTO.MovementRequestDTO;
import com.shakur.cafehelp.DTO.MovementTurnoverRowDTO;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MovementService {

    private final DSLContext dsl;
    private final WareHouseService wareHouseService;
    private final UnitConversionService unitConversionService;

    public MovementService(DSLContext dsl, WareHouseService wareHouseService, UnitConversionService unitConversionService) {
        this.dsl = dsl;
        this.wareHouseService = wareHouseService;
        this.unitConversionService = unitConversionService;
    }

    private static final Table<?> INVENTORY_DOCUMENTS = DSL.table(DSL.name("sales", "inventory_documents"));
    private static final Table<?> INVENTORY_DOCUMENT_LINES = DSL.table(DSL.name("sales", "inventory_document_lines"));
    private static final Table<?> STOCK_MOVEMENTS = DSL.table(DSL.name("sales", "stock_movements"));
    private static final Table<?> PRODUCT = DSL.table(DSL.name("sales", "product"));

    private static final Field<Integer> DOC_ID = DSL.field(DSL.name("id"), Integer.class);
    private static final Field<String> DOC_TYPE = DSL.field(DSL.name("doc_type"), String.class);
    private static final Field<LocalDateTime> DOC_DATE = DSL.field(DSL.name("doc_date"), LocalDateTime.class);
    private static final Field<Integer> DOC_SUPPLIER_ID = DSL.field(DSL.name("supplier_id"), Integer.class);
    private static final Field<Integer> DOC_WH_FROM_ID = DSL.field(DSL.name("warehouse_from_id"), Integer.class);
    private static final Field<Integer> DOC_WH_TO_ID = DSL.field(DSL.name("warehouse_to_id"), Integer.class);
    private static final Field<String> DOC_STATUS = DSL.field(DSL.name("status"), String.class);
    private static final Field<String> DOC_COMMENT = DSL.field(DSL.name("comment"), String.class);
    private static final Field<String> DOC_CREATED_BY = DSL.field(DSL.name("created_by"), String.class);
    private static final Field<LocalDateTime> DOC_CREATED_AT = DSL.field(DSL.name("created_at"), LocalDateTime.class);

    private static final Field<Integer> LINE_DOCUMENT_ID = DSL.field(DSL.name("document_id"), Integer.class);
    private static final Field<Integer> LINE_PRODUCT_ID = DSL.field(DSL.name("product_id"), Integer.class);
    private static final Field<BigDecimal> LINE_QTY = DSL.field(DSL.name("qty"), BigDecimal.class);
    private static final Field<BigDecimal> LINE_UNIT_PRICE = DSL.field(DSL.name("unit_price"), BigDecimal.class);
    private static final Field<BigDecimal> LINE_TOTAL = DSL.field(DSL.name("line_total"), BigDecimal.class);

    private static final Field<LocalDateTime> MOVEMENT_DATE = DSL.field(DSL.name("movement_date"), LocalDateTime.class);
    private static final Field<Integer> MOVEMENT_DOCUMENT_ID = DSL.field(DSL.name("document_id"), Integer.class);
    private static final Field<Integer> MOVEMENT_WAREHOUSE_ID = DSL.field(DSL.name("warehouse_id"), Integer.class);
    private static final Field<Integer> MOVEMENT_PRODUCT_ID = DSL.field(DSL.name("product_id"), Integer.class);
    private static final Field<BigDecimal> MOVEMENT_QTY_IN = DSL.field(DSL.name("qty_in"), BigDecimal.class);
    private static final Field<BigDecimal> MOVEMENT_QTY_OUT = DSL.field(DSL.name("qty_out"), BigDecimal.class);
    private static final Field<BigDecimal> MOVEMENT_UNIT_COST = DSL.field(DSL.name("unit_cost"), BigDecimal.class);
    private static final Field<BigDecimal> MOVEMENT_AMOUNT = DSL.field(DSL.name("amount"), BigDecimal.class);
    private static final Field<LocalDateTime> MOVEMENT_CREATED_AT = DSL.field(DSL.name("created_at"), LocalDateTime.class);
    private static final Field<Integer> PRODUCT_PRODUCT_ID = DSL.field(DSL.name("productid"), Integer.class);
    private static final Field<Integer> PRODUCT_SUPPLIER_ID = DSL.field(DSL.name("supplierid"), Integer.class);
    private static final Table<?> PRODUCT_SUPPLIER = DSL.table(DSL.name("sales", "product_supplier"));
    private static final Field<Integer> PS_PRODUCT_ID = DSL.field(DSL.name("product_id"), Integer.class);
    private static final Field<Integer> PS_SUPPLIER_ID = DSL.field(DSL.name("supplier_id"), Integer.class);
    private static final Field<Double> PRODUCT_WASTE = DSL.field(DSL.name("waste"), Double.class);

    @Transactional
    public MovementDTO createMovement(MovementRequestDTO dto) {
        if (dto == null
                || dto.getProductId() == null
                || dto.getQuantity() == null
                || dto.getQuantity() <= 0) {
            return null;
        }

        String docType = dto.getDocType() == null ? "movement" : dto.getDocType().trim().toLowerCase();
        LocalDateTime now = dto.getDocDate() != null ? dto.getDocDate() : LocalDateTime.now();
        BigDecimal qtyBase = unitConversionService.toBaseQuantity(dto.getProductId(), dto.getQuantity());
        BigDecimal netQtyBase = qtyBase;
        BigDecimal unitPrice = dto.getUnitPrice() != null ? BigDecimal.valueOf(dto.getUnitPrice()) : null;
        BigDecimal total = unitPrice != null ? unitPrice.multiply(qtyBase) : null;

        Integer fromWarehouseId = dto.getFromWarehouseId();
        Integer toWarehouseId = dto.getToWarehouseId();
        Integer supplierId = dto.getSupplierId();

        // inventory_documents.supplier_id is NOT NULL in current DB schema.
        // If supplier is not explicitly provided, resolve it from product.
        if (supplierId == null) {
            supplierId = dsl.select(PS_SUPPLIER_ID)
                    .from(PRODUCT_SUPPLIER)
                    .where(PS_PRODUCT_ID.eq(dto.getProductId()))
                    .limit(1)
                    .fetchOne(PS_SUPPLIER_ID);
        }
        if (supplierId == null) {
            supplierId = dsl.select(PRODUCT_SUPPLIER_ID)
                    .from(PRODUCT)
                    .where(PRODUCT_PRODUCT_ID.eq(dto.getProductId()))
                    .fetchOne(PRODUCT_SUPPLIER_ID);
        }
        if (supplierId == null) {
            return null;
        }

        if ("receipt".equals(docType)) {
            Double wastePercentRaw = dsl.select(PRODUCT_WASTE)
                    .from(PRODUCT)
                    .where(PRODUCT_PRODUCT_ID.eq(dto.getProductId()))
                    .fetchOne(PRODUCT_WASTE);
            double wastePercent = wastePercentRaw == null ? 0.0 : wastePercentRaw;
            if (wastePercent < 0) wastePercent = 0;
            if (wastePercent > 100) wastePercent = 100;
            BigDecimal factor = BigDecimal.valueOf(1 - (wastePercent / 100.0));
            netQtyBase = qtyBase.multiply(factor);
        }

        if ("movement".equals(docType)) {
            if (fromWarehouseId == null
                    || toWarehouseId == null
                    || fromWarehouseId.equals(toWarehouseId)) {
                return null;
            }

            boolean moved = wareHouseService.moveProduct(
                    fromWarehouseId,
                    toWarehouseId,
                    dto.getProductId(),
                    qtyBase.doubleValue()
            );
            if (!moved) return null;
        } else if ("receipt".equals(docType)) {
            if (toWarehouseId == null) {
                return null;
            }

            boolean adjusted = wareHouseService.adjustQuantity(toWarehouseId, dto.getProductId(), netQtyBase.doubleValue());
            if (!adjusted) {
                ProductWarehouseDTO pw = new ProductWarehouseDTO();
                pw.setProductId(dto.getProductId());
                // addProductsToWarehouse() itself converts to base quantity by product factor
                Double netQtyDisplay = dto.getQuantity();
                if (dto.getQuantity() != null && qtyBase.compareTo(BigDecimal.ZERO) > 0) {
                    netQtyDisplay = netQtyBase
                            .divide(qtyBase, 6, java.math.RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(dto.getQuantity()))
                            .doubleValue();
                }
                pw.setQuantity(netQtyDisplay);
                wareHouseService.addProductsToWarehouse(toWarehouseId, List.of(pw));
            }
        } else if ("writeoff".equals(docType)) {
            if (fromWarehouseId == null) {
                return null;
            }
            boolean adjusted = wareHouseService.adjustQuantity(fromWarehouseId, dto.getProductId(), -qtyBase.doubleValue());
            if (!adjusted) return null;
        } else {
            return null;
        }

        Integer documentId = dsl.insertInto(INVENTORY_DOCUMENTS)
                .columns(DOC_TYPE, DOC_DATE, DOC_SUPPLIER_ID, DOC_WH_FROM_ID, DOC_WH_TO_ID, DOC_STATUS, DOC_COMMENT, DOC_CREATED_BY, DOC_CREATED_AT)
                .values(docType, now, supplierId, fromWarehouseId, toWarehouseId, "posted", dto.getComment(), dto.getCreatedBy(), now)
                .returning(DOC_ID)
                .fetchOne(DOC_ID);

        dsl.insertInto(INVENTORY_DOCUMENT_LINES)
                .columns(LINE_DOCUMENT_ID, LINE_PRODUCT_ID, LINE_QTY, LINE_UNIT_PRICE, LINE_TOTAL)
                .values(documentId, dto.getProductId(), qtyBase, unitPrice, total)
                .execute();

        dsl.insertInto(STOCK_MOVEMENTS)
                .columns(MOVEMENT_DATE, MOVEMENT_DOCUMENT_ID, MOVEMENT_WAREHOUSE_ID, MOVEMENT_PRODUCT_ID, MOVEMENT_QTY_IN, MOVEMENT_QTY_OUT, MOVEMENT_UNIT_COST, MOVEMENT_AMOUNT, MOVEMENT_CREATED_AT)
                .values(
                        now,
                        documentId,
                        "movement".equals(docType) || "writeoff".equals(docType) ? fromWarehouseId : toWarehouseId,
                        dto.getProductId(),
                        "receipt".equals(docType) ? netQtyBase : BigDecimal.ZERO,
                        "receipt".equals(docType) ? BigDecimal.ZERO : qtyBase,
                        unitPrice,
                        total,
                        now
                )
                .execute();

        if ("movement".equals(docType)) {
            dsl.insertInto(STOCK_MOVEMENTS)
                    .columns(MOVEMENT_DATE, MOVEMENT_DOCUMENT_ID, MOVEMENT_WAREHOUSE_ID, MOVEMENT_PRODUCT_ID, MOVEMENT_QTY_IN, MOVEMENT_QTY_OUT, MOVEMENT_UNIT_COST, MOVEMENT_AMOUNT, MOVEMENT_CREATED_AT)
                    .values(now, documentId, toWarehouseId, dto.getProductId(), qtyBase, BigDecimal.ZERO, unitPrice, total, now)
                    .execute();
        }

        MovementDTO result = new MovementDTO();
        result.setId(documentId);
        result.setDocType(docType);
        result.setDocDate(now);
        result.setSupplierId(supplierId);
        result.setFromWarehouseId(fromWarehouseId);
        result.setToWarehouseId(toWarehouseId);
        result.setProductId(dto.getProductId());
        result.setQuantity(qtyBase);
        result.setUnitPrice(unitPrice);
        result.setLineTotal(total);
        result.setStatus("posted");
        result.setComment(dto.getComment());
        result.setCreatedBy(dto.getCreatedBy());
        return result;
    }

    public List<MovementDTO> getAllMovements() {
        Table<?> d = INVENTORY_DOCUMENTS.as("d");
        Table<?> l = INVENTORY_DOCUMENT_LINES.as("l");

        Field<Integer> dId = DSL.field(DSL.name("d", "id"), Integer.class);
        Field<String> dType = DSL.field(DSL.name("d", "doc_type"), String.class);
        Field<LocalDateTime> dDate = DSL.field(DSL.name("d", "doc_date"), LocalDateTime.class);
        Field<Integer> dSupplierId = DSL.field(DSL.name("d", "supplier_id"), Integer.class);
        Field<Integer> dWhFromId = DSL.field(DSL.name("d", "warehouse_from_id"), Integer.class);
        Field<Integer> dWhToId = DSL.field(DSL.name("d", "warehouse_to_id"), Integer.class);
        Field<String> dStatus = DSL.field(DSL.name("d", "status"), String.class);
        Field<String> dComment = DSL.field(DSL.name("d", "comment"), String.class);
        Field<String> dCreatedBy = DSL.field(DSL.name("d", "created_by"), String.class);

        Field<Integer> lDocumentId = DSL.field(DSL.name("l", "document_id"), Integer.class);
        Field<Integer> lProductId = DSL.field(DSL.name("l", "product_id"), Integer.class);
        Field<BigDecimal> lQty = DSL.field(DSL.name("l", "qty"), BigDecimal.class);
        Field<BigDecimal> lUnitPrice = DSL.field(DSL.name("l", "unit_price"), BigDecimal.class);
        Field<BigDecimal> lTotal = DSL.field(DSL.name("l", "line_total"), BigDecimal.class);

        return dsl.select(
                        dId,
                        dType,
                        dDate,
                        dSupplierId,
                        dWhFromId,
                        dWhToId,
                        dStatus,
                        dComment,
                        dCreatedBy,
                        lProductId,
                        lQty,
                        lUnitPrice,
                        lTotal
                )
                .from(d)
                .join(l).on(dId.eq(lDocumentId))
                .orderBy(dDate.desc(), dId.desc())
                .fetch()
                .map(r -> {
                    MovementDTO dto = new MovementDTO();
                    dto.setId(r.get(dId));
                    dto.setDocType(r.get(dType));
                    dto.setDocDate(r.get(dDate));
                    dto.setSupplierId(r.get(dSupplierId));
                    dto.setFromWarehouseId(r.get(dWhFromId));
                    dto.setToWarehouseId(r.get(dWhToId));
                    dto.setStatus(r.get(dStatus));
                    dto.setComment(r.get(dComment));
                    dto.setCreatedBy(r.get(dCreatedBy));
                    dto.setProductId(r.get(lProductId));
                    dto.setQuantity(r.get(lQty));
                    dto.setUnitPrice(r.get(lUnitPrice));
                    dto.setLineTotal(r.get(lTotal));
                    return dto;
                });
    }

    @Transactional
    public boolean updateMovementDate(int documentId, LocalDateTime newDocDate) {
        if (newDocDate == null) return false;

        int updatedDoc = dsl.update(INVENTORY_DOCUMENTS)
                .set(DOC_DATE, newDocDate)
                .where(DOC_ID.eq(documentId))
                .execute();

        if (updatedDoc == 0) return false;

        dsl.update(STOCK_MOVEMENTS)
                .set(MOVEMENT_DATE, newDocDate)
                .where(MOVEMENT_DOCUMENT_ID.eq(documentId))
                .execute();

        return true;
    }

    public List<MovementReportRowDTO> getReceiptReport(Integer productId, LocalDate dateFrom, LocalDate dateTo) {
        if (productId == null || dateFrom == null || dateTo == null) return List.of();
        if (dateTo.isBefore(dateFrom)) return List.of();

        Table<?> d = INVENTORY_DOCUMENTS.as("d");
        Table<?> l = INVENTORY_DOCUMENT_LINES.as("l");

        Field<Integer> dId = DSL.field(DSL.name("d", "id"), Integer.class);
        Field<String> dType = DSL.field(DSL.name("d", "doc_type"), String.class);
        Field<LocalDateTime> dDate = DSL.field(DSL.name("d", "doc_date"), LocalDateTime.class);

        Field<Integer> lDocumentId = DSL.field(DSL.name("l", "document_id"), Integer.class);
        Field<Integer> lProductId = DSL.field(DSL.name("l", "product_id"), Integer.class);
        Field<BigDecimal> lQty = DSL.field(DSL.name("l", "qty"), BigDecimal.class);
        Field<BigDecimal> lUnitPrice = DSL.field(DSL.name("l", "unit_price"), BigDecimal.class);
        Field<BigDecimal> lTotal = DSL.field(DSL.name("l", "line_total"), BigDecimal.class);

        LocalDateTime from = dateFrom.atStartOfDay();
        LocalDateTime to = dateTo.atTime(LocalTime.MAX);

        var rows = dsl.select(
                        dId,
                        dDate,
                        lProductId,
                        lQty,
                        lUnitPrice,
                        lTotal
                )
                .from(d)
                .join(l).on(dId.eq(lDocumentId))
                .where(dType.eq("receipt"))
                .and(lProductId.eq(productId))
                .and(dDate.ge(from))
                .and(dDate.le(to))
                .orderBy(dDate.asc(), dId.asc())
                .fetch();

        List<MovementReportRowDTO> result = new ArrayList<>();
        BigDecimal prevPrice = null;
        BigDecimal prevQty = null;

        for (Record r : rows) {
            BigDecimal price = r.get(lUnitPrice);
            BigDecimal qty = r.get(lQty);

            MovementReportRowDTO dto = new MovementReportRowDTO();
            dto.setDocumentId(r.get(dId));
            dto.setDocDate(r.get(dDate));
            dto.setProductId(r.get(lProductId));
            dto.setQuantity(qty);
            dto.setUnitPrice(price);
            dto.setLineTotal(r.get(lTotal));
            dto.setPriceDelta(prevPrice != null && price != null ? price.subtract(prevPrice) : null);
            dto.setQuantityDelta(prevQty != null && qty != null ? qty.subtract(prevQty) : null);
            result.add(dto);

            if (price != null) prevPrice = price;
            if (qty != null) prevQty = qty;
        }

        return result;
    }

    public List<MovementTurnoverRowDTO> getTurnoverReport(Integer productId, String productName, LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom == null || dateTo == null) return List.of();
        if (dateTo.isBefore(dateFrom)) return List.of();

        Table<?> d = INVENTORY_DOCUMENTS.as("d");
        Table<?> l = INVENTORY_DOCUMENT_LINES.as("l");
        Table<?> p = PRODUCT.as("p");

        Field<Integer> dId = DSL.field(DSL.name("d", "id"), Integer.class);
        Field<String> dType = DSL.field(DSL.name("d", "doc_type"), String.class);
        Field<LocalDateTime> dDate = DSL.field(DSL.name("d", "doc_date"), LocalDateTime.class);
        Field<Integer> lDocumentId = DSL.field(DSL.name("l", "document_id"), Integer.class);
        Field<Integer> lProductId = DSL.field(DSL.name("l", "product_id"), Integer.class);
        Field<BigDecimal> lQty = DSL.field(DSL.name("l", "qty"), BigDecimal.class);
        Field<BigDecimal> lTotal = DSL.field(DSL.name("l", "line_total"), BigDecimal.class);
        Field<Integer> pProductId = DSL.field(DSL.name("p", "productid"), Integer.class);
        Field<String> pProductName = DSL.field(DSL.name("p", "productname"), String.class);

        LocalDateTime from = dateFrom.atStartOfDay();
        LocalDateTime to = dateTo.atTime(LocalTime.MAX);

        var query = dsl.select(dType, lProductId, lQty, lTotal, pProductName)
                .from(d)
                .join(l).on(dId.eq(lDocumentId))
                .leftJoin(p).on(pProductId.eq(lProductId))
                .where(dDate.ge(from))
                .and(dDate.le(to))
                .and(dType.in("receipt", "movement", "writeoff"));

        if (productId != null) {
            query = query.and(lProductId.eq(productId));
        } else if (productName != null && !productName.isBlank()) {
            query = query.and(DSL.lower(pProductName).eq(productName.trim().toLowerCase(Locale.ROOT)));
        }

        var rows = query.orderBy(pProductName.asc().nullsLast(), lProductId.asc(), dType.asc()).fetch();

        Map<String, MovementTurnoverRowDTO> byProduct = new LinkedHashMap<>();

        for (Record r : rows) {
            Integer pid = r.get(lProductId);
            if (pid == null) continue;

            String pName = r.get(pProductName);
            String normalizedName = pName != null && !pName.isBlank()
                    ? pName.trim().toLowerCase(Locale.ROOT)
                    : ("#pid:" + pid);

            MovementTurnoverRowDTO dto = byProduct.computeIfAbsent(normalizedName, key -> {
                MovementTurnoverRowDTO x = new MovementTurnoverRowDTO();
                x.setProductId(pid);
                x.setProductName((pName != null && !pName.isBlank()) ? pName.trim() : ("Товар #" + pid));
                x.setQtyIn(BigDecimal.ZERO);
                x.setQtyOutMovement(BigDecimal.ZERO);
                x.setQtyWriteoff(BigDecimal.ZERO);
                x.setQtyOutTotal(BigDecimal.ZERO);
                x.setAmountIn(BigDecimal.ZERO);
                x.setAmountOutMovement(BigDecimal.ZERO);
                x.setAmountWriteoff(BigDecimal.ZERO);
                x.setAmountOutTotal(BigDecimal.ZERO);
                return x;
            });

            String type = r.get(dType);
            BigDecimal qty = r.get(lQty) != null ? r.get(lQty) : BigDecimal.ZERO;
            BigDecimal amount = r.get(lTotal) != null ? r.get(lTotal) : BigDecimal.ZERO;

            if ("receipt".equals(type)) {
                dto.setQtyIn(dto.getQtyIn().add(qty));
                dto.setAmountIn(dto.getAmountIn().add(amount));
            } else if ("movement".equals(type)) {
                dto.setQtyOutMovement(dto.getQtyOutMovement().add(qty));
                dto.setAmountOutMovement(dto.getAmountOutMovement().add(amount));
            } else if ("writeoff".equals(type)) {
                dto.setQtyWriteoff(dto.getQtyWriteoff().add(qty));
                dto.setAmountWriteoff(dto.getAmountWriteoff().add(amount));
            }
        }

        for (MovementTurnoverRowDTO dto : byProduct.values()) {
            dto.setQtyOutTotal(dto.getQtyOutMovement().add(dto.getQtyWriteoff()));
            dto.setAmountOutTotal(dto.getAmountOutMovement().add(dto.getAmountWriteoff()));
        }

        return new ArrayList<>(byProduct.values());
    }
}
