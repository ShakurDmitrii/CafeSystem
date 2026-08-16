package com.shakur.cafehelp.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shakur.cafehelp.config.BusinessTimeProvider;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static jooqdata.tables.Order.ORDER;

@Service
public class TaxOutboxWriterService {
    private static final Field<Boolean> IS_PAID = DSL.field(DSL.name("is_paid"), Boolean.class);
    private static final Field<String> PAYMENT_TYPE = DSL.field(DSL.name("payment_type"), String.class);
    private static final Field<LocalDateTime> CANCELLED_AT = DSL.field(DSL.name("cancelled_at"), LocalDateTime.class);
    private static final Field<String> CANCEL_REASON = DSL.field(DSL.name("cancel_reason"), String.class);
    private static final org.jooq.Table<?> TAX_OUTBOX = DSL.table(DSL.name("sales", "tax_outbox"));
    private static final Field<Long> OUTBOX_ID = DSL.field(DSL.name("id"), Long.class);
    private static final Field<String> AGGREGATE_TYPE = DSL.field(DSL.name("aggregate_type"), String.class);
    private static final Field<Integer> AGGREGATE_ID = DSL.field(DSL.name("aggregate_id"), Integer.class);
    private static final Field<String> EVENT_TYPE = DSL.field(DSL.name("event_type"), String.class);
    private static final Field<String> EVENT_KEY = DSL.field(DSL.name("event_key"), String.class);
    private static final Field<JSONB> PAYLOAD_JSON = DSL.field(DSL.name("payload_json"), JSONB.class);
    private static final Field<String> STATUS = DSL.field(DSL.name("status"), String.class);
    private static final Field<Timestamp> AVAILABLE_AT = DSL.field(DSL.name("available_at"), Timestamp.class);
    private static final Field<Timestamp> LOCKED_AT = DSL.field(DSL.name("locked_at"), Timestamp.class);
    private static final Field<Timestamp> PROCESSED_AT = DSL.field(DSL.name("processed_at"), Timestamp.class);
    private static final Field<Timestamp> UPDATED_AT = DSL.field(DSL.name("updated_at"), Timestamp.class);
    private static final Field<String> LAST_ERROR = DSL.field(DSL.name("last_error"), String.class);

    private final DSLContext dsl;
    private final ObjectMapper objectMapper;
    private final BusinessTimeProvider businessTime;

    public TaxOutboxWriterService(DSLContext dsl, ObjectMapper objectMapper, BusinessTimeProvider businessTime) {
        this.dsl = dsl;
        this.objectMapper = objectMapper;
        this.businessTime = businessTime;
    }

