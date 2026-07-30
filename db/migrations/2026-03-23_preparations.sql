-- Support for semi-finished preparations (e.g. sauces) with own tech cards
-- and ability to include preparations inside dish tech cards.

create table if not exists sales.preparation (
    preparationid integer generated always as identity primary key,
    preparationname varchar(255) not null,
    output_weight double precision not null default 1
);

create table if not exists sales.preparationwarehouse (
    warehouseid integer not null,
    preparationid integer not null,
    preparationwarehouseid integer generated always as identity primary key,
    quantity double precision not null default 0
);

alter table sales.preparation
    add column if not exists preparationname varchar(255);

alter table sales.preparation
    add column if not exists output_weight double precision;

alter table sales.preparation
    alter column output_weight set default 1;

update sales.preparation
set output_weight = 1
where output_weight is null or output_weight <= 0;

update sales.preparationwarehouse
set quantity = 0
where quantity is null;

create unique index if not exists preparation_name_uq_idx
    on sales.preparation (lower(preparationname));

create index if not exists preparationwarehouse_wh_idx
    on sales.preparationwarehouse (warehouseid);

create index if not exists preparationwarehouse_prep_idx
    on sales.preparationwarehouse (preparationid);

alter table sales.techproduct
    add column if not exists preparation_id integer;

alter table sales.techproduct
    add column if not exists ingredient_preparation_id integer;

alter table sales.techproduct
    alter column "DishId" drop not null;

alter table sales.techproduct
    alter column productid drop not null;

create index if not exists techproduct_preparation_owner_idx
    on sales.techproduct (preparation_id);

create index if not exists techproduct_preparation_ingredient_idx
    on sales.techproduct (ingredient_preparation_id);

do $$
begin
    if not exists (
        select 1
        from information_schema.table_constraints
        where constraint_schema = 'sales'
          and table_name = 'preparation'
          and constraint_name = 'preparation_output_weight_positive_chk'
    ) then
        alter table sales.preparation
            add constraint preparation_output_weight_positive_chk
            check (output_weight > 0);
    end if;
end $$;

do $$
begin
    if not exists (
        select 1
        from information_schema.table_constraints
        where constraint_schema = 'sales'
          and table_name = 'preparationwarehouse'
          and constraint_name = 'preparationwarehouse_warehouse_fk'
    ) then
        alter table sales.preparationwarehouse
            add constraint preparationwarehouse_warehouse_fk
            foreign key (warehouseid)
            references sales.warehouse (warehouseid)
            on delete cascade;
    end if;
end $$;

do $$
begin
    if not exists (
        select 1
        from information_schema.table_constraints
        where constraint_schema = 'sales'
          and table_name = 'preparationwarehouse'
          and constraint_name = 'preparationwarehouse_preparation_fk'
    ) then
        alter table sales.preparationwarehouse
            add constraint preparationwarehouse_preparation_fk
            foreign key (preparationid)
            references sales.preparation (preparationid)
            on delete cascade;
    end if;
end $$;

do $$
begin
    if not exists (
        select 1
        from information_schema.table_constraints
        where constraint_schema = 'sales'
          and table_name = 'preparationwarehouse'
          and constraint_name = 'preparationwarehouse_quantity_nonnegative_chk'
    ) then
        alter table sales.preparationwarehouse
            add constraint preparationwarehouse_quantity_nonnegative_chk
            check (quantity >= 0);
    end if;
end $$;

do $$
begin
    if not exists (
        select 1
        from information_schema.table_constraints
        where constraint_schema = 'sales'
          and table_name = 'techproduct'
          and constraint_name = 'techproduct_preparation_owner_fk'
    ) then
        alter table sales.techproduct
            add constraint techproduct_preparation_owner_fk
            foreign key (preparation_id)
            references sales.preparation (preparationid)
            on delete cascade;
    end if;
end $$;

do $$
begin
    if not exists (
        select 1
        from information_schema.table_constraints
        where constraint_schema = 'sales'
          and table_name = 'techproduct'
          and constraint_name = 'techproduct_ingredient_preparation_fk'
    ) then
        alter table sales.techproduct
            add constraint techproduct_ingredient_preparation_fk
            foreign key (ingredient_preparation_id)
            references sales.preparation (preparationid);
    end if;
end $$;

do $$
begin
    if not exists (
        select 1
        from information_schema.table_constraints
        where constraint_schema = 'sales'
          and table_name = 'techproduct'
          and constraint_name = 'techproduct_owner_chk'
    ) then
        alter table sales.techproduct
            add constraint techproduct_owner_chk
            check (
                (case when "DishId" is not null then 1 else 0 end) +
                (case when preparation_id is not null then 1 else 0 end) = 1
            );
    end if;
end $$;

do $$
begin
    if not exists (
        select 1
        from information_schema.table_constraints
        where constraint_schema = 'sales'
          and table_name = 'techproduct'
          and constraint_name = 'techproduct_ingredient_chk'
    ) then
        alter table sales.techproduct
            add constraint techproduct_ingredient_chk
            check (
                (case when productid is not null then 1 else 0 end) +
                (case when ingredient_preparation_id is not null then 1 else 0 end) = 1
            );
    end if;
end $$;
