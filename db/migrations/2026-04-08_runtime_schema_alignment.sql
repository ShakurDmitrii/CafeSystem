-- Align schema changes that were previously created by DatabaseSchemaInitializer.

create sequence if not exists sales.person_personid_seq;

select setval(
    'sales.person_personid_seq',
    coalesce((select max(personid) from sales.person), 0) + 1,
    false
);

do $$
begin
    if not exists (
        select 1
        from information_schema.columns
        where table_schema = 'sales'
          and table_name = 'person'
          and column_name = 'personid'
          and is_identity = 'YES'
    ) then
        alter table sales.person
            alter column personid set default nextval('sales.person_personid_seq');
        alter sequence sales.person_personid_seq
            owned by sales.person.personid;
    end if;
end $$;

alter table sales.person
    add column if not exists archived boolean;

update sales.person
set archived = false
where archived is null;

alter table sales.person
    alter column archived set default false,
    alter column archived set not null;

create sequence if not exists sales.user_account_id_seq;

select setval(
    'sales.user_account_id_seq',
    coalesce((select max(id) from sales.user_account), 0) + 1,
    false
);

do $$
begin
    if not exists (
        select 1
        from information_schema.columns
        where table_schema = 'sales'
          and table_name = 'user_account'
          and column_name = 'id'
          and is_identity = 'YES'
    ) then
        alter table sales.user_account
            alter column id set default nextval('sales.user_account_id_seq');
        alter sequence sales.user_account_id_seq
            owned by sales.user_account.id;
    end if;
end $$;

create sequence if not exists sales.shiftperson_shiftpersonid_seq;

select setval(
    'sales.shiftperson_shiftpersonid_seq',
    coalesce((select max(shiftpersonid) from sales.shiftperson), 0) + 1,
    false
);

do $$
begin
    if not exists (
        select 1
        from information_schema.columns
        where table_schema = 'sales'
          and table_name = 'shiftperson'
          and column_name = 'shiftpersonid'
          and is_identity = 'YES'
    ) then
        alter table sales.shiftperson
            alter column shiftpersonid set default nextval('sales.shiftperson_shiftpersonid_seq');
        alter sequence sales.shiftperson_shiftpersonid_seq
            owned by sales.shiftperson.shiftpersonid;
    end if;
end $$;

alter table sales."order"
    add column if not exists delivery_phone varchar(50),
    add column if not exists delivery_address varchar(255),
    add column if not exists payment_type varchar(20),
    add column if not exists is_paid boolean;

update sales."order"
set payment_type = 'cash'
where payment_type is null;

update sales."order"
set is_paid = true
where is_paid is null;

do $$
begin
    if exists (
        select 1
        from information_schema.table_constraints
        where constraint_schema = 'sales'
          and table_name = 'shift'
          and constraint_name = 'shift_unique'
    ) then
        alter table sales.shift drop constraint shift_unique;
    end if;
end $$;

create unique index if not exists shift_open_unique_person_idx
    on sales.shift (personcode)
    where endtime is null and personcode is not null;

alter table sales.orderdish
    add column if not exists set_id integer;

alter table sales.orderdish
    alter column dishid drop not null;

create index if not exists orderdish_set_idx
    on sales.orderdish (set_id);

do $$
begin
    if not exists (
        select 1
        from information_schema.table_constraints
        where constraint_schema = 'sales'
          and table_name = 'orderdish'
          and constraint_name = 'orderdish_set_fk'
    ) then
        alter table sales.orderdish
            add constraint orderdish_set_fk
            foreign key (set_id)
            references sales.dish_set(setid);
    end if;
end $$;

do $$
begin
    if not exists (
        select 1
        from information_schema.table_constraints
        where constraint_schema = 'sales'
          and table_name = 'orderdish'
          and constraint_name = 'orderdish_item_target_chk'
    ) then
        alter table sales.orderdish
            add constraint orderdish_item_target_chk
            check (
                (case when dishid is not null then 1 else 0 end) +
                (case when set_id is not null then 1 else 0 end) = 1
            );
    end if;
end $$;

create table if not exists sales.shift_inventory_snapshot (
    id integer generated always as identity primary key,
    shift_id integer not null,
    warehouse_id integer not null,
    product_id integer not null,
    quantity double precision not null default 0,
    created_at timestamp not null default now()
);

alter table sales.shift_inventory_snapshot
    add column if not exists shift_id integer,
    add column if not exists warehouse_id integer,
    add column if not exists product_id integer,
    add column if not exists quantity double precision,
    add column if not exists created_at timestamp;

