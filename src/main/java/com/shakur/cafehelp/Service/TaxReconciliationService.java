package com.shakur.cafehelp.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shakur.cafehelp.config.BusinessTimeProvider;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static jooqdata.tables.Order.ORDER;

@Service
public class TaxReconciliationService {

    private static final Field<Boolean> IS_PAID = DSL.field(DSL.name("is_paid"), Boolean.class);
    private static final Field<String> PAYMENT_TYPE = DSL.field(DSL.name("payment_type"), String.class);
    private static final Field<java.time.LocalDateTime> CANCELLED_AT =
            DSL.field(DSL.name("cancelled_at"), java.time.LocalDateTime.class);
    private static final org.jooq.Table<?> TAX_OUTBOX = DSL.table(DSL.name("sales", "tax_outbox"));
    private static final Field<Long> OUTBOX_ID = DSL.field(DSL.name("id"), Long.class);
    private static final Field<Integer> OUTBOX_ORDER_ID = DSL.field(DSL.name("aggregate_id"), Integer.class);
    private static final Field<String> OUTBOX_EVENT_KEY = DSL.field(DSL.name("event_key"), String.class);
    private static final Field<String> OUTBOX_STATUS = DSL.field(DSL.name("status"), String.class);

    private final DSLContext dsl;
    private final JdbcTemplate taxJdbcTemplate;
    private final TaxOutboxBackfillService backfillService;
    private final ObjectMapper objectMapper;
    private final BusinessTimeProvider businessTime;

    public TaxReconciliationService(
            DSLContext dsl,
            @Qualifier("taxJdbcTemplate") JdbcTemplate taxJdbcTemplate,
            TaxOutboxBackfillService backfillService,
            ObjectMapper objectMapper,
            BusinessTimeProvider businessTime
    ) {
        this.dsl = dsl;
        this.taxJdbcTemplate = taxJdbcTemplate;
        this.backfillService = backfillService;
        this.objectMapper = objectMapper;
        this.businessTime = businessTime;
    }

