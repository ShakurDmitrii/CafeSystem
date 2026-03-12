-- Main warehouse flag
-- Run once in PostgreSQL (DBeaver/psql):
-- \i db/migrations/2026-03-10_main_warehouse.sql

alter table sales.warehouse
    add column if not exists is_main boolean not null default false;

-- If no main warehouse is set, pick the smallest id
do $$
begin
    if not exists (
        select 1 from sales.warehouse where is_main = true
    ) then
        update sales.warehouse
        set is_main = true
        where warehouseid = (
            select warehouseid from sales.warehouse order by warehouseid asc limit 1
        );
    end if;
end $$;
