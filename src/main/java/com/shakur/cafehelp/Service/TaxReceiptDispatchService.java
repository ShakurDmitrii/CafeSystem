package com.shakur.cafehelp.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shakur.cafehelp.config.TaxDispatchProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class TaxReceiptDispatchService {
    private static final Logger log = LoggerFactory.getLogger(TaxReceiptDispatchService.class);

    private final JdbcTemplate taxJdbcTemplate;
    private final ObjectMapper objectMapper;
    private final TaxDispatchProperties dispatchProperties;
    private final TaxPartnerDispatchClient partnerDispatchClient;

    public TaxReceiptDispatchService(
            @Qualifier("taxJdbcTemplate") JdbcTemplate taxJdbcTemplate,
            ObjectMapper objectMapper,
            TaxDispatchProperties dispatchProperties,
            TaxPartnerDispatchClient partnerDispatchClient
    ) {
        this.taxJdbcTemplate = taxJdbcTemplate;
        this.objectMapper = objectMapper;
        this.dispatchProperties = dispatchProperties;
        this.partnerDispatchClient = partnerDispatchClient;
    }

    public DispatchResult dispatchPending(Integer limit) {
        int effectiveLimit = limit != null && limit > 0 ? limit : 100;
        DispatchAvailability availability = resolveDispatchAvailability();
        if (!availability.ready()) {
            return new DispatchResult(0, 0, 0, 0, List.of(), List.of(), availability.message());
        }

        recoverStaleProcessing();
        List<ClaimedJob> claimed = claimPending(effectiveLimit);
        return processClaimedJobs(claimed, availability.mode());
    }

    public DispatchResult dispatchPendingByOrder(int orderId, Integer limit) {
        int effectiveLimit = limit != null && limit > 0 ? limit : 1;
        DispatchAvailability availability = resolveDispatchAvailability();
        if (!availability.ready()) {
            return new DispatchResult(0, 0, 0, 0, List.of(), List.of(), availability.message());
        }

        recoverStaleProcessing();
        List<ClaimedJob> claimed = claimPendingByOrder(orderId, effectiveLimit);
        return processClaimedJobs(claimed, availability.mode());
    }

    private DispatchResult processClaimedJobs(List<ClaimedJob> claimed, String mode) {
        int sent = 0;
        int failed = 0;
        int manualRequired = 0;
        List<Long> sentJobIds = new ArrayList<>();
        List<Long> failedJobIds = new ArrayList<>();

        for (ClaimedJob job : claimed) {
            int attemptNo = (job.attemptCount() != null ? job.attemptCount() : 0) + 1;
            int maxAttempts = job.maxAttempts() != null && job.maxAttempts() > 0 ? job.maxAttempts() : 10;
            Instant started = Instant.now();
            String requestJson = buildAttemptRequestJson(job);

            try {
                ProviderSendResult sendResult = sendToProvider(job, mode);
                String providerPayload = buildProviderPayload(mode, sendResult);
                markSent(job.id(), attemptNo, sendResult.providerReceiptId(), sendResult.providerReceiptUrl(), providerPayload);
                safeInsertAttempt(
                        job.id(),
                        attemptNo,
                        started,
                        sendResult.httpStatus(),
                        requestJson,
                        sendResult.responseJson(),
                        null,
                        null,
                        false
                );
                sent++;
                sentJobIds.add(job.id());
            } catch (Exception e) {
                Integer httpStatus = resolveHttpStatus(e);
                boolean retryable = isRetryableError(e, httpStatus, attemptNo, maxAttempts);
                String nextStatus = retryable ? "pending" : "manual_required";
                String errorCode = resolveErrorCode(httpStatus, e);
                String errorMessage = shortMessage(e);
                markFailed(job.id(), attemptNo, nextStatus, errorCode, errorMessage, retryable);
                safeInsertAttempt(
                        job.id(),
                        attemptNo,
                        started,
                        httpStatus,
                        requestJson,
                        toJson(Map.of("error", errorMessage, "status", nextStatus, "mode", mode)),
                        errorCode,
                        errorMessage,
                        retryable
                );
                failed++;
                failedJobIds.add(job.id());
                if (!retryable) {
                    manualRequired++;
                }
            }
        }

        return new DispatchResult(claimed.size(), sent, failed, manualRequired, sentJobIds, failedJobIds, null);
    }

    public int recoverStaleProcessing() {
        return taxJdbcTemplate.update("""
                update tax.tax_receipt_job
                set status = 'pending',
                    next_attempt_at = now(),
                    processing_started_at = null,
                    updated_at = now(),
                    last_error_code = 'STALE_PROCESSING_RECOVERED',
                    last_error_message = 'Автоматически восстановлено после истечения processing-lock'
                where status = 'processing'
                  and (processing_started_at is null or processing_started_at < now() - interval '15 minutes')
                """);
    }

    private DispatchAvailability resolveDispatchAvailability() {
        String mode = dispatchProperties.normalizedMode();
        if ("SAFE".equals(mode)) {
            return new DispatchAvailability(mode, false, "Режим SAFE: внешняя отправка чеков отключена");
        }
        if ("PARTNER".equals(mode)) {
            if (!dispatchProperties.isPartnerConfigured()) {
                return new DispatchAvailability(
                        mode,
                        false,
                        "Режим PARTNER: не заданы TAX_PARTNER_BASE_URL и/или TAX_PARTNER_API_KEY"
                );
            }
            return new DispatchAvailability(mode, true, "OK");
        }
        return new DispatchAvailability(mode, false, "Неизвестный TAX_PROVIDER=" + mode + ". Допустимые значения: SAFE, PARTNER");
    }

    private List<ClaimedJob> claimPending(int limit) {
        String sql = """
                with picked as (
                    select candidate.id
                    from tax.tax_receipt_job candidate
                    where candidate.status = 'pending'
                      and candidate.next_attempt_at <= now()
                      and (
                          candidate.operation_type = 'sale'
                          or exists (
                              select 1
                              from tax.tax_receipt_job original
                              where original.idempotency_key = candidate.original_idempotency_key
                                and original.status = 'sent'
                          )
                      )
                    order by candidate.id
                    limit ?
                    for update skip locked
                )
                update tax.tax_receipt_job j
                set status = 'processing',
                    processing_started_at = now(),
                    updated_at = now()
                from picked
                where j.id = picked.id
                returning j.id,
                          j.order_id,
                          j.business_date,
                          j.amount,
                          j.payment_type,
                          j.operation_type,
                          j.original_idempotency_key,
                          (select original.provider_receipt_id
                           from tax.tax_receipt_job original
                           where original.idempotency_key = j.original_idempotency_key) as original_provider_receipt_id,
                          (select original.provider_receipt_url
                           from tax.tax_receipt_job original
                           where original.idempotency_key = j.original_idempotency_key) as original_provider_receipt_url,
                          j.attempt_count,
                          j.max_attempts,
                          j.idempotency_key,
                          j.payload_json::text as payload_json
                """;
        return taxJdbcTemplate.query(sql, (rs, rowNum) -> new ClaimedJob(
                rs.getLong("id"),
                rs.getInt("order_id"),
                rs.getObject("business_date", LocalDate.class),
                rs.getBigDecimal("amount"),
                rs.getString("payment_type"),
                rs.getString("operation_type"),
                rs.getString("original_idempotency_key"),
                rs.getString("original_provider_receipt_id"),
                rs.getString("original_provider_receipt_url"),
                rs.getObject("attempt_count", Integer.class),
                rs.getObject("max_attempts", Integer.class),
                rs.getString("idempotency_key"),
                rs.getString("payload_json")
        ), limit);
    }

    private List<ClaimedJob> claimPendingByOrder(int orderId, int limit) {
        String sql = """
                with picked as (
                    select candidate.id
                    from tax.tax_receipt_job candidate
                    where candidate.status = 'pending'
                      and candidate.next_attempt_at <= now()
                      and candidate.order_id = ?
                      and (
                          candidate.operation_type = 'sale'
                          or exists (
                              select 1
                              from tax.tax_receipt_job original
                              where original.idempotency_key = candidate.original_idempotency_key
                                and original.status = 'sent'
                          )
                      )
                    order by candidate.id
                    limit ?
                    for update skip locked
                )
                update tax.tax_receipt_job j
                set status = 'processing',
                    processing_started_at = now(),
                    updated_at = now()
                from picked
                where j.id = picked.id
                returning j.id,
                          j.order_id,
                          j.business_date,
                          j.amount,
                          j.payment_type,
                          j.operation_type,
                          j.original_idempotency_key,
                          (select original.provider_receipt_id
                           from tax.tax_receipt_job original
                           where original.idempotency_key = j.original_idempotency_key) as original_provider_receipt_id,
                          (select original.provider_receipt_url
                           from tax.tax_receipt_job original
                           where original.idempotency_key = j.original_idempotency_key) as original_provider_receipt_url,
                          j.attempt_count,
                          j.max_attempts,
                          j.idempotency_key,
                          j.payload_json::text as payload_json
                """;
        return taxJdbcTemplate.query(sql, (rs, rowNum) -> new ClaimedJob(
                rs.getLong("id"),
                rs.getInt("order_id"),
                rs.getObject("business_date", LocalDate.class),
                rs.getBigDecimal("amount"),
                rs.getString("payment_type"),
                rs.getString("operation_type"),
                rs.getString("original_idempotency_key"),
                rs.getString("original_provider_receipt_id"),
                rs.getString("original_provider_receipt_url"),
                rs.getObject("attempt_count", Integer.class),
                rs.getObject("max_attempts", Integer.class),
                rs.getString("idempotency_key"),
                rs.getString("payload_json")
        ), orderId, limit);
    }

    private ProviderSendResult sendToProvider(ClaimedJob job, String mode) throws Exception {
        if ("PARTNER".equals(mode)) {
            return sendToPartner(job);
        }
        throw new IllegalStateException("Отправка не поддерживается для режима " + mode);
    }

    private ProviderSendResult sendToPartner(ClaimedJob job) throws Exception {
        JsonNode payload = objectMapper.readTree(job.payloadJson() != null ? job.payloadJson() : "{}");
        List<Map<String, Object>> items = normalizeItems(payload);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("В payload заказа нет позиций для отправки");
        }

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("sourceSystem", isBlank(dispatchProperties.getSourceSystem()) ? "cafehelp" : dispatchProperties.getSourceSystem());
        request.put("externalOrderId", String.valueOf(job.orderId()));
        request.put("externalJobId", String.valueOf(job.id()));
        request.put("idempotencyKey", job.idempotencyKey());
        request.put("businessDate", resolveBusinessDate(job, payload));
        request.put("amount", round2(job.amount() != null ? job.amount().doubleValue() : 0.0));
        request.put("paymentType", job.paymentType());
        request.put("operationType", job.operationType());
        if ("refund".equals(job.operationType())) {
            request.put("originalIdempotencyKey", job.originalIdempotencyKey());
            request.put("originalReceiptId", job.originalProviderReceiptId());
            request.put("originalReceiptUrl", job.originalProviderReceiptUrl());
            request.put("correctionReason", safeText(payload, "correctionReason"));
        }
        request.put("customerPhone", safeText(payload, "deliveryPhone"));
        request.put("items", items);
        request.put("metadata", safeJson(job.payloadJson()));

        TaxPartnerDispatchClient.PartnerResponse response = partnerDispatchClient.sendReceipt(dispatchProperties, request);
        if (!response.success()) {
            throw new DispatchException(
                    "Partner API error: HTTP " + response.httpStatus() + ". Body: " + truncate(response.responseBody(), 500),
                    response.httpStatus()
            );
        }
        if (isBlank(response.receiptId()) && isBlank(response.receiptUrl())) {
            throw new DispatchException(
                    "Partner API returned success without receipt identifier or URL",
                    response.httpStatus()
            );
        }
        return new ProviderSendResult(
                response.httpStatus(),
                response.receiptId(),
                response.receiptUrl(),
                normalizeJsonText(response.responseBody())
        );
    }

    private List<Map<String, Object>> normalizeItems(JsonNode payload) {
        JsonNode itemsNode = payload.path("items");
        if (!itemsNode.isArray() || itemsNode.isEmpty()) return List.of();

        List<Map<String, Object>> items = new ArrayList<>();
        for (JsonNode item : itemsNode) {
            String name = item.path("name").asText("Позиция");
            int qty = normalizeQty(item.path("quantity").isMissingNode() ? item.path("qty").asInt(1) : item.path("quantity").asInt(1));
            double unitPrice = resolveUnitPrice(item, qty);
            double sum = round2(unitPrice * qty);

            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("name", name);
            normalized.put("quantity", qty);
            normalized.put("unitPrice", unitPrice);
            normalized.put("sum", sum);
            items.add(normalized);
        }
        return items;
    }

    private String resolveBusinessDate(ClaimedJob job, JsonNode payload) {
        String fromPayload = safeText(payload, "orderDate");
        if (!isBlank(fromPayload)) {
            return fromPayload;
        }
        if (job.businessDate() != null) {
            return job.businessDate().toString();
        }
        return LocalDate.now().toString();
    }

    private void markSent(long jobId, int attemptNo, String providerReceiptId, String providerReceiptUrl, String providerPayload) {
        String sql = """
                update tax.tax_receipt_job
                set status = 'sent',
                    attempt_count = ?,
                    processing_started_at = null,
                    provider_receipt_id = ?,
                    provider_receipt_url = ?,
                    provider_payload = cast(? as jsonb),
                    sent_at = now(),
                    last_error_code = null,
                    last_error_message = null,
                    updated_at = now()
                where id = ?
                """;
        taxJdbcTemplate.update(
                sql,
                attemptNo,
                providerReceiptId,
                providerReceiptUrl,
                providerPayload,
                jobId
        );
    }

    private void markFailed(
            long jobId,
            int attemptNo,
            String status,
            String errorCode,
            String errorMessage,
            boolean retryable
    ) {
        LocalDateTime nextAttemptAt = retryable
                ? LocalDateTime.now().plusMinutes(Math.min(60, Math.max(1, attemptNo * 2)))
                : LocalDateTime.now();

        String sql = """
                update tax.tax_receipt_job
                set status = ?,
                    attempt_count = ?,
                    next_attempt_at = ?,
                    processing_started_at = null,
                    last_error_code = ?,
                    last_error_message = ?,
                    updated_at = now()
                where id = ?
                """;
        taxJdbcTemplate.update(
                sql,
                status,
                attemptNo,
                Timestamp.valueOf(nextAttemptAt),
                errorCode,
                errorMessage,
                jobId
        );
    }

    private void insertAttempt(
            long jobId,
            int attemptNo,
            Instant started,
            Integer httpStatus,
            String requestJson,
            String responseJson,
            String errorCode,
            String errorMessage,
            boolean retryable
    ) {
        Instant finished = Instant.now();
        int durationMs = (int) Duration.between(started, finished).toMillis();
        String sql = """
                insert into tax.tax_receipt_attempt (
                    job_id,
                    attempt_no,
                    started_at,
                    finished_at,
                    duration_ms,
                    http_status,
                    request_json,
                    response_json,
                    error_code,
                    error_message,
                    retryable,
                    created_at
                ) values (
                    ?, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), ?, ?, ?, now()
                )
                """;
        taxJdbcTemplate.update(
                sql,
                jobId,
                attemptNo,
                Timestamp.from(started),
                Timestamp.from(finished),
                durationMs,
                httpStatus,
                requestJson != null ? requestJson : "{}",
                responseJson != null ? responseJson : "{}",
                errorCode,
                errorMessage,
                retryable
        );
    }

    private void safeInsertAttempt(
            long jobId,
            int attemptNo,
            Instant started,
            Integer httpStatus,
            String requestJson,
            String responseJson,
            String errorCode,
            String errorMessage,
            boolean retryable
    ) {
        try {
            insertAttempt(
                    jobId,
                    attemptNo,
                    started,
                    httpStatus,
                    requestJson,
                    responseJson,
                    errorCode,
                    errorMessage,
                    retryable
            );
        } catch (Exception exception) {
            log.error(
                    "Failed to persist tax receipt attempt metadata: jobId={}, attemptNo={}, errorType={}",
                    jobId,
                    attemptNo,
                    exception.getClass().getSimpleName()
            );
        }
    }

    private String buildAttemptRequestJson(ClaimedJob job) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("jobId", job.id());
        map.put("orderId", job.orderId());
        map.put("businessDate", job.businessDate());
        map.put("amount", job.amount());
        map.put("paymentType", job.paymentType());
        map.put("operationType", job.operationType());
        map.put("originalIdempotencyKey", job.originalIdempotencyKey());
        map.put("dispatchMode", dispatchProperties.normalizedMode());
        map.put("payload", safeJson(job.payloadJson()));
        return toJson(map);
    }

    private String buildProviderPayload(String mode, ProviderSendResult sendResult) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mode", mode);
        payload.put("httpStatus", sendResult.httpStatus());
        payload.put("providerReceiptId", sendResult.providerReceiptId());
        payload.put("providerReceiptUrl", sendResult.providerReceiptUrl());
        payload.put("response", safeJson(sendResult.responseJson()));
        return toJson(payload);
    }

    private int normalizeQty(int qty) {
        return qty > 0 ? qty : 1;
    }

    private double resolveUnitPrice(JsonNode item, int qty) {
        double price = item.path("price").isMissingNode() ? -1 : item.path("price").asDouble(-1);
        if (price >= 0) {
            return round2(price);
        }
        double sum = item.path("sum").isMissingNode() ? -1 : item.path("sum").asDouble(-1);
        if (sum >= 0 && qty > 0) {
            return round2(sum / qty);
        }
        return 0.0;
    }

    private double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private boolean isRetryableError(Exception e, Integer httpStatus, int attemptNo, int maxAttempts) {
        if (attemptNo >= maxAttempts) {
            return false;
        }

        if (httpStatus != null) {
            if (httpStatus == 401 || httpStatus == 403) {
                return false;
            }
            if (httpStatus >= 400 && httpStatus < 500 && httpStatus != 429 && httpStatus != 408) {
                return false;
            }
        }

        String message = (e.getMessage() != null ? e.getMessage() : "").toLowerCase(Locale.ROOT);
        if (message.contains("authentication error")
                || message.contains("неверн")
                || message.contains("парол")
                || message.contains("unauthorized")
                || message.contains("forbidden")) {
            return false;
        }
        return true;
    }

    private String resolveErrorCode(Integer httpStatus, Exception e) {
        if (httpStatus != null) {
            return "HTTP_" + httpStatus;
        }
        return "SEND_ERROR";
    }

    private Integer resolveHttpStatus(Exception e) {
        if (e instanceof DispatchException dispatchException) {
            return dispatchException.httpStatus();
        }
        return null;
    }

    private Object safeJson(String source) {
        try {
            return source != null ? objectMapper.readTree(source) : objectMapper.createObjectNode();
        } catch (Exception e) {
            return source;
        }
    }

    private String toJson(Object data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String normalizeJsonText(String rawResponse) {
        try {
            JsonNode node = objectMapper.readTree(rawResponse);
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return toJson(Map.of("raw", rawResponse != null ? rawResponse : ""));
        }
    }

    private String safeText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return isBlank(text) ? null : text;
    }

    private String truncate(String value, int maxLen) {
        if (value == null) return "";
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }

    private String shortMessage(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage() : "unknown error";
        return msg.substring(0, Math.min(msg.length(), 2000));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record ClaimedJob(
            long id,
            int orderId,
            LocalDate businessDate,
            BigDecimal amount,
            String paymentType,
            String operationType,
            String originalIdempotencyKey,
            String originalProviderReceiptId,
            String originalProviderReceiptUrl,
            Integer attemptCount,
            Integer maxAttempts,
            String idempotencyKey,
            String payloadJson
    ) {
    }

    private record DispatchAvailability(
            String mode,
            boolean ready,
            String message
    ) {
    }

    private record ProviderSendResult(
            Integer httpStatus,
            String providerReceiptId,
            String providerReceiptUrl,
            String responseJson
    ) {
    }

    private static class DispatchException extends RuntimeException {
        private final Integer httpStatus;

        private DispatchException(String message, Integer httpStatus) {
            super(message);
            this.httpStatus = httpStatus;
        }

        private Integer httpStatus() {
            return httpStatus;
        }
    }

    public record DispatchResult(
            int claimed,
            int sent,
            int failed,
            int manualRequired,
            List<Long> sentJobIds,
            List<Long> failedJobIds,
            String message
    ) {
    }
}
