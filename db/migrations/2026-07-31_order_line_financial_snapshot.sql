alter table sales.orderdish
    add column if not exists unit_price double precision,
    add column if not exists unit_cost double precision;

update sales.orderdish od
set unit_price = coalesce(od.unit_price, d.price),
    unit_cost = coalesce(od.unit_cost, d.firstcost)
from sales.dish d
where od.dishid = d.dishid
  and (od.unit_price is null or od.unit_cost is null);

update sales.orderdish od
set unit_price = coalesce(od.unit_price, ds.price),
    unit_cost = coalesce(od.unit_cost, ds.first_cost)
from sales.dish_set ds
where od.set_id = ds.setid
  and (od.unit_price is null or od.unit_cost is null);

update sales.orderdish
set unit_price = 0
where unit_price is null;

update sales.orderdish
set unit_cost = 0
where unit_cost is null;

alter table sales.orderdish
    alter column unit_price set default 0,
    alter column unit_price set not null,
    alter column unit_cost set default 0,
    alter column unit_cost set not null;

do $$
begin
    if not exists (
        select 1
        from information_schema.table_constraints
        where constraint_schema = 'sales'
          and table_name = 'orderdish'
          and constraint_name = 'orderdish_unit_price_nonnegative_chk'
    ) then
        alter table sales.orderdish
            add constraint orderdish_unit_price_nonnegative_chk
            check (unit_price >= 0);
    end if;

    if not exists (
        select 1
        from information_schema.table_constraints
        where constraint_schema = 'sales'
          and table_name = 'orderdish'
          and constraint_name = 'orderdish_unit_cost_nonnegative_chk'
    ) then
        alter table sales.orderdish
            add constraint orderdish_unit_cost_nonnegative_chk
            check (unit_cost >= 0);
    end if;
end $$;
