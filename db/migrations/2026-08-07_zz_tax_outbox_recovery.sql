create index if not exists tax_outbox_processing_lock_idx
    on sales.tax_outbox (locked_at)
    where status = 'processing';

comment on column sales.tax_outbox.locked_at is
    'Claim timestamp. processing rows older than the worker lock timeout are returned to pending and delivered idempotently.';
