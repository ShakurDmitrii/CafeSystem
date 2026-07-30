package com.shakur.cafehelp.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class TaxOutboxRelayService {

    private static final org.jooq.Table<?> TAX_OUTBOX = DSL.table(DSL.name("sales", "tax_outbox"));
    private static final org.jooq.Field<Long> OUTBOX_ID = DSL.field(DSL.name("id"), Long.class);
    private static final org.jooq.Field<Integer> OUTBOX_AGGREGATE_ID = DSL.field(DSL.name("aggregate_id"), Integer.class);
    private static final org.jooq.Field<String> OUTBOX_EVENT_KEY = DSL.field(DSL.name("event_key"), String.class);
    private static final org.jooq.Field<Integer> OUTBOX_ATTEMPT_COUNT = DSL.field(DSL.name("attempt_count"), Integer.class);
    private static final org.jooq.Field<String> OUTBOX_STATUS = DSL.field(DSL.name("status"), String.class);
    private static final org.jooq.Field<String> OUTBOX_LAST_ERROR = DSL.field(DSL.name("last_error"), String.class);
    private static final org.jooq.Field<Timestamp> OUTBOX_AVAILABLE_AT = DSL.field(DSL.name("available_at"), Timestamp.class);
    private static final org.jooq.Field<Timestamp> OUTBOX_PROCESSED_AT = DSL.field(DSL.name("processed_at"), Timestamp.class);
    private static final org.jooq.Field<Timestamp> OUTBOX_UPDATED_AT = DSL.field(DSL.name("updated_at"), Timestamp.class);
    private static final org.jooq.Field<Timestamp> OUTBOX_LOCKED_AT = DSL.field(DSL.name("locked_at"), Timestamp.class);

    private static final int MAX_ATTEMPTS = 10;

    private final DSLContext dsl;
    private final JdbcTemplate taxJdbcTemplate;
    private final ObjectMapper objectMapper;

    public TaxOutboxRelayService(
            DSLContext dsl,
            @Qualifier("taxJdbcTemplate") JdbcTemplate taxJdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.dsl = dsl;
        this.taxJdbcTemplate = taxJdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public RelayResult relayPending(int limit) {
        int effectiveLimit = limit > 0 ? limit : 500;
        List<ClaimedOutboxRow> claimed = claimPending(effectiveLimit);
        if (claimed.isEmpty()) {
            return new RelayResult(0, 0, 0, List.of());
        }

        int processed = 0;
        int failed = 0;
        List<Long> failedIds = new ArrayList<>();

        for (ClaimedOutboxRow row : claimed) {
            try {
                relayRowToTaxDb(row);
                markProcessed(row.id());
                processed++;
            } catch (Exception e) {
                markFailed(row, e);
                failed++;
                failedIds.add(row.id());
            }
        }

        return new RelayResult(claimed.size(), processed, failed, failedIds);
    }

    public RelayOneResult relayOutboxById(long outboxId) {
        ClaimedOutboxRow row = claimPendingById(outboxId);
        if (row == null) {
            String currentStatus = dsl.select(OUTBOX_STATUS)
                    .from(TAX_OUTBOX)
                    .where(OUTBOX_ID.eq(outboxId))
                    .fetchOne(OUTBOX_STATUS);
            if (currentStatus == null) {
                return new RelayOneResult(outboxId, false, false, false, "not_found", "Запись outbox не найдена");
            }
            return new RelayOneResult(
                    outboxId,
                    false,
                    false,
                    false,
                    currentStatus,
                    "Запись outbox не в статусе pending или еще недоступна для обработки"
            );
        }

        try {
            relayRowToTaxDb(row);
            markProcessed(row.id());
            return new RelayOneResult(outboxId, true, true, false, "processed", null);
        } catch (Exception e) {
            markFailed(row, e);
            String errorMessage = e.getMessage() != null
                    ? e.getMessage().substring(0, Math.min(500, e.getMessage().length()))
                    : "unknown relay error";
            return new RelayOneResult(outboxId, true, false, true, "failed", errorMessage);
        }
    }

    private List<ClaimedOutboxRow> claimPending(int limit) {
        var rows = dsl.fetch("""
                with picked as (
                    select id
                    from sales.tax_outbox
                    where status = 'pending'
                      and available_at <= now()
                    order by id
                    limit ?
                    for update skip locked
                )
                update sales.tax_outbox o
                set status = 'processing',
                    locked_at = now(),
                    updated_at = now()
                from picked
                where o.id = picked.id
                returning o.id,
                          o.aggregate_id,
                          o.event_key,
                          o.payload_json::text as payload_json,
                          o.attempt_count
                """, limit);

        List<ClaimedOutboxRow> result = new ArrayList<>();
        for (Record r : rows) {
            result.add(new ClaimedOutboxRow(
                    r.get("id", Long.class),
                    r.get("aggregate_id", Integer.class),
                    r.get("event_key", String.class),
                    r.get("payload_json", String.class),
                    r.get("attempt_count", Integer.class)
            ));
        }
        return result;
    }

    private ClaimedOutboxRow claimPendingById(long outboxId) {
        Record row = dsl.fetchOne("""
                update sales.tax_outbox o
                set status = 'processing',
                    locked_at = now(),
                    updated_at = now()
                where o.id = ?
                  and o.status = 'pending'
                  and o.available_at <= now()
                returning o.id,
                          o.aggregate_id,
                          o.event_key,
                          o.payload_json::text as payload_json,
                          o.attempt_count
                """, outboxId);

        if (row == null) {
            return null;
        }

        return new ClaimedOutboxRow(
                row.get("id", Long.class),
                row.get("aggregate_id", Integer.class),
                row.get("event_key", String.class),
                row.get("payload_json", String.class),
                row.get("attempt_count", Integer.class)
        );
    }

    private void relayRowToTaxDb(ClaimedOutboxRow row) throws Exception {
        JsonNode payload = objectMapper.readTree(row.payloadJson());
        LocalDate businessDate = resolveBusinessDate(payload);
        int shiftId = payload.path("shiftId").asInt(0);
        String paymentType = payload.path("paymentType").asText("cash");
        String customerPhone = payload.path("deliveryPhone").isNull()
                ? null
                : payload.path("deliveryPhone").asText(null);
        double total = payload.path("total").asDouble(0.0);

        String upsertSql = """
                insert into tax.tax_receipt_job (
                    source_system,
                    source_event_id,
                    order_id,
                    shift_id,
                    business_date,
                    amount,
                    payment_type,
                    customer_phone,
                    payload_json,
                    status,
                    idempotency_key,
                    next_attempt_at,
                    created_at,
                    updated_at
                ) values (
                    ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, now(), now(), now()
                )
                on conflict (idempotency_key)
                do update
                set payload_json = excluded.payload_json,
                    amount = excluded.amount,
                    payment_type = excluded.payment_type,
                    customer_phone = excluded.customer_phone,
                    updated_at = now()
                """;

        taxJdbcTemplate.update(
                upsertSql,
                "cafehelp",
                row.id(),
                row.orderId(),
                shiftId == 0 ? null : shiftId,
                businessDate,
                total,
                paymentType,
                customerPhone,
                row.payloadJson(),
                "pending",
                row.eventKey()
        );
    }

    private LocalDate resolveBusinessDate(JsonNode payload) {
        JsonNode orderDateNode = payload.get("orderDate");
        if (orderDateNode != null && !orderDateNode.isNull()) {
            String value = orderDateNode.asText(null);
            if (value != null && !value.isBlank()) {
                try {
                    return LocalDate.parse(value);
                } catch (Exception ignored) {
                }
            }
        }

        JsonNode createdAtNode = payload.get("createdAt");
        if (createdAtNode != null && !createdAtNode.isNull()) {
            String createdAt = createdAtNode.asText(null);
            if (createdAt != null && !createdAt.isBlank()) {
                try {
                    return LocalDateTime.parse(createdAt).toLocalDate();
                } catch (Exception ignored) {
                }
            }
        }

        return LocalDate.now();
    }

    private void markProcessed(Long outboxId) {
        dsl.update(TAX_OUTBOX)
                .set(OUTBOX_STATUS, "processed")
                .set(OUTBOX_PROCESSED_AT, Timestamp.valueOf(LocalDateTime.now()))
                .set(OUTBOX_UPDATED_AT, Timestamp.valueOf(LocalDateTime.now()))
                .set(OUTBOX_LAST_ERROR, (String) null)
                .where(OUTBOX_ID.eq(outboxId))
                .execute();
    }

    private void markFailed(ClaimedOutboxRow row, Exception error) {
        int currentAttempt = row.attemptCount() != null ? row.attemptCount() : 0;
        int nextAttempt = currentAttempt + 1;
        boolean deadLetter = nextAttempt >= MAX_ATTEMPTS;
        int nextDelayMinutes = Math.min(60, Math.max(1, nextAttempt * 2));

        LocalDateTime availableAt = LocalDateTime.now().plusMinutes(nextDelayMinutes);
        String shortError = error.getMessage() != null
                ? error.getMessage().substring(0, Math.min(2000, error.getMessage().length()))
                : "unknown relay error";

        dsl.update(TAX_OUTBOX)
                .set(OUTBOX_STATUS, deadLetter ? "dead_letter" : "pending")
                .set(OUTBOX_ATTEMPT_COUNT, nextAttempt)
                .set(OUTBOX_LAST_ERROR, shortError)
                .set(OUTBOX_AVAILABLE_AT, Timestamp.valueOf(availableAt))
                .set(OUTBOX_UPDATED_AT, Timestamp.valueOf(LocalDateTime.now()))
                .set(OUTBOX_LOCKED_AT, (Timestamp) null)
                .where(OUTBOX_ID.eq(row.id()))
                .execute();
    }

    private record ClaimedOutboxRow(
            Long id,
            Integer orderId,
            String eventKey,
            String payloadJson,
            Integer attemptCount
    ) {
    }

    public record RelayResult(
            int claimed,
            int processed,
            int failed,
            List<Long> failedOutboxIds
    ) {
    }

    public record RelayOneResult(
            long outboxId,
            boolean claimed,
            boolean processed,
            boolean failed,
            String finalStatus,
            String message
    ) {
    }
}