    public ReconcileResult reconcile(LocalDate businessDate, Integer limit) {
        LocalDate effectiveDate = businessDate != null ? businessDate : businessTime.today();
        int effectiveLimit = limit != null && limit > 0 ? Math.min(limit, 5000) : 1000;
        int sourceOrdersCount = countSourceOrders(effectiveDate);
        List<SourceOrder> sourceOrders = fetchSourceOrders(effectiveDate, effectiveLimit);
        List<Integer> orderIds = sourceOrders.stream()
                .filter(order -> !order.cancelled())
                .map(SourceOrder::orderId)
                .toList();
        List<Integer> cancelledOrderIds = sourceOrders.stream()
                .filter(SourceOrder::cancelled)
                .map(SourceOrder::orderId)
                .toList();
        int scannedOrdersCount = sourceOrders.size();
        long runId = startRun(effectiveDate, sourceOrdersCount, scannedOrdersCount, effectiveLimit);

        int outboxRestored = 0;
        int outboxRequeued = 0;
        int taxJobsMissing = 0;
        int jobsFound = 0;
        int sent = 0;
        int failed = 0;

        try {
            Map<Integer, OutboxState> outboxByOrder = fetchOutboxStates(orderIds);
            for (Integer orderId : orderIds) {
                if (outboxByOrder.containsKey(orderId)) continue;

                backfillService.enqueueSinglePaidOrder(orderId, false, null);
                outboxRestored++;
                recordGap(
                        runId,
                        orderId,
                        "OUTBOX_MISSING",
                        Map.of("repair", "outbox_created", "businessDate", effectiveDate.toString()),
                        true
                );
            }

            outboxByOrder = fetchOutboxStates(orderIds);
            Map<String, TaxJobState> jobsByKey = fetchTaxJobs(outboxByOrder.values().stream()
                    .map(OutboxState::eventKey)
                    .toList());

            for (Integer orderId : orderIds) {
                OutboxState outbox = outboxByOrder.get(orderId);
                if (outbox == null) {
                    recordGap(
                            runId,
                            orderId,
                            "OUTBOX_REPAIR_FAILED",
                            Map.of("businessDate", effectiveDate.toString()),
                            false
                    );
                    failed++;
                    continue;
                }

                TaxJobState job = jobsByKey.get(outbox.eventKey());
                if (job == null) {
                    if ("processed".equals(outbox.status())) {
                        int requeued = requeueProcessedOutbox(outbox.id());
                        outboxRequeued += requeued;
                        taxJobsMissing++;
                        recordGap(
                                runId,
                                orderId,
                                "TAX_JOB_MISSING",
                                Map.of(
                                        "outboxId", outbox.id(),
                                        "outboxStatus", outbox.status(),
                                        "repair", requeued == 1 ? "outbox_requeued" : "already_changed"
                                ),
                                false
                        );
                    } else if ("failed".equals(outbox.status())) {
                        failed++;
                        recordGap(
                                runId,
                                orderId,
                                "OUTBOX_FAILED",
                                Map.of("outboxId", outbox.id(), "outboxStatus", outbox.status()),
                                false
                        );
                    }
                    continue;
                }

                jobsFound++;
                resolveGap(orderId, "TAX_JOB_MISSING");
                resolveGap(orderId, "OUTBOX_FAILED");
                if ("sent".equals(job.status())) {
                    sent++;
                    resolveGap(orderId, "TAX_JOB_MANUAL_REQUIRED");
                } else if ("failed".equals(job.status()) || "manual_required".equals(job.status())) {
                    failed++;
                    recordGap(
                            runId,
                            orderId,
                            "TAX_JOB_MANUAL_REQUIRED",
                            Map.of("jobId", job.id(), "jobStatus", job.status()),
                            false
                    );
                }
            }

            for (Integer orderId : cancelledOrderIds) {
                List<ExpectedEvent> expectedEvents = List.of(
                        new ExpectedEvent(backfillService.buildEventKey(orderId), "SALE_OUTBOX_MISSING", "TAX_SALE_JOB_MISSING"),
                        new ExpectedEvent("order:" + orderId + ":refund", "REFUND_OUTBOX_MISSING", "TAX_REFUND_JOB_MISSING")
                );
                for (ExpectedEvent expectedEvent : expectedEvents) {
                    OutboxState outbox = fetchOutboxState(expectedEvent.eventKey());
                    if (outbox == null) {
                        failed++;
                        recordGap(
                                runId,
                                orderId,
                                expectedEvent.missingOutboxReason(),
                                Map.of(
                                        "eventKey", expectedEvent.eventKey(),
                                        "repair", "manual_snapshot_required"
                                ),
                                false
                        );
                        continue;
                    }
                    resolveGap(orderId, expectedEvent.missingOutboxReason());

                    TaxJobState job = fetchTaxJob(expectedEvent.eventKey());
                    if (job == null) {
                        if ("processed".equals(outbox.status())) {
                            int requeued = requeueProcessedOutbox(outbox.id());
                            outboxRequeued += requeued;
                            taxJobsMissing++;
                            recordGap(
                                    runId,
                                    orderId,
                                    expectedEvent.missingJobReason(),
                                    Map.of(
                                            "eventKey", expectedEvent.eventKey(),
                                            "outboxId", outbox.id(),
                                            "repair", requeued == 1 ? "outbox_requeued" : "already_changed"
                                    ),
                                    false
                            );
                        }
                        continue;
                    }

                    jobsFound++;
                    resolveGap(orderId, expectedEvent.missingJobReason());
                    if ("sent".equals(job.status())) {
                        sent++;
                    } else if ("failed".equals(job.status()) || "manual_required".equals(job.status())) {
                        failed++;
                        recordGap(
                                runId,
                                orderId,
                                "TAX_JOB_MANUAL_REQUIRED",
                                Map.of(
                                        "jobId", job.id(),
                                        "jobStatus", job.status(),
                                        "eventKey", expectedEvent.eventKey()
                                ),
                                false
                        );
                    }
                }
            }

            int unresolved = countUnresolvedGaps();
            finishRun(
                    runId,
                    jobsFound,
                    sent,
                    failed,
                    unresolved,
                    "completed",
                    Map.of(
                            "scannedOrdersCount", scannedOrdersCount,
                            "scanTruncated", sourceOrdersCount > scannedOrdersCount,
                            "outboxRestored", outboxRestored,
                            "outboxRequeued", outboxRequeued,
                            "taxJobsMissing", taxJobsMissing
                    )
            );
            return new ReconcileResult(
                    runId,
                    effectiveDate,
                    sourceOrdersCount,
                    scannedOrdersCount,
                    jobsFound,
                    sent,
                    failed,
                    unresolved,
                    outboxRestored,
                    outboxRequeued,
                    taxJobsMissing,
                    sourceOrdersCount > scannedOrdersCount
            );
        } catch (RuntimeException exception) {
            safeFailRun(runId, exception);
            throw exception;
        }
    }