update sales.shift_inventory_snapshot
set quantity = 0
where quantity is null;

update sales.shift_inventory_snapshot
set created_at = now()
where created_at is null;

alter table sales.shift_inventory_snapshot
    alter column shift_id set not null,
    alter column warehouse_id set not null,
    alter column product_id set not null,
    alter column quantity set default 0,
    alter column quantity set not null,
    alter column created_at set default now(),
    alter column created_at set not null;

create unique index if not exists shift_inventory_snapshot_shift_wh_product_uq_idx
    on sales.shift_inventory_snapshot (shift_id, warehouse_id, product_id);

create index if not exists shift_inventory_snapshot_shift_wh_idx
    on sales.shift_inventory_snapshot (shift_id, warehouse_id);

do $$
begin
    if not exists (
        select 1
        from information_schema.table_constraints
        where constraint_schema = 'sales'
          and table_name = 'shift_inventory_snapshot'
          and constraint_name = 'shift_inventory_snapshot_shift_fk'
    ) then
        alter table sales.shift_inventory_snapshot
            add constraint shift_inventory_snapshot_shift_fk
            foreign key (shift_id)
            references sales.shift(id)
            on delete cascade;
    end if;
end $$;

do $$
begin
    if not exists (
        select 1
        from information_schema.table_constraints
        where constraint_schema = 'sales'
          and table_name = 'shift_inventory_snapshot'
          and constraint_name = 'shift_inventory_snapshot_warehouse_fk'
    ) then
        alter table sales.shift_inventory_snapshot
            add constraint shift_inventory_snapshot_warehouse_fk
            foreign key (warehouse_id)
            references sales.warehouse(warehouseid)
            on delete cascade;
    end if;
end $$;

do $$
begin
    if not exists (
        select 1
        from information_schema.table_constraints
        where constraint_schema = 'sales'
          and table_name = 'shift_inventory_snapshot'
          and constraint_name = 'shift_inventory_snapshot_product_fk'
    ) then
        alter table sales.shift_inventory_snapshot
            add constraint shift_inventory_snapshot_product_fk
            foreign key (product_id)
            references sales.product(productid);
    end if;
end $$;

create table if not exists sales.inventory_shift_report (
    id integer generated always as identity primary key,
    warehouse_id integer not null,
    shift_id integer not null,
    snapshot_available boolean not null default false,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now(),
    applied_at timestamp null
);

create table if not exists sales.inventory_shift_report_line (
    id integer generated always as identity primary key,
    report_id integer not null,
    product_id integer not null,
    product_name varchar(255) not null,
    unit varchar(50) not null default 'g',
    opening_qty double precision not null default 0,
    movement_in_qty double precision not null default 0,
    movement_out_qty double precision not null default 0,
    movement_net_qty double precision not null default 0,
    sold_qty double precision not null default 0,
    expected_qty double precision not null default 0,
    system_qty double precision not null default 0,
    actual_qty double precision null,
    discrepancy_qty double precision null,
    shortage_qty double precision not null default 0,
    shortage_flag boolean not null default false,
    sort_order integer not null default 0
);

alter table sales.inventory_shift_report
    add column if not exists warehouse_id integer,
    add column if not exists shift_id integer,
    add column if not exists snapshot_available boolean,
    add column if not exists created_at timestamp,
    add column if not exists updated_at timestamp,
    add column if not exists applied_at timestamp;

update sales.inventory_shift_report
set created_at = now()
where created_at is null;

update sales.inventory_shift_report
set updated_at = coalesce(updated_at, created_at, now())
where updated_at is null;

update sales.inventory_shift_report
set snapshot_available = false
where snapshot_available is null;

alter table sales.inventory_shift_report
    alter column warehouse_id set not null,
    alter column shift_id set not null,
    alter column snapshot_available set default false,
    alter column snapshot_available set not null,
    alter column created_at set default now(),
    alter column created_at set not null,
    alter column updated_at set default now(),
    alter column updated_at set not null;

alter table sales.inventory_shift_report_line
    add column if not exists report_id integer,
    add column if not exists product_id integer,
    add column if not exists product_name varchar(255),
    add column if not exists unit varchar(50),
    add column if not exists opening_qty double precision,
    add column if not exists movement_in_qty double precision,
    add column if not exists movement_out_qty double precision,
    add column if not exists movement_net_qty double precision,
    add column if not exists sold_qty double precision,
    add column if not exists expected_qty double precision,
    add column if not exists system_qty double precision,
    add column if not exists actual_qty double precision,
    add column if not exists discrepancy_qty double precision,
    add column if not exists shortage_qty double precision,
    add column if not exists shortage_flag boolean,
    add column if not exists sort_order integer;

