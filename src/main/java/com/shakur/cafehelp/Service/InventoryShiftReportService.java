package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.InventoryShiftReportActualRowDTO;
import com.shakur.cafehelp.DTO.InventoryShiftReportApplyRequestDTO;
import com.shakur.cafehelp.DTO.InventoryShiftReportDTO;
import com.shakur.cafehelp.DTO.InventoryShiftReportRowDTO;
import com.shakur.cafehelp.DTO.InventoryShiftSaleItemDTO;
import jooqdata.tables.Dish;
import jooqdata.tables.Order;
import jooqdata.tables.Orderdish;
import jooqdata.tables.Productwarehouse;
import jooqdata.tables.Shift;
import jooqdata.tables.Warehouse;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class InventoryShiftReportService {
    private static final double EPS = 0.000001d;

    private static final Field<Integer> ORDERDISH_SET_ID = DSL.field(DSL.name("set_id"), Integer.class);
    private static final Field<LocalDateTime> ORDER_CANCELLED_AT = DSL.field(DSL.name("cancelled_at"), LocalDateTime.class);
    private static final org.jooq.Table<?> DISH_SET = DSL.table(DSL.name("sales", "dish_set"));
    private static final org.jooq.Table<?> DISH_SET_ITEM = DSL.table(DSL.name("sales", "dish_set_item"));
    private static final Field<Integer> DISH_SET_ID = DSL.field(DSL.name("setid"), Integer.class);
    private static final Field<String> DISH_SET_NAME = DSL.field(DSL.name("setname"), String.class);
    private static final Field<Integer> DISH_SET_ITEM_SET_ID = DSL.field(DSL.name("set_id"), Integer.class);
    private static final Field<Integer> DISH_SET_ITEM_DISH_ID = DSL.field(DSL.name("dish_id"), Integer.class);
    private static final Field<Integer> DISH_SET_ITEM_QTY = DSL.field(DSL.name("qty"), Integer.class);

    private static final org.jooq.Table<?> PRODUCT = DSL.table(DSL.name("sales", "product"));
    private static final Field<Integer> PRODUCT_ID = DSL.field(DSL.name("productid"), Integer.class);
    private static final Field<String> PRODUCT_NAME = DSL.field(DSL.name("productname"), String.class);
    private static final Field<String> PRODUCT_UNIT = DSL.field(DSL.name("unit"), String.class);
    private static final Field<String> PRODUCT_BASE_UNIT = DSL.field(DSL.name("base_unit"), String.class);

    private static final org.jooq.Table<?> SNAPSHOT = DSL.table(DSL.name("sales", "shift_inventory_snapshot"));
    private static final Field<Integer> SNAPSHOT_SHIFT_ID = DSL.field(DSL.name("shift_id"), Integer.class);
    private static final Field<Integer> SNAPSHOT_WAREHOUSE_ID = DSL.field(DSL.name("warehouse_id"), Integer.class);
    private static final Field<Integer> SNAPSHOT_PRODUCT_ID = DSL.field(DSL.name("product_id"), Integer.class);
    private static final Field<Double> SNAPSHOT_QUANTITY = DSL.field(DSL.name("quantity"), Double.class);

    private static final org.jooq.Table<?> STOCK_MOVEMENTS = DSL.table(DSL.name("sales", "stock_movements"));
    private static final Field<LocalDateTime> MOVEMENT_DATE = DSL.field(DSL.name("movement_date"), LocalDateTime.class);
    private static final Field<Integer> MOVEMENT_WAREHOUSE_ID = DSL.field(DSL.name("warehouse_id"), Integer.class);
    private static final Field<Integer> MOVEMENT_PRODUCT_ID = DSL.field(DSL.name("product_id"), Integer.class);
    private static final Field<java.math.BigDecimal> MOVEMENT_QTY_IN = DSL.field(DSL.name("qty_in"), java.math.BigDecimal.class);
    private static final Field<java.math.BigDecimal> MOVEMENT_QTY_OUT = DSL.field(DSL.name("qty_out"), java.math.BigDecimal.class);

    private static final org.jooq.Table<?> REPORT = DSL.table(DSL.name("sales", "inventory_shift_report"));
    private static final Field<Integer> REPORT_ID = DSL.field(DSL.name("id"), Integer.class);
    private static final Field<Integer> REPORT_WAREHOUSE_ID = DSL.field(DSL.name("warehouse_id"), Integer.class);
    private static final Field<Integer> REPORT_SHIFT_ID = DSL.field(DSL.name("shift_id"), Integer.class);
    private static final Field<Boolean> REPORT_SNAPSHOT_AVAILABLE = DSL.field(DSL.name("snapshot_available"), Boolean.class);
    private static final Field<LocalDateTime> REPORT_CREATED_AT = DSL.field(DSL.name("created_at"), LocalDateTime.class);
    private static final Field<LocalDateTime> REPORT_UPDATED_AT = DSL.field(DSL.name("updated_at"), LocalDateTime.class);
    private static final Field<LocalDateTime> REPORT_APPLIED_AT = DSL.field(DSL.name("applied_at"), LocalDateTime.class);

    private static final org.jooq.Table<?> REPORT_LINE = DSL.table(DSL.name("sales", "inventory_shift_report_line"));
    private static final Field<Integer> REPORT_LINE_REPORT_ID = DSL.field(DSL.name("report_id"), Integer.class);
    private static final Field<Integer> REPORT_LINE_PRODUCT_ID = DSL.field(DSL.name("product_id"), Integer.class);
    private static final Field<String> REPORT_LINE_PRODUCT_NAME = DSL.field(DSL.name("product_name"), String.class);
    private static final Field<String> REPORT_LINE_UNIT = DSL.field(DSL.name("unit"), String.class);
    private static final Field<Double> REPORT_LINE_OPENING_QTY = DSL.field(DSL.name("opening_qty"), Double.class);
    private static final Field<Double> REPORT_LINE_MOVEMENT_IN_QTY = DSL.field(DSL.name("movement_in_qty"), Double.class);
    private static final Field<Double> REPORT_LINE_MOVEMENT_OUT_QTY = DSL.field(DSL.name("movement_out_qty"), Double.class);
    private static final Field<Double> REPORT_LINE_MOVEMENT_NET_QTY = DSL.field(DSL.name("movement_net_qty"), Double.class);
    private static final Field<Double> REPORT_LINE_SOLD_QTY = DSL.field(DSL.name("sold_qty"), Double.class);
    private static final Field<Double> REPORT_LINE_EXPECTED_QTY = DSL.field(DSL.name("expected_qty"), Double.class);
    private static final Field<Double> REPORT_LINE_SYSTEM_QTY = DSL.field(DSL.name("system_qty"), Double.class);
    private static final Field<Double> REPORT_LINE_ACTUAL_QTY = DSL.field(DSL.name("actual_qty"), Double.class);
    private static final Field<Double> REPORT_LINE_DISCREPANCY_QTY = DSL.field(DSL.name("discrepancy_qty"), Double.class);
    private static final Field<Double> REPORT_LINE_SHORTAGE_QTY = DSL.field(DSL.name("shortage_qty"), Double.class);
    private static final Field<Boolean> REPORT_LINE_SHORTAGE_FLAG = DSL.field(DSL.name("shortage_flag"), Boolean.class);
    private static final Field<Integer> REPORT_LINE_SORT_ORDER = DSL.field(DSL.name("sort_order"), Integer.class);

    private final DSLContext dsl;
    private final WareHouseService wareHouseService;
    private final RecipeExpansionService recipeExpansionService;
    private volatile Boolean baseUnitPresent = null;

    public InventoryShiftReportService(
            DSLContext dsl,
            WareHouseService wareHouseService,
            RecipeExpansionService recipeExpansionService
    ) {
        this.dsl = dsl;
        this.wareHouseService = wareHouseService;
        this.recipeExpansionService = recipeExpansionService;
    }

    public InventoryShiftReportDTO getReport(Integer warehouseId, Integer shiftId) {
        int resolvedWarehouseId = resolveWarehouseId(warehouseId);
        ShiftRecordView shift = resolveShift(shiftId);
        ComputedReport computed = computeReport(resolvedWarehouseId, shift);
        SavedReport savedReport = loadSavedReport(resolvedWarehouseId, shift.id());
        return toDto(computed, savedReport);
    }

    @Transactional
    public InventoryShiftReportDTO applyActualBalances(Integer warehouseId, InventoryShiftReportApplyRequestDTO request) {
        if (request == null) {
            throw new RuntimeException("Не переданы данные отчёта");
        }

        int resolvedWarehouseId = resolveWarehouseId(warehouseId);
        ShiftRecordView shift = resolveShift(request.getShiftId());
        ComputedReport computed = computeReport(resolvedWarehouseId, shift);

        Map<Integer, Double> actualByProduct = new HashMap<>();
        if (request.getRows() != null) {
            for (InventoryShiftReportActualRowDTO row : request.getRows()) {
                if (row == null || row.getProductId() == null || row.getActualQty() == null) {
                    continue;
                }
                actualByProduct.put(row.getProductId(), Math.max(0.0, row.getActualQty()));
            }
        }

        LocalDateTime now = LocalDateTime.now();
        Integer reportId = dsl.select(REPORT_ID)
                .from(REPORT)
                .where(REPORT_WAREHOUSE_ID.eq(resolvedWarehouseId))
                .and(REPORT_SHIFT_ID.eq(shift.id()))
                .fetchOne(REPORT_ID);

        if (reportId == null) {
            reportId = dsl.insertInto(REPORT)
                    .set(REPORT_WAREHOUSE_ID, resolvedWarehouseId)
                    .set(REPORT_SHIFT_ID, shift.id())
                    .set(REPORT_SNAPSHOT_AVAILABLE, computed.snapshotAvailable())
                    .set(REPORT_CREATED_AT, now)
                    .set(REPORT_UPDATED_AT, now)
                    .set(REPORT_APPLIED_AT, now)
                    .returningResult(REPORT_ID)
                    .fetchOne(REPORT_ID);
        } else {
            dsl.update(REPORT)
                    .set(REPORT_SNAPSHOT_AVAILABLE, computed.snapshotAvailable())
                    .set(REPORT_UPDATED_AT, now)
                    .set(REPORT_APPLIED_AT, now)
                    .where(REPORT_ID.eq(reportId))
                    .execute();
        }

        Set<Integer> liveProductIds = new LinkedHashSet<>();
        int sortOrder = 0;
        for (InventoryShiftReportRowDTO row : computed.rows()) {
            Integer productId = row.getProductId();
            if (productId == null) {
                continue;
            }
            liveProductIds.add(productId);

            double actualQty = actualByProduct.getOrDefault(productId, safeDouble(row.getSystemQty()));
            double discrepancy = actualQty - safeDouble(row.getExpectedQty());

            dsl.insertInto(REPORT_LINE)
                    .set(REPORT_LINE_REPORT_ID, reportId)
                    .set(REPORT_LINE_PRODUCT_ID, productId)
                    .set(REPORT_LINE_PRODUCT_NAME, row.getProductName())
                    .set(REPORT_LINE_UNIT, row.getUnit())
                    .set(REPORT_LINE_OPENING_QTY, safeDouble(row.getOpeningQty()))
                    .set(REPORT_LINE_MOVEMENT_IN_QTY, safeDouble(row.getMovementInQty()))
                    .set(REPORT_LINE_MOVEMENT_OUT_QTY, safeDouble(row.getMovementOutQty()))
                    .set(REPORT_LINE_MOVEMENT_NET_QTY, safeDouble(row.getMovementNetQty()))
                    .set(REPORT_LINE_SOLD_QTY, safeDouble(row.getSoldQty()))
                    .set(REPORT_LINE_EXPECTED_QTY, safeDouble(row.getExpectedQty()))
                    .set(REPORT_LINE_SYSTEM_QTY, safeDouble(row.getSystemQty()))
                    .set(REPORT_LINE_ACTUAL_QTY, actualQty)
                    .set(REPORT_LINE_DISCREPANCY_QTY, discrepancy)
                    .set(REPORT_LINE_SHORTAGE_QTY, safeDouble(row.getShortageQty()))
                    .set(REPORT_LINE_SHORTAGE_FLAG, Boolean.TRUE.equals(row.getShortageFlag()))
                    .set(REPORT_LINE_SORT_ORDER, sortOrder++)
                    .onConflict(REPORT_LINE_REPORT_ID, REPORT_LINE_PRODUCT_ID)
                    .doUpdate()
                    .set(REPORT_LINE_PRODUCT_NAME, row.getProductName())
                    .set(REPORT_LINE_UNIT, row.getUnit())
                    .set(REPORT_LINE_OPENING_QTY, safeDouble(row.getOpeningQty()))
                    .set(REPORT_LINE_MOVEMENT_IN_QTY, safeDouble(row.getMovementInQty()))
                    .set(REPORT_LINE_MOVEMENT_OUT_QTY, safeDouble(row.getMovementOutQty()))
                    .set(REPORT_LINE_MOVEMENT_NET_QTY, safeDouble(row.getMovementNetQty()))
                    .set(REPORT_LINE_SOLD_QTY, safeDouble(row.getSoldQty()))
                    .set(REPORT_LINE_EXPECTED_QTY, safeDouble(row.getExpectedQty()))
                    .set(REPORT_LINE_SYSTEM_QTY, safeDouble(row.getSystemQty()))
                    .set(REPORT_LINE_ACTUAL_QTY, actualQty)
                    .set(REPORT_LINE_DISCREPANCY_QTY, discrepancy)
                    .set(REPORT_LINE_SHORTAGE_QTY, safeDouble(row.getShortageQty()))
                    .set(REPORT_LINE_SHORTAGE_FLAG, Boolean.TRUE.equals(row.getShortageFlag()))
                    .set(REPORT_LINE_SORT_ORDER, sortOrder - 1)
                    .execute();

            wareHouseService.setProductQuantity(resolvedWarehouseId, productId, actualQty);
        }

        if (liveProductIds.isEmpty()) {
            dsl.deleteFrom(REPORT_LINE)
                    .where(REPORT_LINE_REPORT_ID.eq(reportId))
                    .execute();
        } else {
            dsl.deleteFrom(REPORT_LINE)
                    .where(REPORT_LINE_REPORT_ID.eq(reportId))
                    .and(REPORT_LINE_PRODUCT_ID.notIn(liveProductIds))
                    .execute();
        }

        return getReport(resolvedWarehouseId, shift.id());
    }

    private InventoryShiftReportDTO toDto(ComputedReport computed, SavedReport savedReport) {
        InventoryShiftReportDTO dto = new InventoryShiftReportDTO();
        dto.setWarehouseId(computed.warehouseId());
        dto.setWarehouseName(computed.warehouseName());
        dto.setShiftId(computed.shiftId());
        dto.setShiftDate(computed.shiftDate());
        dto.setShiftStartTime(computed.shiftStartTime());
        dto.setShiftEndTime(computed.shiftEndTime());
        dto.setOrdersCount(computed.ordersCount());
        dto.setSoldPositionsCount(computed.sales().size());
        dto.setSoldItemsCount(computed.sales().stream().mapToInt(s -> s.getQty() != null ? s.getQty() : 0).sum());
        dto.setSales(computed.sales());

        if (savedReport != null) {
            dto.setReportId(savedReport.reportId());
            dto.setSnapshotAvailable(savedReport.snapshotAvailable());
            dto.setSaved(true);
            dto.setCreatedAt(savedReport.createdAt());
            dto.setUpdatedAt(savedReport.updatedAt());
            dto.setAppliedAt(savedReport.appliedAt());
            dto.setRows(savedReport.rows());
        } else {
            dto.setSnapshotAvailable(computed.snapshotAvailable());
            dto.setSaved(false);
            dto.setRows(computed.rows());
        }
        return dto;
    }

    private ComputedReport computeReport(int warehouseId, ShiftRecordView shift) {
        ShiftWindow window = buildShiftWindow(shift);
        String warehouseName = dsl.select(Warehouse.WAREHOUSE.WAREHOUSENAME)
                .from(Warehouse.WAREHOUSE)
                .where(Warehouse.WAREHOUSE.WAREHOUSEID.eq(warehouseId))
                .fetchOne(Warehouse.WAREHOUSE.WAREHOUSENAME);
        if (warehouseName == null) {
            throw new RuntimeException("Склад не найден: " + warehouseId);
        }

        Map<Integer, Double> snapshotByProduct = loadOpeningSnapshot(warehouseId, shift.id());
        boolean snapshotAvailable = !snapshotByProduct.isEmpty();

        MovementData movementData = loadMovementData(warehouseId, window);
        SalesData salesData = loadSalesData(shift.id(), window.start());
        Map<Integer, Double> currentByProduct = loadCurrentStockByProduct(warehouseId);

        Map<Integer, Double> openingByProduct = new HashMap<>(snapshotByProduct);
        if (!snapshotAvailable) {
            Set<Integer> fallbackProductIds = new LinkedHashSet<>();
            fallbackProductIds.addAll(currentByProduct.keySet());
            fallbackProductIds.addAll(movementData.netByProduct().keySet());
            fallbackProductIds.addAll(salesData.soldByProduct().keySet());
            for (Integer productId : fallbackProductIds) {
                double inferredOpening = currentByProduct.getOrDefault(productId, 0.0)
                        - movementData.netByProduct().getOrDefault(productId, 0.0)
                        + salesData.soldByProduct().getOrDefault(productId, 0.0);
                openingByProduct.put(productId, inferredOpening);
            }
        }

        Set<Integer> productIds = new LinkedHashSet<>();
        productIds.addAll(openingByProduct.keySet());
        productIds.addAll(movementData.netByProduct().keySet());
        productIds.addAll(salesData.soldByProduct().keySet());
        productIds.addAll(currentByProduct.keySet());

        Map<Integer, ProductSnapshot> productSnapshots = loadProductSnapshots(productIds);
        Map<Integer, Double> shortageByProduct = simulateShortages(productIds, openingByProduct, movementData.events(), salesData.events());

        List<InventoryShiftReportRowDTO> rows = productIds.stream()
                .map(productId -> {
                    ProductSnapshot product = productSnapshots.get(productId);
                    double openingQty = openingByProduct.getOrDefault(productId, 0.0);
                    double movementInQty = movementData.inByProduct().getOrDefault(productId, 0.0);
                    double movementOutQty = movementData.outByProduct().getOrDefault(productId, 0.0);
                    double movementNetQty = movementData.netByProduct().getOrDefault(productId, 0.0);
                    double soldQty = salesData.soldByProduct().getOrDefault(productId, 0.0);
                    double expectedQty = openingQty + movementNetQty - soldQty;
                    double currentQty = currentByProduct.getOrDefault(productId, 0.0);
                    double shortageQty = shortageByProduct.getOrDefault(productId, 0.0);

                    InventoryShiftReportRowDTO row = new InventoryShiftReportRowDTO();
                    row.setProductId(productId);
                    row.setProductName(product != null ? product.name() : "Продукт #" + productId);
                    row.setUnit(product != null ? product.unit() : "g");
                    row.setOpeningQty(openingQty);
                    row.setMovementInQty(movementInQty);
                    row.setMovementOutQty(movementOutQty);
                    row.setMovementNetQty(movementNetQty);
                    row.setSoldQty(soldQty);
                    row.setExpectedQty(expectedQty);
                    row.setSystemQty(currentQty);
                    row.setActualQty(null);
                    row.setDiscrepancyQty(null);
                    row.setShortageQty(shortageQty);
                    row.setShortageFlag(shortageQty > EPS);
                    return row;
                })
                .sorted(Comparator
                        .comparing((InventoryShiftReportRowDTO row) -> Boolean.TRUE.equals(row.getShortageFlag())).reversed()
                        .thenComparing((InventoryShiftReportRowDTO row) -> safeDouble(row.getSoldQty()), Comparator.reverseOrder())
                        .thenComparing(row -> String.valueOf(row.getProductName()), String.CASE_INSENSITIVE_ORDER))
                .toList();

        return new ComputedReport(
                warehouseId,
                warehouseName,
                shift.id(),
                shift.date(),
                shift.startTime(),
                shift.endTime(),
                salesData.orderCount(),
                snapshotAvailable,
                salesData.sales(),
                rows
        );
    }

    private Map<Integer, Double> loadOpeningSnapshot(int warehouseId, int shiftId) {
        Map<Integer, Double> result = new HashMap<>();
        dsl.select(SNAPSHOT_PRODUCT_ID, SNAPSHOT_QUANTITY)
                .from(SNAPSHOT)
                .where(SNAPSHOT_SHIFT_ID.eq(shiftId))
                .and(SNAPSHOT_WAREHOUSE_ID.eq(warehouseId))
                .fetch()
                .forEach(record -> result.put(
                        record.get(SNAPSHOT_PRODUCT_ID),
                        safeDouble(record.get(SNAPSHOT_QUANTITY))
                ));
        return result;
    }

    private MovementData loadMovementData(int warehouseId, ShiftWindow window) {
        Map<Integer, Double> inByProduct = new HashMap<>();
        Map<Integer, Double> outByProduct = new HashMap<>();
        List<StockEvent> events = new ArrayList<>();

        dsl.select(MOVEMENT_PRODUCT_ID, MOVEMENT_DATE, MOVEMENT_QTY_IN, MOVEMENT_QTY_OUT)
                .from(STOCK_MOVEMENTS)
                .where(MOVEMENT_WAREHOUSE_ID.eq(warehouseId))
                .and(MOVEMENT_DATE.ge(window.start()))
                .and(MOVEMENT_DATE.le(window.end()))
                .fetch()
                .forEach(record -> {
                    Integer productId = record.get(MOVEMENT_PRODUCT_ID);
                    if (productId == null) {
                        return;
                    }

                    double qtyIn = toDouble(record.get(MOVEMENT_QTY_IN));
                    double qtyOut = toDouble(record.get(MOVEMENT_QTY_OUT));
                    LocalDateTime timestamp = record.get(MOVEMENT_DATE) != null ? record.get(MOVEMENT_DATE) : window.start();

                    if (qtyIn > EPS) {
                        inByProduct.merge(productId, qtyIn, Double::sum);
                        events.add(StockEvent.movement(productId, timestamp, qtyIn));
                    }
                    if (qtyOut > EPS) {
                        outByProduct.merge(productId, qtyOut, Double::sum);
                        events.add(StockEvent.movement(productId, timestamp, -qtyOut));
                    }
                });

        Map<Integer, Double> netByProduct = new HashMap<>();
        Set<Integer> productIds = new LinkedHashSet<>();
        productIds.addAll(inByProduct.keySet());
        productIds.addAll(outByProduct.keySet());
        for (Integer productId : productIds) {
            netByProduct.put(
                    productId,
                    inByProduct.getOrDefault(productId, 0.0) - outByProduct.getOrDefault(productId, 0.0)
            );
        }

        return new MovementData(inByProduct, outByProduct, netByProduct, events);
    }

    private SalesData loadSalesData(int shiftId, LocalDateTime defaultTimestamp) {
        var orderRows = dsl.select(
                        Order.ORDER.ORDERID,
                        Order.ORDER.CREATED_AT,
                        Orderdish.ORDERDISH.DISHID,
                        ORDERDISH_SET_ID,
                        Orderdish.ORDERDISH.QTY
                )
                .from(Order.ORDER)
                .join(Orderdish.ORDERDISH).on(Orderdish.ORDERDISH.ORDERID.eq(Order.ORDER.ORDERID))
                .where(Order.ORDER.SHIFTID.eq(shiftId))
                .and(ORDER_CANCELLED_AT.isNull())
                .orderBy(Order.ORDER.CREATED_AT.asc().nullsLast(), Order.ORDER.ORDERID.asc(), Orderdish.ORDERDISH.ID.asc())
                .fetch();

        Set<Integer> orderIds = new LinkedHashSet<>();
        Set<Integer> dishIds = new LinkedHashSet<>();
        Set<Integer> setIds = new LinkedHashSet<>();
        for (Record row : orderRows) {
            Integer orderId = row.get(Order.ORDER.ORDERID);
            Integer dishId = row.get(Orderdish.ORDERDISH.DISHID);
            Integer setId = row.get(ORDERDISH_SET_ID);
            if (orderId != null) orderIds.add(orderId);
            if (dishId != null && dishId > 0) dishIds.add(dishId);
            if (setId != null && setId > 0) setIds.add(setId);
        }

        Map<Integer, String> dishNames = loadDishNames(dishIds);
        Map<Integer, String> setNames = loadSetNames(setIds);
        Map<Integer, List<SetDishRow>> setRows = loadSetRows(setIds);
        Map<Integer, Double> soldByProduct = new HashMap<>();
        Map<String, InventoryShiftSaleItemDTO> salesMap = new LinkedHashMap<>();
        List<StockEvent> events = new ArrayList<>();

        for (Record row : orderRows) {
            LocalDateTime timestamp = row.get(Order.ORDER.CREATED_AT) != null ? row.get(Order.ORDER.CREATED_AT) : defaultTimestamp;
            Integer dishId = row.get(Orderdish.ORDERDISH.DISHID);
            Integer setId = row.get(ORDERDISH_SET_ID);
            int qty = row.get(Orderdish.ORDERDISH.QTY) != null ? row.get(Orderdish.ORDERDISH.QTY) : 0;
            if (qty <= 0) {
                continue;
            }

            if (dishId != null && dishId > 0) {
                mergeSalesItem(salesMap, "dish:" + dishId, "dish", dishId, dishNames.getOrDefault(dishId, "Блюдо #" + dishId), qty);
                mergeSalesRequirements(soldByProduct, events, timestamp, recipeExpansionService.buildRequirementsForDish(dishId, qty));
                continue;
            }

            if (setId != null && setId > 0) {
                mergeSalesItem(salesMap, "set:" + setId, "set", setId, setNames.getOrDefault(setId, "Сет #" + setId), qty);
                for (SetDishRow setDish : setRows.getOrDefault(setId, List.of())) {
                    if (setDish.dishId() == null || setDish.dishId() <= 0 || setDish.qty() <= 0) {
                        continue;
                    }
                    mergeSalesRequirements(
                            soldByProduct,
                            events,
                            timestamp,
                            recipeExpansionService.buildRequirementsForDish(setDish.dishId(), qty * setDish.qty())
                    );
                }
            }
        }

        List<InventoryShiftSaleItemDTO> sales = salesMap.values().stream()
                .sorted(Comparator.comparing(item -> String.valueOf(item.getItemName()), String.CASE_INSENSITIVE_ORDER))
                .toList();

        return new SalesData(orderIds.size(), sales, soldByProduct, events);
    }

    private void mergeSalesRequirements(
            Map<Integer, Double> soldByProduct,
            List<StockEvent> events,
            LocalDateTime timestamp,
            Map<Integer, Double> requirements
    ) {
        if (requirements == null || requirements.isEmpty()) {
            return;
        }
        for (Map.Entry<Integer, Double> entry : requirements.entrySet()) {
            Integer productId = entry.getKey();
            double qty = entry.getValue() != null ? entry.getValue() : 0.0;
            if (productId == null || qty <= EPS) {
                continue;
            }
            soldByProduct.merge(productId, qty, Double::sum);
            events.add(StockEvent.sale(productId, timestamp, qty));
        }
    }

    private Map<Integer, Double> simulateShortages(
            Collection<Integer> productIds,
            Map<Integer, Double> openingByProduct,
            List<StockEvent> movementEvents,
            List<StockEvent> salesEvents
    ) {
        Map<Integer, Double> balances = new HashMap<>();
        if (productIds != null) {
            for (Integer productId : productIds) {
                balances.put(productId, openingByProduct.getOrDefault(productId, 0.0));
            }
        }

        List<StockEvent> timeline = new ArrayList<>();
        if (movementEvents != null) timeline.addAll(movementEvents);
        if (salesEvents != null) timeline.addAll(salesEvents);

        timeline.sort(Comparator.comparing(StockEvent::timestamp).thenComparing(StockEvent::sortOrder));

        Map<Integer, Double> shortageByProduct = new HashMap<>();
        for (StockEvent event : timeline) {
            Integer productId = event.productId();
            double balance = balances.getOrDefault(productId, 0.0);

            if (event.type() == StockEventType.MOVEMENT) {
                balances.put(productId, balance + event.delta());
                continue;
            }

            double saleQty = event.saleQty();
            double available = Math.max(balance, 0.0);
            if (available + EPS < saleQty) {
                shortageByProduct.merge(productId, saleQty - available, Double::sum);
            }
            balances.put(productId, balance - saleQty);
        }

        return shortageByProduct;
    }

    private SavedReport loadSavedReport(int warehouseId, int shiftId) {
        Record header = dsl.select(
                        REPORT_ID,
                        REPORT_SNAPSHOT_AVAILABLE,
                        REPORT_CREATED_AT,
                        REPORT_UPDATED_AT,
                        REPORT_APPLIED_AT
                )
                .from(REPORT)
                .where(REPORT_WAREHOUSE_ID.eq(warehouseId))
                .and(REPORT_SHIFT_ID.eq(shiftId))
                .fetchOne();
        if (header == null) {
            return null;
        }

        Integer reportId = header.get(REPORT_ID);
        List<InventoryShiftReportRowDTO> rows = dsl.select(
                        REPORT_LINE_PRODUCT_ID,
                        REPORT_LINE_PRODUCT_NAME,
                        REPORT_LINE_UNIT,
                        REPORT_LINE_OPENING_QTY,
                        REPORT_LINE_MOVEMENT_IN_QTY,
                        REPORT_LINE_MOVEMENT_OUT_QTY,
                        REPORT_LINE_MOVEMENT_NET_QTY,
                        REPORT_LINE_SOLD_QTY,
                        REPORT_LINE_EXPECTED_QTY,
                        REPORT_LINE_SYSTEM_QTY,
                        REPORT_LINE_ACTUAL_QTY,
                        REPORT_LINE_DISCREPANCY_QTY,
                        REPORT_LINE_SHORTAGE_QTY,
                        REPORT_LINE_SHORTAGE_FLAG,
                        REPORT_LINE_SORT_ORDER
                )
                .from(REPORT_LINE)
                .where(REPORT_LINE_REPORT_ID.eq(reportId))
                .orderBy(REPORT_LINE_SORT_ORDER.asc(), REPORT_LINE_PRODUCT_NAME.asc())
                .fetch(record -> {
                    InventoryShiftReportRowDTO row = new InventoryShiftReportRowDTO();
                    row.setProductId(record.get(REPORT_LINE_PRODUCT_ID));
                    row.setProductName(record.get(REPORT_LINE_PRODUCT_NAME));
                    row.setUnit(record.get(REPORT_LINE_UNIT));
                    row.setOpeningQty(record.get(REPORT_LINE_OPENING_QTY));
                    row.setMovementInQty(record.get(REPORT_LINE_MOVEMENT_IN_QTY));
                    row.setMovementOutQty(record.get(REPORT_LINE_MOVEMENT_OUT_QTY));
                    row.setMovementNetQty(record.get(REPORT_LINE_MOVEMENT_NET_QTY));
                    row.setSoldQty(record.get(REPORT_LINE_SOLD_QTY));
                    row.setExpectedQty(record.get(REPORT_LINE_EXPECTED_QTY));
                    row.setSystemQty(record.get(REPORT_LINE_SYSTEM_QTY));
                    row.setActualQty(record.get(REPORT_LINE_ACTUAL_QTY));
                    row.setDiscrepancyQty(record.get(REPORT_LINE_DISCREPANCY_QTY));
                    row.setShortageQty(record.get(REPORT_LINE_SHORTAGE_QTY));
                    row.setShortageFlag(Boolean.TRUE.equals(record.get(REPORT_LINE_SHORTAGE_FLAG)));
                    return row;
                });

        return new SavedReport(
                reportId,
                Boolean.TRUE.equals(header.get(REPORT_SNAPSHOT_AVAILABLE)),
                header.get(REPORT_CREATED_AT),
                header.get(REPORT_UPDATED_AT),
                header.get(REPORT_APPLIED_AT),
                rows
        );
    }

    private Map<Integer, String> loadDishNames(Collection<Integer> dishIds) {
        if (dishIds == null || dishIds.isEmpty()) {
            return Map.of();
        }
        return dsl.select(Dish.DISH.DISHID, Dish.DISH.DISHNAME)
                .from(Dish.DISH)
                .where(Dish.DISH.DISHID.in(dishIds))
                .fetchMap(Dish.DISH.DISHID, Dish.DISH.DISHNAME);
    }

    private Map<Integer, String> loadSetNames(Collection<Integer> setIds) {
        if (setIds == null || setIds.isEmpty()) {
            return Map.of();
        }
        return dsl.select(DISH_SET_ID, DISH_SET_NAME)
                .from(DISH_SET)
                .where(DISH_SET_ID.in(setIds))
                .fetchMap(DISH_SET_ID, DISH_SET_NAME);
    }

    private Map<Integer, List<SetDishRow>> loadSetRows(Collection<Integer> setIds) {
        if (setIds == null || setIds.isEmpty()) {
            return Map.of();
        }
        Map<Integer, List<SetDishRow>> result = new HashMap<>();
        dsl.select(DISH_SET_ITEM_SET_ID, DISH_SET_ITEM_DISH_ID, DISH_SET_ITEM_QTY)
                .from(DISH_SET_ITEM)
                .where(DISH_SET_ITEM_SET_ID.in(setIds))
                .fetch()
                .forEach(record -> result.computeIfAbsent(
                        record.get(DISH_SET_ITEM_SET_ID),
                        ignored -> new ArrayList<>()
                ).add(new SetDishRow(
                        record.get(DISH_SET_ITEM_DISH_ID),
                        record.get(DISH_SET_ITEM_QTY) != null ? record.get(DISH_SET_ITEM_QTY) : 0
                )));
        return result;
    }

    private Map<Integer, Double> loadCurrentStockByProduct(int warehouseId) {
        Map<Integer, Double> result = new HashMap<>();
        dsl.select(
                        Productwarehouse.PRODUCTWAREHOUSE.PRODUCTID,
                        DSL.sum(Productwarehouse.PRODUCTWAREHOUSE.QUANTITY).as("qty_sum")
                )
                .from(Productwarehouse.PRODUCTWAREHOUSE)
                .where(Productwarehouse.PRODUCTWAREHOUSE.WAREHOUSEID.eq(warehouseId))
                .groupBy(Productwarehouse.PRODUCTWAREHOUSE.PRODUCTID)
                .fetch()
                .forEach(record -> result.put(
                        record.get(Productwarehouse.PRODUCTWAREHOUSE.PRODUCTID),
                        safeDouble(record.get("qty_sum", Double.class))
                ));
        return result;
    }

    private Map<Integer, ProductSnapshot> loadProductSnapshots(Collection<Integer> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }

        Map<Integer, ProductSnapshot> result = new HashMap<>();
        if (hasBaseUnitColumn()) {
            dsl.select(PRODUCT_ID, PRODUCT_NAME, PRODUCT_BASE_UNIT)
                    .from(PRODUCT)
                    .where(PRODUCT_ID.in(productIds))
                    .fetch()
                    .forEach(record -> result.put(
                            record.get(PRODUCT_ID),
                            new ProductSnapshot(record.get(PRODUCT_NAME), normalizeUnit(record.get(PRODUCT_BASE_UNIT)))
                    ));
            return result;
        }

        dsl.select(PRODUCT_ID, PRODUCT_NAME, PRODUCT_UNIT)
                .from(PRODUCT)
                .where(PRODUCT_ID.in(productIds))
                .fetch()
                .forEach(record -> result.put(
                        record.get(PRODUCT_ID),
                        new ProductSnapshot(record.get(PRODUCT_NAME), normalizeUnit(record.get(PRODUCT_UNIT)))
                ));
        return result;
    }

    private boolean hasBaseUnitColumn() {
        if (baseUnitPresent != null) {
            return baseUnitPresent;
        }
        Integer count = dsl.selectCount()
                .from(DSL.table(DSL.name("information_schema", "columns")))
                .where(DSL.field(DSL.name("table_schema"), String.class).eq("sales"))
                .and(DSL.field(DSL.name("table_name"), String.class).eq("product"))
                .and(DSL.field(DSL.name("column_name"), String.class).eq("base_unit"))
                .fetchOne(0, Integer.class);
        baseUnitPresent = count != null && count > 0;
        return baseUnitPresent;
    }

    private String normalizeUnit(String unit) {
        return unit != null && !unit.isBlank() ? unit : "g";
    }

    private void mergeSalesItem(
            Map<String, InventoryShiftSaleItemDTO> salesMap,
            String key,
            String itemType,
            Integer itemId,
            String itemName,
            int qty
    ) {
        InventoryShiftSaleItemDTO item = salesMap.computeIfAbsent(key, ignored -> {
            InventoryShiftSaleItemDTO dto = new InventoryShiftSaleItemDTO();
            dto.setItemType(itemType);
            dto.setItemId(itemId);
            dto.setItemName(itemName);
            dto.setQty(0);
            return dto;
        });
        item.setQty((item.getQty() != null ? item.getQty() : 0) + qty);
    }

    private int resolveWarehouseId(Integer warehouseId) {
        if (warehouseId != null && warehouseId > 0) {
            return warehouseId;
        }
        Integer resolved = wareHouseService.getMainWarehouseId();
        if (resolved == null || resolved <= 0) {
            throw new RuntimeException("Не найден склад для отчёта");
        }
        return resolved;
    }

    private ShiftRecordView resolveShift(Integer shiftId) {
        Record record;
        if (shiftId != null && shiftId > 0) {
            record = dsl.select(Shift.SHIFT.ID, Shift.SHIFT.DATA, Shift.SHIFT.STARTTIME, Shift.SHIFT.ENDTIME)
                    .from(Shift.SHIFT)
                    .where(Shift.SHIFT.ID.eq(shiftId))
                    .fetchOne();
            if (record == null) {
                throw new RuntimeException("Смена не найдена: " + shiftId);
            }
        } else {
            record = dsl.select(Shift.SHIFT.ID, Shift.SHIFT.DATA, Shift.SHIFT.STARTTIME, Shift.SHIFT.ENDTIME)
                    .from(Shift.SHIFT)
                    .orderBy(Shift.SHIFT.DATA.desc().nullsLast(), Shift.SHIFT.STARTTIME.desc().nullsLast(), Shift.SHIFT.ID.desc())
                    .limit(1)
                    .fetchOne();
            if (record == null) {
                throw new RuntimeException("Нет смен для построения отчёта");
            }
        }

        return new ShiftRecordView(
                Objects.requireNonNull(record.get(Shift.SHIFT.ID)),
                record.get(Shift.SHIFT.DATA),
                record.get(Shift.SHIFT.STARTTIME),
                record.get(Shift.SHIFT.ENDTIME)
        );
    }

    private ShiftWindow buildShiftWindow(ShiftRecordView shift) {
        LocalDate shiftDate = shift.date() != null ? shift.date() : LocalDate.now();
        LocalTime startTime = shift.startTime() != null ? shift.startTime() : LocalTime.MIN;
        LocalDateTime start = LocalDateTime.of(shiftDate, startTime);

        LocalDateTime end;
        if (shift.endTime() != null) {
            end = LocalDateTime.of(shiftDate, shift.endTime());
            if (end.isBefore(start)) {
                end = end.plusDays(1);
            }
        } else {
            end = LocalDateTime.now();
            if (end.isBefore(start)) {
                end = start;
            }
        }
        return new ShiftWindow(start, end);
    }

    private double safeDouble(Double value) {
        return value != null ? value : 0.0;
    }

    private double toDouble(java.math.BigDecimal value) {
        return value != null ? value.doubleValue() : 0.0;
    }

    private record SetDishRow(Integer dishId, int qty) {}
    private record ProductSnapshot(String name, String unit) {}
    private record ShiftRecordView(int id, LocalDate date, LocalTime startTime, LocalTime endTime) {}
    private record ShiftWindow(LocalDateTime start, LocalDateTime end) {}
    private record ComputedReport(
            int warehouseId,
            String warehouseName,
            int shiftId,
            LocalDate shiftDate,
            LocalTime shiftStartTime,
            LocalTime shiftEndTime,
            int ordersCount,
            boolean snapshotAvailable,
            List<InventoryShiftSaleItemDTO> sales,
            List<InventoryShiftReportRowDTO> rows
    ) {}
    private record SavedReport(
            Integer reportId,
            boolean snapshotAvailable,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime appliedAt,
            List<InventoryShiftReportRowDTO> rows
    ) {}
    private record MovementData(
            Map<Integer, Double> inByProduct,
            Map<Integer, Double> outByProduct,
            Map<Integer, Double> netByProduct,
            List<StockEvent> events
    ) {}
    private record SalesData(
            int orderCount,
            List<InventoryShiftSaleItemDTO> sales,
            Map<Integer, Double> soldByProduct,
            List<StockEvent> events
    ) {}
    private enum StockEventType { MOVEMENT, SALE }
    private record StockEvent(Integer productId, LocalDateTime timestamp, StockEventType type, double delta, double saleQty) {
        static StockEvent movement(Integer productId, LocalDateTime timestamp, double delta) {
            return new StockEvent(productId, timestamp, StockEventType.MOVEMENT, delta, 0.0);
        }

        static StockEvent sale(Integer productId, LocalDateTime timestamp, double saleQty) {
            return new StockEvent(productId, timestamp, StockEventType.SALE, 0.0, saleQty);
        }

        int sortOrder() {
            return type == StockEventType.MOVEMENT ? 0 : 1;
        }
    }
}
