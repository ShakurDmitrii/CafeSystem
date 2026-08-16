create index if not exists tax_receipt_job_processing_lock_idx
    on tax.tax_receipt_job (processing_started_at)
    where status = 'processing';

comment on column tax.tax_receipt_job.processing_started_at is
    'Claim timestamp. Stale processing jobs are automatically returned to pending for idempotent retry.';