update sales.inventory_shift_report_line
set unit = 'g'
where unit is null or unit = '';

update sales.inventory_shift_report_line set sold_qty = 0 where sold_qty is null;
update sales.inventory_shift_report_line set opening_qty = 0 where opening_qty is null;
update sales.inventory_shift_report_line set movement_in_qty = 0 where movement_in_qty is null;
update sales.inventory_shift_report_line set movement_out_qty = 0 where movement_out_qty is null;
update sales.inventory_shift_report_line set movement_net_qty = 0 where movement_net_qty is null;
update sales.inventory_shift_report_line set expected_qty = 0 where expected_qty is null;
update sales.inventory_shift_report_line set system_qty = 0 where system_qty is null;
update sales.inventory_shift_report_line set shortage_qty = 0 where shortage_qty is null;
update sales.inventory_shift_report_line set shortage_flag = false where shortage_flag is null;
update sales.inventory_shift_report_line set sort_order = 0 where sort_order is null;

alter table sales.inventory_shift_report_line
    alter column report_id set not null,
    alter column product_id set not null,
    alter column product_name set not null,
    alter column unit set default 'g',
    alter column unit set not null,
    alter column opening_qty set default 0,
    alter column opening_qty set not null,
    alter column movement_in_qty set default 0,
    alter column movement_in_qty set not null,
    alter column movement_out_qty set default 0,
    alter column movement_out_qty set not null,
    alter column movement_net_qty set default 0,
    alter column movement_net_qty set not null,
    alter column sold_qty set default 0,
    alter column sold_qty set not null,
    alter column expected_qty set default 0,
    alter column expected_qty set not null,
    alter column system_qty set default 0,
    alter column system_qty set not null,
    alter column shortage_qty set default 0,
    alter column shortage_qty set not null,
    alter column shortage_flag set default false,
    alter column shortage_flag set not null,
    alter column sort_order set default 0,
    alter column sort_order set not null;

create unique index if not exists inventory_shift_report_wh_shift_uq_idx
    on sales.inventory_shift_report (warehouse_id, shift_id);

create unique index if not exists inventory_shift_report_line_report_product_uq_idx
    on sales.inventory_shift_report_line (report_id, product_id);

create index if not exists inventory_shift_report_line_report_sort_idx
    on sales.inventory_shift_report_line (report_id, sort_order);

do $$
begin
    if not exists (
        select 1
        from information_schema.table_constraints
        where constraint_schema = 'sales'
          and table_name = 'inventory_shift_report'
          and constraint_name = 'inventory_shift_report_warehouse_fk'
    ) then
        alter table sales.inventory_shift_report
            add constraint inventory_shift_report_warehouse_fk
            foreign key (warehouse_id)
            references sales.warehouse(warehouseid)
            on delete cascade;
    end if;
end $$;

do $$
begin
    if not exists (
        select 1
        from information_schema.table_constraints
        where constraint_schema = 'sales'
          and table_name = 'inventory_shift_report'
          and constraint_name = 'inventory_shift_report_shift_fk'
    ) then
        alter table sales.inventory_shift_report
            add constraint inventory_shift_report_shift_fk
            foreign key (shift_id)
            references sales.shift(id)
            on delete cascade;
    end if;
end $$;

do $$
begin
    if not exists (
        select 1
        from information_schema.table_constraints
        where constraint_schema = 'sales'
          and table_name = 'inventory_shift_report_line'
          and constraint_name = 'inventory_shift_report_line_report_fk'
    ) then
        alter table sales.inventory_shift_report_line
            add constraint inventory_shift_report_line_report_fk
            foreign key (report_id)
            references sales.inventory_shift_report(id)
            on delete cascade;
    end if;
end $$;

do $$
begin
    if not exists (
        select 1
        from information_schema.table_constraints
        where constraint_schema = 'sales'
          and table_name = 'inventory_shift_report_line'
          and constraint_name = 'inventory_shift_report_line_product_fk'
    ) then
        alter table sales.inventory_shift_report_line
            add constraint inventory_shift_report_line_product_fk
            foreign key (product_id)
            references sales.product(productid);
    end if;
end $$;
