-- Server-side catalog invariants. Constraints are NOT VALID so legacy rows can be
-- remediated separately while every new or changed row is protected immediately.

create unique index if not exists supplier_name_normalized_uq_idx
    on sales.supplier (lower(btrim(suppliername)));

create unique index if not exists dish_category_name_normalized_uq_idx
    on sales.dish_category (lower(btrim(name)));

do $$
begin
    if not exists (select 1 from pg_constraint where conname = 'supplier_name_nonblank_chk') then
        alter table sales.supplier
            add constraint supplier_name_nonblank_chk
            check (suppliername is not null and btrim(suppliername) <> '') not valid;
    end if;

    if not exists (select 1 from pg_constraint where conname = 'dish_category_name_nonblank_chk') then
        alter table sales.dish_category
            add constraint dish_category_name_nonblank_chk
            check (name is not null and btrim(name) <> '') not valid;
    end if;

    if not exists (select 1 from pg_constraint where conname = 'preparation_catalog_values_chk') then
        alter table sales.preparation
            add constraint preparation_catalog_values_chk
            check (
                preparationname is not null
                and btrim(preparationname) <> ''
                and output_weight is not null
                and output_weight > 0
                and output_weight not in ('NaN'::double precision, 'Infinity'::double precision, '-Infinity'::double precision)
            ) not valid;
    end if;

    if not exists (select 1 from pg_constraint where conname = 'product_catalog_values_chk') then
        alter table sales.product
            add constraint product_catalog_values_chk
            check (
                productname is not null
                and btrim(productname) <> ''
                and productprice is not null
                and productprice >= 0
                and productprice not in ('NaN'::numeric, 'Infinity'::numeric, '-Infinity'::numeric)
                and waste is not null
                and waste between 0 and 100
                and waste not in ('NaN'::double precision, 'Infinity'::double precision, '-Infinity'::double precision)
                and unit in ('g', 'kg', 'ml', 'l', 'pcs')
                and base_unit in ('g', 'ml', 'pcs')
                and (
                    (unit in ('g', 'kg') and base_unit = 'g')
                    or (unit in ('ml', 'l') and base_unit = 'ml')
                    or (unit = 'pcs' and base_unit = 'pcs')
                )
                and unit_factor is not null
                and unit_factor > 0
                and unit_factor not in ('NaN'::numeric, 'Infinity'::numeric, '-Infinity'::numeric)
            ) not valid;
    end if;

    if not exists (select 1 from pg_constraint where conname = 'dish_catalog_values_chk') then
        alter table sales.dish
            add constraint dish_catalog_values_chk
            check (
                dishname is not null
                and btrim(dishname) <> ''
                and price is not null
                and price > 0
                and price not in ('NaN'::double precision, 'Infinity'::double precision, '-Infinity'::double precision)
                and weight is not null
                and weight > 0
                and weight not in ('NaN'::double precision, 'Infinity'::double precision, '-Infinity'::double precision)
                and firstcost is not null
                and firstcost >= 0
                and firstcost not in ('NaN'::double precision, 'Infinity'::double precision, '-Infinity'::double precision)
            ) not valid;
    end if;

    if not exists (select 1 from pg_constraint where conname = 'techproduct_catalog_values_chk') then
        alter table sales.techproduct
            add constraint techproduct_catalog_values_chk
            check (
                weight is not null
                and weight > 0
                and weight not in ('NaN'::double precision, 'Infinity'::double precision, '-Infinity'::double precision)
                and waste is not null
                and waste between 0 and 100
                and waste not in ('NaN'::double precision, 'Infinity'::double precision, '-Infinity'::double precision)
            ) not valid;
    end if;

    if not exists (select 1 from pg_constraint where conname = 'dish_set_catalog_values_chk') then
        alter table sales.dish_set
            add constraint dish_set_catalog_values_chk
            check (
                setname is not null
                and btrim(setname) <> ''
                and price is not null
                and price > 0
                and price not in ('NaN'::double precision, 'Infinity'::double precision, '-Infinity'::double precision)
                and first_cost is not null
                and first_cost >= 0
                and first_cost not in ('NaN'::double precision, 'Infinity'::double precision, '-Infinity'::double precision)
            ) not valid;
    end if;
end $$;

comment on constraint product_catalog_values_chk on sales.product is
    'NOT VALID until legacy catalog rows are normalized; enforced for all new and updated rows.';
comment on constraint dish_catalog_values_chk on sales.dish is
    'NOT VALID until legacy catalog rows are normalized; enforced for all new and updated rows.';

-- Salary is calculated from the per-shift rate and immutable accrual/payment ledgers.
-- These legacy accumulators are no longer part of the employee model.
alter table sales.person
    drop column if exists salary,
    drop column if exists numdays;
