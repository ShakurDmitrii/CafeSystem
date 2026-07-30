-- Product units normalization
-- Run once in PostgreSQL (DBeaver/psql):
-- \i db/migrations/2026-02-23_product_units.sql

alter table sales.product
    add column if not exists unit varchar(16),
    add column if not exists base_unit varchar(16),
    add column if not exists unit_factor numeric(18,6);

update sales.product
set
    unit = coalesce(unit, 'g'),
    base_unit = coalesce(base_unit, 'g'),
    unit_factor = coalesce(unit_factor, 1);

alter table sales.product
    alter column unit set not null,
    alter column base_unit set not null,
    alter column unit_factor set not null;

do $$
begin
    if not exists (
        select 1
        from information_schema.table_constraints
        where constraint_schema = 'sales'
          and table_name = 'product'
          and constraint_name = 'product_unit_factor_positive_chk'
    ) then
        alter table sales.product
            add constraint product_unit_factor_positive_chk
            check (unit_factor > 0);
    end if;
end $$;
