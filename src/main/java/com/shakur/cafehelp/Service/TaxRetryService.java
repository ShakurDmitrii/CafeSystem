package com.shakur.cafehelp.Service;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaxRetryService {

    private final DSLContext dsl;
    private final JdbcTemplate taxJdbcTemplate;

    public TaxRetryService(DSLContext dsl, @Qualifier("taxJdbcTemplate") JdbcTemplate taxJdbcTemplate) {
        this.dsl = dsl;
        this.taxJdbcTemplate = taxJdbcTemplate;
    }

    public RetryResult retryFailedAndDeadLetter(Integer outboxLimit, Integer jobsLimit) {
        int outboxEffectiveLimit = outboxLimit != null && outboxLimit > 0 ? outboxLimit : 500;
        int jobsEffectiveLimit = jobsLimit != null && jobsLimit > 0 ? jobsLimit : 500;

        int outboxReset = retryOutbox(outboxEffectiveLimit);
        int jobsReset = retryTaxJobs(jobsEffectiveLimit);

        return new RetryResult(outboxReset, jobsReset);
    }

    private int retryOutbox(int limit) {
        List<Record> rows = dsl.fetch("""
                with picked as (
                    select id
                    from sales.tax_outbox
                    where status in ('failed', 'dead_letter')
                    order by id
                    limit ?
                    for update skip locked
                )
                update sales.tax_outbox o
                set status = 'pending',
                    available_at = now(),
                    locked_at = null,
                    updated_at = now(),
                    last_error = null
                from picked
                where o.id = picked.id
                returning o.id
                """, limit);
        return rows.size();
    }

    private int retryTaxJobs(int limit) {
        String sql = """
                with picked as (
                    select id
                    from tax.tax_receipt_job
                    where status in ('failed', 'manual_required')
                    order by id
                    limit ?
                )
                update tax.tax_receipt_job j
                set status = 'pending',
                    next_attempt_at = now(),
                    updated_at = now(),
                    last_error_code = null,
                    last_error_message = null
                from picked
                where j.id = picked.id
                returning j.id
                """;

        List<Long> updatedIds = taxJdbcTemplate.query(
                sql,
                (rs, rowNum) -> rs.getLong(1),
                limit
        );
        return updatedIds.size();
    }

    public record RetryResult(
            int outboxReset,
            int jobsReset
    ) {
    }
}
