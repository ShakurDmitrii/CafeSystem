package com.shakur.cafehelp.Service;

import jooqdata.tables.Productwarehouse;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShiftInventorySnapshotService {
    private static final org.jooq.Table<?> SNAPSHOT = DSL.table(DSL.name("sales", "shift_inventory_snapshot"));
    private static final Field<Integer> SNAPSHOT_SHIFT_ID = DSL.field(DSL.name("shift_id"), Integer.class);
    private static final Field<Integer> SNAPSHOT_WAREHOUSE_ID = DSL.field(DSL.name("warehouse_id"), Integer.class);
    private static final Field<Integer> SNAPSHOT_PRODUCT_ID = DSL.field(DSL.name("product_id"), Integer.class);
    private static final Field<Double> SNAPSHOT_QUANTITY = DSL.field(DSL.name("quantity"), Double.class);

    private final DSLContext dsl;

    public ShiftInventorySnapshotService(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Transactional
    public void captureSnapshotForShift(int shiftId) {
        if (shiftId <= 0) {
            return;
        }

        Integer existing = dsl.selectCount()
                .from(SNAPSHOT)
                .where(SNAPSHOT_SHIFT_ID.eq(shiftId))
                .fetchOne(0, Integer.class);
        if (existing != null && existing > 0) {
            return;
        }

        dsl.select(
                        Productwarehouse.PRODUCTWAREHOUSE.WAREHOUSEID,
                        Productwarehouse.PRODUCTWAREHOUSE.PRODUCTID,
                        DSL.sum(Productwarehouse.PRODUCTWAREHOUSE.QUANTITY).as("qty_sum")
                )
                .from(Productwarehouse.PRODUCTWAREHOUSE)
                .groupBy(
                        Productwarehouse.PRODUCTWAREHOUSE.WAREHOUSEID,
                        Productwarehouse.PRODUCTWAREHOUSE.PRODUCTID
                )
                .fetch()
                .forEach(record -> dsl.insertInto(SNAPSHOT)
                        .set(SNAPSHOT_SHIFT_ID, shiftId)
                        .set(SNAPSHOT_WAREHOUSE_ID, record.get(Productwarehouse.PRODUCTWAREHOUSE.WAREHOUSEID))
                        .set(SNAPSHOT_PRODUCT_ID, record.get(Productwarehouse.PRODUCTWAREHOUSE.PRODUCTID))
                        .set(SNAPSHOT_QUANTITY, record.get("qty_sum", Double.class) != null ? record.get("qty_sum", Double.class) : 0.0)
                        .execute());
    }
}
