package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.config.TaxDispatchProperties;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static jooqdata.tables.Order.ORDER;

@Service
public class TaxAdminQueryService {

    private static final Field<Boolean> IS_PAID_FIELD = DSL.field(DSL.name("is_paid"), Boolean.class);
    private static final Field<String> PAYMENT_TYPE_FIELD = DSL.field(DSL.name("payment_type"), String.class);
    private static final Field<Boolean> ORDER_TYPE_FIELD = DSL.field(DSL.name("type"), Boolean.class);
    private static final Field<String> DELIVERY_PHONE_FIELD = DSL.field(DSL.name("delivery_phone"), String.class);
    private static final Field<String> DELIVERY_ADDRESS_FIELD = DSL.field(DSL.name("delivery_address"), String.class);
    private static final Field<LocalDateTime> CANCELLED_AT_FIELD = DSL.field(DSL.name("cancelled_at"), LocalDateTime.class);
    private static final org.jooq.Table<?> TAX_OUTBOX = DSL.table(DSL.name("sales", "tax_outbox"));
    private static final Field<Integer> OUTBOX_AGGREGATE_ID = DSL.field(DSL.name("aggregate_id"), Integer.class);
    private static final Field<String> OUTBOX_STATUS = DSL.field(DSL.name("status"), String.class);

    private final DSLContext dsl;
    private final JdbcTemplate taxJdbcTemplate;
    private final TaxDispatchProperties dispatchProperties;

    public TaxAdminQueryService(
            DSLContext dsl,
            @Qualifier("taxJdbcTemplate") JdbcTemplate taxJdbcTemplate,
            TaxDispatchProperties dispatchProperties
    ) {
        this.dsl = dsl;
        this.taxJdbcTemplate = taxJdbcTemplate;
        this.dispatchProperties = dispatchProperties;
    }

    public Map<String, Object> getOverview(Integer limit) {
        int effectiveLimit = limit != null && limit > 0 ? limit : 50;

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("paidOrdersCount", fetchPaidOrdersCount());
        overview.put("outboxStatusCounts", fetchOutboxStatusCounts());
        overview.put("jobStatusCounts", fetchJobStatusCounts());
        overview.put("recentOutbox", fetchRecentOutbox(effectiveLimit));
        overview.put("recentJobs", fetchRecentJobs(effectiveLimit));
        Map<String, Object> integrationStatus = getDispatchIntegrationStatus();
        overview.put("dispatchIntegration", integrationStatus);
        overview.put("myTaxIntegration", integrationStatus);
        return overview;
    }

    private Map<String, Object> getDispatchIntegrationStatus() {
        String mode = dispatchProperties.normalizedMode();
        boolean configured;
        boolean ready;
        String message;
        String provider;

        if ("SAFE".equals(mode)) {
            configured = true;
            ready = false;
            provider = "SAFE";
            message = "Безопасный режим: внешняя отправка чеков отключена";
        } else if ("PARTNER".equals(mode)) {
            configured = dispatchProperties.isPartnerConfigured();
            ready = configured;
            provider = dispatchProperties.getPartner().getProviderName();
            if (configured) {
                message = "Партнерская интеграция настроена и готова к отправке";
            } else {
                message = "Режим PARTNER включен, но не заданы TAX_PARTNER_BASE_URL и/или TAX_PARTNER_API_KEY";
            }
        } else {
            configured = false;
            ready = false;
            provider = mode;
            message = "Неизвестный TAX_PROVIDER=" + mode + ". Допустимые значения: SAFE, PARTNER";
        }

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("mode", mode);
        status.put("provider", provider);
        status.put("configured", configured);
        status.put("ready", ready);
        status.put("message", message);
        return status;
    }