    private int countSourceOrders(LocalDate businessDate) {
        Integer count = dsl.selectCount()
                .from(ORDER)
                .where(ORDER.DATE.eq(businessDate))
                .and(IS_PAID.eq(true))
                .and(DSL.lower(PAYMENT_TYPE).in("cash", "transfer"))
                .fetchOne(0, Integer.class);
        return count != null ? count : 0;
    }

    private List<SourceOrder> fetchSourceOrders(LocalDate businessDate, int limit) {
        return dsl.select(ORDER.ORDERID, CANCELLED_AT)
                .from(ORDER)
                .where(ORDER.DATE.eq(businessDate))
                .and(IS_PAID.eq(true))
                .and(DSL.lower(PAYMENT_TYPE).in("cash", "transfer"))
                .orderBy(ORDER.ORDERID.asc())
                .limit(limit)
                .fetch(row -> new SourceOrder(
                        row.get(ORDER.ORDERID),
                        row.get(CANCELLED_AT) != null
                ));
    }

    private Map<Integer, OutboxState> fetchOutboxStates(List<Integer> orderIds) {
        if (orderIds.isEmpty()) return Map.of();
        var rows = dsl.select(OUTBOX_ID, OUTBOX_ORDER_ID, OUTBOX_EVENT_KEY, OUTBOX_STATUS)
                .from(TAX_OUTBOX)
                .where(OUTBOX_ORDER_ID.in(orderIds))
                .and(OUTBOX_EVENT_KEY.like("order:%:paid"))
                .fetch();
        Map<Integer, OutboxState> result = new LinkedHashMap<>();
        for (Record row : rows) {
            Integer orderId = row.get(OUTBOX_ORDER_ID);
            if (orderId == null) continue;
            result.put(orderId, new OutboxState(
                    row.get(OUTBOX_ID),
                    row.get(OUTBOX_EVENT_KEY),
                    normalizeStatus(row.get(OUTBOX_STATUS))
            ));
        }
        return result;
    }

