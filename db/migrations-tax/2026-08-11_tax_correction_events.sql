alter table tax.tax_receipt_job
    add column if not exists operation_type varchar(20) not null default 'sale',
    add column if not exists original_idempotency_key varchar(140);

do $$
begin
    if not exists (
        select 1
        from information_schema.table_constraints
        where constraint_schema = 'tax'
          and table_name = 'tax_receipt_job'
          and constraint_name = 'tax_receipt_job_operation_type_chk'
    ) then
        alter table tax.tax_receipt_job
            add constraint tax_receipt_job_operation_type_chk
            check (operation_type in ('sale', 'refund'));
    end if;
end $$;

create index if not exists tax_receipt_job_original_key_idx
    on tax.tax_receipt_job (original_idempotency_key)
    where original_idempotency_key is not null;

comment on column tax.tax_receipt_job.operation_type is
    'Immutable fiscal operation: sale for the original receipt, refund for cancellation correction.';

comment on column tax.tax_receipt_job.original_idempotency_key is
    'For refund jobs, identifies the original sale job that must be sent first.';