    public EnqueueResult enqueuePaidOrder(
            int orderId,
            Map<String, Object> basePayload,
            String eventType,
            String payloadSource,
            boolean forcePending,
            LocalDate receiptDate
    ) {
        Record order = dsl.select(
                        ORDER.ORDERID,
                        ORDER.SHIFTID,
                        ORDER.DATE,
                        IS_PAID,
                        PAYMENT_TYPE,
                        CANCELLED_AT
                )
                .from(ORDER)
                .where(ORDER.ORDERID.eq(orderId))
                .fetchOne();
        if (order == null || order.get(CANCELLED_AT) != null) {
            throw new IllegalStateException("Заказ " + orderId + " не найден или отменён");
        }
        String paymentType = order.get(PAYMENT_TYPE);
        String normalizedPayment = paymentType == null ? "" : paymentType.trim().toLowerCase(Locale.ROOT);
        if (!Boolean.TRUE.equals(order.get(IS_PAID))
                || !("cash".equals(normalizedPayment) || "transfer".equals(normalizedPayment))) {
            throw new IllegalStateException("Заказ " + orderId + " не является оплаченным (cash/transfer)");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        if (basePayload != null) payload.putAll(basePayload);
        LocalDate effectiveDate = receiptDate != null ? receiptDate : order.get(ORDER.DATE);
        payload.put("orderDate", effectiveDate != null ? effectiveDate.toString() : null);
        if (receiptDate != null) {
            payload.put("createdAt", receiptDate.atTime(12, 0).toString());
        }
        payload.put("shiftId", order.get(ORDER.SHIFTID));
        payload.put("source", payloadSource);
        payload.put("operationType", "sale");

        String eventKey = buildEventKey(orderId);
        return storeEvent(orderId, eventType, eventKey, payload, forcePending, effectiveDate);
    }

    public EnqueueResult enqueueRefund(
            int orderId,
            Map<String, Object> basePayload,
            String payloadSource
    ) {
        Record order = dsl.select(
                        ORDER.ORDERID,
                        ORDER.SHIFTID,
                        ORDER.DATE,
                        IS_PAID,
                        PAYMENT_TYPE,
                        CANCELLED_AT,
                        CANCEL_REASON
                )
                .from(ORDER)
                .where(ORDER.ORDERID.eq(orderId))
                .fetchOne();
        if (order == null || order.get(CANCELLED_AT) == null) {
            throw new IllegalStateException("Заказ " + orderId + " не найден или не отменён");
        }
        String paymentType = order.get(PAYMENT_TYPE);
        String normalizedPayment = paymentType == null ? "" : paymentType.trim().toLowerCase(Locale.ROOT);
        if (!Boolean.TRUE.equals(order.get(IS_PAID))
                || !("cash".equals(normalizedPayment) || "transfer".equals(normalizedPayment))) {
            throw new IllegalStateException("Возврат возможен только для оплаченного заказа cash/transfer");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        if (basePayload != null) payload.putAll(basePayload);
        payload.put("orderDate", order.get(ORDER.DATE) != null ? order.get(ORDER.DATE).toString() : null);
        payload.put("shiftId", order.get(ORDER.SHIFTID));
        payload.put("source", payloadSource);
        payload.put("operationType", "refund");
        payload.put("originalEventKey", buildEventKey(orderId));
        payload.put("correctionReason", order.get(CANCEL_REASON));
        payload.put("cancelledAt", order.get(CANCELLED_AT).toString());

        return storeEvent(
                orderId,
                "order_refund",
                buildRefundEventKey(orderId),
                payload,
                false,
                order.get(ORDER.DATE)
        );
    }

    private EnqueueResult storeEvent(
            int orderId,
            String eventType,
            String eventKey,
            Map<String, Object> payload,
            boolean forcePending,
            LocalDate effectiveDate
    ) {
        String payloadJson = toJson(payload);
        Field<JSONB> json = DSL.field("cast(? as jsonb)", JSONB.class, payloadJson);

        if (!forcePending) {
            Record1<Long> inserted = dsl.insertInto(TAX_OUTBOX)
                    .set(AGGREGATE_TYPE, "order")
                    .set(AGGREGATE_ID, orderId)
                    .set(EVENT_TYPE, eventType)
                    .set(EVENT_KEY, eventKey)
                    .set(PAYLOAD_JSON, json)
                    .set(STATUS, "pending")
                    .onConflict(EVENT_KEY)
                    .doNothing()
                    .returningResult(OUTBOX_ID)
                    .fetchOne();
            if (inserted != null && inserted.get(OUTBOX_ID) != null) {
                return new EnqueueResult(inserted.get(OUTBOX_ID), true, effectiveDate);
            }
            Long existingId = dsl.select(OUTBOX_ID)
                    .from(TAX_OUTBOX)
                    .where(EVENT_KEY.eq(eventKey))
                    .fetchOne(OUTBOX_ID);
            return new EnqueueResult(existingId != null ? existingId : 0L, false, effectiveDate);
        }

        Timestamp now = Timestamp.valueOf(businessTime.now());
        Long id = dsl.insertInto(TAX_OUTBOX)
                .set(AGGREGATE_TYPE, "order")
                .set(AGGREGATE_ID, orderId)
                .set(EVENT_TYPE, eventType)
                .set(EVENT_KEY, eventKey)
                .set(PAYLOAD_JSON, json)
                .set(STATUS, "pending")
                .onConflict(EVENT_KEY)
                .doUpdate()
                .set(EVENT_TYPE, eventType)
                .set(PAYLOAD_JSON, json)
                .set(STATUS, "pending")
                .set(AVAILABLE_AT, now)
                .set(LOCKED_AT, (Timestamp) null)
                .set(PROCESSED_AT, (Timestamp) null)
                .set(LAST_ERROR, (String) null)
                .set(UPDATED_AT, now)
                .returningResult(OUTBOX_ID)
                .fetchOne(OUTBOX_ID);
        if (id == null) {
            throw new IllegalStateException("Не удалось создать tax outbox для заказа " + orderId);
        }
        return new EnqueueResult(id, true, effectiveDate);
    }

    public String buildEventKey(int orderId) {
        return "order:" + orderId + ":paid";
    }

    public String buildRefundEventKey(int orderId) {
        return "order:" + orderId + ":refund";
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сериализовать payload чека", e);
        }
    }

    public record EnqueueResult(long outboxId, boolean inserted, LocalDate receiptDate) {
    }
}