    private Map<String, TaxJobState> fetchTaxJobs(List<String> eventKeys) {
        if (eventKeys.isEmpty()) return Map.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(eventKeys.size(), "?"));
        String sql = """
                select id, idempotency_key, status
                from tax.tax_receipt_job
                where idempotency_key in (%s)
                """.formatted(placeholders);
        Map<String, TaxJobState> result = new LinkedHashMap<>();
        taxJdbcTemplate.query(sql, rs -> {
            String key = rs.getString("idempotency_key");
            result.put(key, new TaxJobState(
                    rs.getLong("id"),
                    key,
                    normalizeStatus(rs.getString("status"))
            ));
        }, eventKeys.toArray());
        return result;
    }

    private OutboxState fetchOutboxState(String eventKey) {
        Record row = dsl.select(OUTBOX_ID, OUTBOX_EVENT_KEY, OUTBOX_STATUS)
                .from(TAX_OUTBOX)
                .where(OUTBOX_EVENT_KEY.eq(eventKey))
                .fetchOne();
        if (row == null) return null;
        return new OutboxState(
                row.get(OUTBOX_ID),
                row.get(OUTBOX_EVENT_KEY),
                normalizeStatus(row.get(OUTBOX_STATUS))
        );
    }

    private TaxJobState fetchTaxJob(String eventKey) {
        List<TaxJobState> rows = taxJdbcTemplate.query("""
                select id, idempotency_key, status
                from tax.tax_receipt_job
                where idempotency_key = ?
                """, (rs, rowNum) -> new TaxJobState(
                rs.getLong("id"),
                rs.getString("idempotency_key"),
                normalizeStatus(rs.getString("status"))
        ), eventKey);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private int requeueProcessedOutbox(long outboxId) {
        return dsl.execute("""
                update sales.tax_outbox
                set status = 'pending',
                    available_at = now(),
                    locked_at = null,
                    processed_at = null,
                    last_error = null,
                    updated_at = now()
                where id = ? and status = 'processed'
                """, outboxId);
    }

    private long startRun(LocalDate businessDate, int sourceOrdersCount, int scannedCount, int limit) {
        Long id = taxJdbcTemplate.queryForObject("""
                insert into tax.tax_reconcile_run (
                    business_date, source_orders_count, status, details_json
                ) values (?, ?, 'running', cast(? as jsonb))
                returning id
                """, Long.class, businessDate, sourceOrdersCount, toJson(Map.of(
                "scannedOrdersCount", scannedCount,
                "limit", limit
        )));
        if (id == null) throw new IllegalStateException("Не удалось создать запуск сверки чеков");
        return id;
    }

    private void finishRun(
            long runId,
            int jobsFound,
            int sent,
            int failed,
            int missing,
            String status,
            Map<String, Object> details
    ) {
        taxJdbcTemplate.update("""
                update tax.tax_reconcile_run
                set finished_at = now(),
                    jobs_created_count = ?,
                    sent_count = ?,
                    failed_count = ?,
                    missing_count = ?,
                    status = ?,
                    details_json = cast(? as jsonb)
                where id = ?
                """, jobsFound, sent, failed, missing, status, toJson(details), runId);
    }

    private void safeFailRun(long runId, RuntimeException exception) {
        try {
            finishRun(
                    runId,
                    0,
                    0,
                    1,
                    0,
                    "failed",
                    Map.of("errorType", exception.getClass().getSimpleName())
            );
        } catch (RuntimeException ignored) {
            // The tax database itself may be unavailable; the worker will retry on its next schedule.
        }
    }

    private void recordGap(long runId, int orderId, String reason, Map<String, Object> snapshot, boolean resolved) {
        taxJdbcTemplate.update("""
                insert into tax.tax_reconcile_gap (
                    reconcile_run_id, order_id, reason, snapshot_json, resolved, resolved_at
                )
                select ?, ?, ?, cast(? as jsonb), ?, case when ? then now() else null end
                where not exists (
                    select 1
                    from tax.tax_reconcile_gap
                    where order_id = ? and reason = ? and resolved = false
                )
                """, runId, orderId, reason, toJson(snapshot), resolved, resolved, orderId, reason);
    }

    private void resolveGap(int orderId, String reason) {
        taxJdbcTemplate.update("""
                update tax.tax_reconcile_gap
                set resolved = true, resolved_at = now()
                where order_id = ? and reason = ? and resolved = false
                """, orderId, reason);
    }

    private int countUnresolvedGaps() {
        Integer count = taxJdbcTemplate.queryForObject("""
                select count(*)
                from tax.tax_reconcile_gap
                where resolved = false
                """, Integer.class);
        return count != null ? count : 0;
    }

    private String normalizeStatus(String status) {
        return status == null ? "unknown" : status.trim().toLowerCase(Locale.ROOT);
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Не удалось сериализовать результат сверки", exception);
        }
    }

    private record OutboxState(long id, String eventKey, String status) {
    }

    private record TaxJobState(long id, String eventKey, String status) {
    }

    private record ExpectedEvent(String eventKey, String missingOutboxReason, String missingJobReason) {
    }

    private record SourceOrder(int orderId, boolean cancelled) {
    }

    public record ReconcileResult(
            long runId,
            LocalDate businessDate,
            int sourceOrdersCount,
            int scannedOrdersCount,
            int jobsFound,
            int sent,
            int failed,
            int unresolvedGaps,
            int outboxRestored,
            int outboxRequeued,
            int taxJobsMissing,
            boolean scanTruncated
    ) {
    }
}
