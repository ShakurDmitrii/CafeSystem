package com.shakur.cafehelp.Service;

import jooqdata.tables.records.OrderRecord;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Record1;
import org.jooq.Record2;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static jooqdata.tables.Order.ORDER;

@Service
public class TaxOutboxBackfillService {

    private static final Field<Boolean> IS_PAID_FIELD = DSL.field(DSL.name("is_paid"), Boolean.class);
    private static final Field<String> PAYMENT_TYPE_FIELD = DSL.field(DSL.name("payment_type"), String.class);
    private static final Field<LocalDateTime> CANCELLED_AT_FIELD = DSL.field(DSL.name("cancelled_at"), LocalDateTime.class);
    private static final org.jooq.Table<?> TAX_OUTBOX = DSL.table(DSL.name("sales", "tax_outbox"));
    private static final Field<Long> OUTBOX_ID = DSL.field(DSL.name("id"), Long.class);
    private static final Field<String> OUTBOX_AGGREGATE_TYPE = DSL.field(DSL.name("aggregate_type"), String.class);
    private static final Field<Integer> OUTBOX_AGGREGATE_ID = DSL.field(DSL.name("aggregate_id"), Integer.class);
    private static final Field<String> OUTBOX_EVENT_TYPE = DSL.field(DSL.name("event_type"), String.class);
    private static final Field<String> OUTBOX_EVENT_KEY = DSL.field(DSL.name("event_key"), String.class);
    private static final Field<JSONB> OUTBOX_PAYLOAD_JSON = DSL.field(DSL.name("payload_json"), JSONB.class);
    private static final Field<String> OUTBOX_STATUS = DSL.field(DSL.name("status"), String.class);
    private static final Field<Timestamp> OUTBOX_AVAILABLE_AT = DSL.field(DSL.name("available_at"), Timestamp.class);
    private static final Field<Timestamp> OUTBOX_LOCKED_AT = DSL.field(DSL.name("locked_at"), Timestamp.class);
    private static final Field<Timestamp> OUTBOX_PROCESSED_AT = DSL.field(DSL.name("processed_at"), Timestamp.class);
    private static final Field<Timestamp> OUTBOX_UPDATED_AT = DSL.field(DSL.name("updated_at"), Timestamp.class);
    private static final Field<String> OUTBOX_LAST_ERROR = DSL.field(DSL.name("last_error"), String.class);

    private final DSLContext dsl;
    private final OrderService orderService;
    private final TaxOutboxWriterService outboxWriterService;

    public TaxOutboxBackfillService(
            DSLContext dsl,
            OrderService orderService,
            TaxOutboxWriterService outboxWriterService
    ) {
        this.dsl = dsl;
        this.orderService = orderService;
        this.outboxWriterService = outboxWriterService;
    }

    @Transactional
    public BackfillResult enqueueExistingPaidOrders(LocalDate fromDate, LocalDate toDate, Integer limit) {
        int effectiveLimit = limit != null && limit > 0 ? limit : 1000;

        var base = dsl.selectFrom(ORDER)
                .where(IS_PAID_FIELD.eq(true))
                .and(CANCELLED_AT_FIELD.isNull())
                .and(PAYMENT_TYPE_FIELD.in("cash", "transfer"));

        if (fromDate != null) {
            base.and(ORDER.DATE.ge(fromDate));
        }
        if (toDate != null) {
            base.and(ORDER.DATE.le(toDate));
        }

        List<OrderRecord> paidOrders = base
                .orderBy(ORDER.ORDERID.asc())
                .limit(effectiveLimit)
                .fetchInto(OrderRecord.class);

        int scanned = paidOrders.size();
        int enqueued = 0;
        int skipped = 0;
        int payloadErrors = 0;
        List<Integer> failedOrderIds = new ArrayList<>();

        for (OrderRecord order : paidOrders) {
            Integer orderId = order.getOrderid();
            if (orderId == null || orderId <= 0) {
                continue;
            }

            String eventKey = buildEventKey(orderId);
            try {
                TaxOutboxWriterService.EnqueueResult inserted = enqueueOrderInternal(
                        order,
                        "order_paid_backfill",
                        "backfill",
                        false,
                        null
                );

                if (inserted.inserted()) {
                    enqueued++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                payloadErrors++;
                failedOrderIds.add(orderId);
            }
        }

        return new BackfillResult(scanned, enqueued, skipped, payloadErrors, failedOrderIds);
    }

    @Transactional
    public SingleOrderEnqueueResult enqueueSinglePaidOrder(int orderId, boolean forcePending, LocalDate receiptDate) {
        OrderRecord order = dsl.selectFrom(ORDER)
                .where(ORDER.ORDERID.eq(orderId))
                .and(CANCELLED_AT_FIELD.isNull())
                .fetchOne();

        if (order == null) {
            throw new RuntimeException("Заказ с id " + orderId + " не найден");
        }

        Record2<Boolean, String> payment = dsl.select(IS_PAID_FIELD, PAYMENT_TYPE_FIELD)
                .from(ORDER)
                .where(ORDER.ORDERID.eq(orderId))
                .and(CANCELLED_AT_FIELD.isNull())
                .fetchOne();

        boolean paid = payment != null && Boolean.TRUE.equals(payment.get(IS_PAID_FIELD));
        String paymentType = payment != null ? payment.get(PAYMENT_TYPE_FIELD) : null;
        String normalizedPayment = paymentType != null ? paymentType.toLowerCase() : "";
        boolean allowedPayment = "cash".equals(normalizedPayment) || "transfer".equals(normalizedPayment);

        if (!paid || !allowedPayment) {
            throw new RuntimeException("Заказ " + orderId + " не является оплаченным (cash/transfer)");
        }

        TaxOutboxWriterService.EnqueueResult result = enqueueOrderInternal(
                order,
                "order_paid_manual_send",
                "manual-send-one",
                forcePending,
                receiptDate
        );
        if (result == null || result.outboxId() <= 0) {
            throw new RuntimeException("Не удалось подготовить чек " + orderId + " в outbox");
        }
        return new SingleOrderEnqueueResult(orderId, result.outboxId(), forcePending, result.receiptDate());
    }

    public String buildEventKey(int orderId) {
        return outboxWriterService.buildEventKey(orderId);
    }

    private TaxOutboxWriterService.EnqueueResult enqueueOrderInternal(
            OrderRecord order,
            String eventType,
            String payloadSource,
            boolean forcePending,
            LocalDate receiptDate
    ) {
        Map<String, Object> payload = new LinkedHashMap<>(orderService.getOrderKitchenPrintPayload(
                order.getOrderid(),
                null,
                null,
                null,
                null
        ));
        return outboxWriterService.enqueuePaidOrder(
                order.getOrderid(),
                payload,
                eventType,
                payloadSource,
                forcePending,
                receiptDate
        );
    }

    public record BackfillResult(
            int scanned,
            int enqueued,
            int skipped,
            int payloadErrors,
            List<Integer> failedOrderIds
    ) {
    }

    public record SingleOrderEnqueueResult(
            int orderId,
            long outboxId,
            boolean forcedPending,
            LocalDate receiptDate
    ) {
    }
}
