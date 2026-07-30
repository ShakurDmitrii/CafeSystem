-- Reliable outbox in main (sales) database to transfer payment events
-- to dedicated tax database without dual-write from cashier transaction.

create table if not exists sales.tax_outbox (
    id bigint generated always as identity primary key,
    aggregate_type varchar(50) not null default 'order',
    aggregate_id integer not null,
    event_type varchar(60) not null,
    event_key varchar(140) not null,
    payload_json jsonb not null default '{}'::jsonb,
    status varchar(20) not null default 'pending',
    available_at timestamp not null default now(),
    locked_at timestamp,
    processed_at timestamp,
    attempt_count integer not null default 0,
    last_error text,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now()
);

create unique index if not exists tax_outbox_event_key_uq_idx
    on sales.tax_outbox (event_key);

create index if not exists tax_outbox_status_available_idx
    on sales.tax_outbox (status, available_at);

create index if not exists tax_outbox_aggregate_idx
    on sales.tax_outbox (aggregate_type, aggregate_id);

create index if not exists tax_outbox_created_idx
    on sales.tax_outbox (created_at);

do $$
begin
    if not exists (
        select 1
        from information_schema.table_constraints
        where constraint_schema = 'sales'
          and table_name = 'tax_outbox'
          and constraint_name = 'tax_outbox_status_chk'
    ) then
        alter table sales.tax_outbox
            add constraint tax_outbox_status_chk
            check (
                status in ('pending', 'processing', 'processed', 'failed', 'dead_letter')
            );
    end if;
end $$;

do $$
begin
    if not exists (
        select 1
        from information_schema.table_constraints
        where constraint_schema = 'sales'
          and table_name = 'tax_outbox'
          and constraint_name = 'tax_outbox_attempt_nonnegative_chk'
    ) then
        alter table sales.tax_outbox
            add constraint tax_outbox_attempt_nonnegative_chk
            check (attempt_count >= 0);
    end if;
end $$;