    public Map<String, Object> getReceiptDetails(int orderId) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("order", fetchOrderInfo(orderId));
        details.put("outbox", fetchOutboxByOrder(orderId, 20));
        details.put("jobs", fetchJobsByOrder(orderId, 20));
        return details;
    }

    private int fetchPaidOrdersCount() {
        Integer count = dsl.selectCount()
                .from(ORDER)
                .where(IS_PAID_FIELD.eq(true))
                .and(CANCELLED_AT_FIELD.isNull())
                .and(PAYMENT_TYPE_FIELD.in("cash", "transfer"))
                .fetchOne(0, Integer.class);
        return count != null ? count : 0;
    }

    private Map<String, Integer> fetchOutboxStatusCounts() {
        Map<String, Integer> result = new LinkedHashMap<>();
        var rows = dsl.select(OUTBOX_STATUS, DSL.count())
                .from(TAX_OUTBOX)
                .groupBy(OUTBOX_STATUS)
                .fetch();
        for (var row : rows) {
            String status = row.get(OUTBOX_STATUS);
            Integer cnt = row.get(1, Integer.class);
            result.put(status != null ? status : "unknown", cnt != null ? cnt : 0);
        }
        return result;
    }

    private Map<String, Integer> fetchJobStatusCounts() {
        List<Map<String, Object>> rows = taxJdbcTemplate.queryForList(
                "select status, count(*) as cnt from tax.tax_receipt_job group by status"
        );
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String status = row.get("status") != null ? String.valueOf(row.get("status")) : "unknown";
            int count = toInt(row.get("cnt"));
            result.put(status, count);
        }
        return result;
    }

    private Map<String, Object> fetchOrderInfo(int orderId) {
        Record row = dsl.select(
                        ORDER.ORDERID,
                        ORDER.DATE,
                        ORDER.CREATED_AT,
                        ORDER.AMOUNT,
                        ORDER.SHIFTID,
                        ORDER_TYPE_FIELD,
                        PAYMENT_TYPE_FIELD,
                        IS_PAID_FIELD,
                        DELIVERY_PHONE_FIELD,
                        DELIVERY_ADDRESS_FIELD
                )
                .from(ORDER)
                .where(ORDER.ORDERID.eq(orderId))
                .fetchOne();

        if (row == null) {
            return null;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", row.get(ORDER.ORDERID));
        result.put("businessDate", row.get(ORDER.DATE));
        result.put("createdAt", row.get(ORDER.CREATED_AT));
        result.put("amount", row.get(ORDER.AMOUNT));
        result.put("shiftId", row.get(ORDER.SHIFTID));
        result.put("isDelivery", row.get(ORDER_TYPE_FIELD));
        result.put("paymentType", row.get(PAYMENT_TYPE_FIELD));
        result.put("isPaid", row.get(IS_PAID_FIELD));
        result.put("deliveryPhone", row.get(DELIVERY_PHONE_FIELD));
        result.put("deliveryAddress", row.get(DELIVERY_ADDRESS_FIELD));
        return result;
    }

    private List<Map<String, Object>> fetchRecentOutbox(int limit) {
        var rows = dsl.select(
                        DSL.field(DSL.name("id"), Long.class),
                        DSL.field(DSL.name("aggregate_id"), Integer.class),
                        DSL.field(DSL.name("event_type"), String.class),
                        DSL.field(DSL.name("event_key"), String.class),
                        DSL.field(DSL.name("status"), String.class),
                        DSL.field(DSL.name("attempt_count"), Integer.class),
                        DSL.field(DSL.name("last_error"), String.class),
                        DSL.field(DSL.name("created_at")),
                        DSL.field(DSL.name("updated_at"))
                )
                .from(TAX_OUTBOX)
                .orderBy(DSL.field(DSL.name("id")).desc())
                .limit(limit)
                .fetch();

        List<Map<String, Object>> result = new ArrayList<>();
        for (var row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.get(0));
            item.put("orderId", row.get(1));
            item.put("eventType", row.get(2));
            item.put("eventKey", row.get(3));
            item.put("status", row.get(4));
            item.put("attemptCount", row.get(5));
            item.put("lastError", row.get(6));
            item.put("createdAt", row.get(7));
            item.put("updatedAt", row.get(8));
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> fetchOutboxByOrder(int orderId, int limit) {
        var rows = dsl.select(
                        DSL.field(DSL.name("id"), Long.class),
                        DSL.field(DSL.name("aggregate_id"), Integer.class),
                        DSL.field(DSL.name("event_type"), String.class),
                        DSL.field(DSL.name("event_key"), String.class),
                        DSL.field(DSL.name("status"), String.class),
                        DSL.field(DSL.name("attempt_count"), Integer.class),
                        DSL.field(DSL.name("last_error"), String.class),
                        DSL.field(DSL.name("created_at")),
                        DSL.field(DSL.name("updated_at")),
                        DSL.field("payload_json::text", String.class)
                )
                .from(TAX_OUTBOX)
                .where(OUTBOX_AGGREGATE_ID.eq(orderId))
                .orderBy(DSL.field(DSL.name("id")).desc())
                .limit(limit)
                .fetch();

        List<Map<String, Object>> result = new ArrayList<>();
        for (var row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.get(0));
            item.put("orderId", row.get(1));
            item.put("eventType", row.get(2));
            item.put("eventKey", row.get(3));
            item.put("status", row.get(4));
            item.put("attemptCount", row.get(5));
            item.put("lastError", row.get(6));
            item.put("createdAt", row.get(7));
            item.put("updatedAt", row.get(8));
            item.put("payloadJson", row.get(9));
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> fetchRecentJobs(int limit) {
        String sql = """
                select id,
                       order_id as "orderId",
                       shift_id as "shiftId",
                       business_date as "businessDate",
                       amount,
                       payment_type as "paymentType",
                       status,
                       attempt_count as "attemptCount",
                       provider_receipt_id as "providerReceiptId",
                       last_error_code as "lastErrorCode",
                       last_error_message as "lastErrorMessage",
                       created_at as "createdAt",
                       updated_at as "updatedAt"
                from tax.tax_receipt_job
                order by id desc
                limit ?
                """;
        return taxJdbcTemplate.queryForList(sql, limit);
    }

    private List<Map<String, Object>> fetchJobsByOrder(int orderId, int limit) {
        String sql = """
                select id,
                       order_id as "orderId",
                       shift_id as "shiftId",
                       business_date as "businessDate",
                       amount,
                       payment_type as "paymentType",
                       customer_phone as "customerPhone",
                       status,
                       attempt_count as "attemptCount",
                       provider_receipt_id as "providerReceiptId",
                       last_error_code as "lastErrorCode",
                       last_error_message as "lastErrorMessage",
                       payload_json::text as "payloadJson",
                       created_at as "createdAt",
                       updated_at as "updatedAt"
                from tax.tax_receipt_job
                where order_id = ?
                order by id desc
                limit ?
                """;
        return taxJdbcTemplate.queryForList(sql, orderId, limit);
    }

    private int toInt(Object value) {
        if (value == null) return 0;
        if (value instanceof Integer i) return i;
        if (value instanceof Long l) return l.intValue();
        if (value instanceof BigDecimal bd) return bd.intValue();
        return Integer.parseInt(String.valueOf(value));
    }
}
