-- Dedicated tax database schema for receipt processing and reconciliation.

create schema if not exists tax;

create table if not exists tax.tax_provider_account (
    id bigint generated always as identity primary key,
    provider varchar(50) not null,
    is_active boolean not null default false,
    login_hint varchar(255),
    secret_ref varchar(500),
    token_encrypted text,
    token_expires_at timestamp,
    settings_json jsonb not null default '{}'::jsonb,
    last_auth_at timestamp,
    last_auth_error text,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now()
);

create index if not exists tax_provider_account_provider_idx
    on tax.tax_provider_account (provider);

create unique index if not exists tax_provider_account_one_active_provider_uq_idx
    on tax.tax_provider_account (provider)
    where is_active = true;

create table if not exists tax.tax_receipt_job (
    id bigint generated always as identity primary key,
    source_system varchar(50) not null default 'cafehelp',
    source_event_id bigint,
    order_id integer not null,
    shift_id integer,
    business_date date,
    amount numeric(14, 2) not null,
    payment_type varchar(20) not null,
    customer_phone varchar(50),
    customer_email varchar(255),
    payload_json jsonb not null default '{}'::jsonb,
    status varchar(20) not null default 'pending',
    attempt_count integer not null default 0,
    max_attempts integer not null default 10,
    next_attempt_at timestamp not null default now(),
    processing_started_at timestamp,
    idempotency_key varchar(140) not null,
    provider_receipt_id varchar(255),
    provider_receipt_url text,
    provider_payload jsonb,
    last_error_code varchar(120),
    last_error_message text,
    sent_at timestamp,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now()
);

create unique index if not exists tax_receipt_job_idempotency_uq_idx
    on tax.tax_receipt_job (idempotency_key);

create index if not exists tax_receipt_job_status_next_attempt_idx
    on tax.tax_receipt_job (status, next_attempt_at);

create index if not exists tax_receipt_job_order_idx
    on tax.tax_receipt_job (order_id);

create index if not exists tax_receipt_job_shift_idx
    on tax.tax_receipt_job (shift_id);

create index if not exists tax_receipt_job_created_idx
    on tax.tax_receipt_job (created_at);

create table if not exists tax.tax_receipt_attempt (
    id bigint generated always as identity primary key,
    job_id bigint not null,
    attempt_no integer not null,
    started_at timestamp not null default now(),
    finished_at timestamp,
    duration_ms integer,
    http_status integer,
    request_json jsonb,
    response_json jsonb,
    error_code varchar(120),
    error_message text,
    retryable boolean not null default true,
    created_at timestamp not null default now()
);

create unique index if not exists tax_receipt_attempt_job_attempt_uq_idx
    on tax.tax_receipt_attempt (job_id, attempt_no);

create index if not exists tax_receipt_attempt_job_created_idx
    on tax.tax_receipt_attempt (job_id, created_at);

create table if not exists tax.tax_reconcile_run (
    id bigint generated always as identity primary key,
    business_date date not null,
    started_at timestamp not null default now(),
    finished_at timestamp,
    source_orders_count integer not null default 0,
    jobs_created_count integer not null default 0,
    sent_count integer not null default 0,
    failed_count integer not null default 0,
    missing_count integer not null default 0,
    status varchar(20) not null default 'running',
    details_json jsonb not null default '{}'::jsonb,
    created_at timestamp not null default now()
);

create index if not exists tax_reconcile_run_business_date_idx
    on tax.tax_reconcile_run (business_date, started_at desc);

create table if not exists tax.tax_reconcile_gap (
    id bigint generated always as identity primary key,
    reconcile_run_id bigint not null,
    order_id integer not null,
    reason varchar(80) not null,
    snapshot_json jsonb not null default '{}'::jsonb,
    resolved boolean not null default false,
    resolved_at timestamp,
    created_at timestamp not null default now()
);

create index if not exists tax_reconcile_gap_run_idx
    on tax.tax_reconcile_gap (reconcile_run_id);

create index if not exists tax_reconcile_gap_order_resolved_idx
    on tax.tax_reconcile_gap (order_id, resolved);

do $$
begin
    if not exists (
        select 1
        from information_schema.table_constraints
        where constraint_schema = 'tax'
          and table_name = 'tax_receipt_job'
          and constraint_name = 'tax_receipt_job_status_chk'
    ) then
        alter table tax.tax_receipt_job
            add constraint tax_receipt_job_status_chk
            check (status in ('pending', 'processing', 'sent', 'failed', 'manual_required', 'cancelled'));
    end if;
end $$;

do $$
begin
    if not exists (
        select 1
        from information_schema.table_constraints
        where constraint_schema = 'tax'
          and table_name = 'tax_receipt_job'
          and constraint_name = 'tax_receipt_job_amount_nonnegative_chk'
    ) then
        alter table tax.tax_receipt_job
            add constraint tax_receipt_job_amount_nonnegative_chk
            check (amount >= 0);
    end if;
end $$;

do $$
begin
    if not exists (
        select 1
        from information_schema.table_constraints
        where constraint_schema = 'tax'
          and table_name = 'tax_receipt_job'
          and constraint_name = 'tax_receipt_job_attempt_nonnegative_chk'
    ) then
        alter table tax.tax_receipt_job
            add constraint tax_receipt_job_attempt_nonnegative_chk
            check (attempt_count >= 0 and max_attempts > 0);
    end if;
end $$;

do $$
begin
    if not exists (
        select 1
        from information_schema.table_constraints
        where constraint_schema = 'tax'
          and table_name = 'tax_receipt_attempt'
          and constraint_name = 'tax_receipt_attempt_attempt_positive_chk'
    ) then
        alter table tax.tax_receipt_attempt
            add constraint tax_receipt_attempt_attempt_positive_chk
            check (attempt_no > 0);
    end if;
end $$;

do $$
begin
    if not exists (
        select 1
        from information_schema.table_constraints
        where constraint_schema = 'tax'
          and table_name = 'tax_reconcile_run'
          and constraint_name = 'tax_reconcile_run_status_chk'
    ) then
        alter table tax.tax_reconcile_run
            add constraint tax_reconcile_run_status_chk
            check (status in ('running', 'completed', 'failed'));
    end if;
end $$;

do $$
begin
    if not exists (
        select 1
        from information_schema.table_constraints
        where constraint_schema = 'tax'
          and table_name = 'tax_receipt_attempt'
          and constraint_name = 'tax_receipt_attempt_job_fk'
    ) then
        alter table tax.tax_receipt_attempt
            add constraint tax_receipt_attempt_job_fk
            foreign key (job_id)
            references tax.tax_receipt_job (id)
            on delete cascade;
    end if;
end $$;

do $$
begin
    if not exists (
        select 1
        from information_schema.table_constraints
        where constraint_schema = 'tax'
          and table_name = 'tax_reconcile_gap'
          and constraint_name = 'tax_reconcile_gap_run_fk'
    ) then
        alter table tax.tax_reconcile_gap
            add constraint tax_reconcile_gap_run_fk
            foreign key (reconcile_run_id)
            references tax.tax_reconcile_run (id)
            on delete cascade;
    end if;
end $$;
