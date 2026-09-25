-- Consumables share the existing product/warehouse valuation, but remain outside recipes.

alter table sales.product
    add column if not exists item_type varchar(24) not null default 'ingredient';

update sales.product
set item_type = 'ingredient'
where item_type is null or item_type not in ('ingredient', 'consumable', 'packaging');

alter table sales.product
    drop constraint if exists product_item_type_chk;

alter table sales.product
    add constraint product_item_type_chk
        check (item_type in ('ingredient', 'consumable', 'packaging'));

create table if not exists sales.consumable_rule (
    product_id integer primary key references sales.product(productid) on delete cascade,
    basis varchar(24) not null,
    default_quantity numeric(18,6) not null,
    trigger_quantity numeric(18,6) not null default 1,
    dish_category_id integer references sales.dish_category(category_id) on delete set null,
    active boolean not null default true,
    updated_at timestamp without time zone not null default now(),
    constraint consumable_rule_basis_chk
        check (basis in ('per_person', 'per_order', 'per_menu_item')),
    constraint consumable_rule_default_quantity_chk check (default_quantity >= 0),
    constraint consumable_rule_trigger_quantity_chk check (trigger_quantity > 0)
);

alter table sales."order"
    add column if not exists person_count integer not null default 1;

alter table sales."order"
    drop constraint if exists order_person_count_chk;

alter table sales."order"
    add constraint order_person_count_chk check (person_count between 1 and 1000);

create table if not exists sales.order_consumable (
    id bigint generated always as identity primary key,
    order_id integer not null references sales."order"(orderid) on delete cascade,
    product_id integer not null references sales.product(productid),
    suggested_quantity numeric(18,6) not null,
    actual_quantity numeric(18,6) not null,
    surcharge_amount numeric(14,2) not null default 0,
    inventory_cost numeric(18,6),
    product_name_snapshot varchar(255) not null,
    base_unit_snapshot varchar(16) not null,
    manual_override boolean not null default false,
    created_by varchar(100),
    created_at timestamp without time zone not null default now(),
    updated_at timestamp without time zone not null default now(),
    constraint order_consumable_quantity_chk
        check (suggested_quantity >= 0 and actual_quantity >= 0),
    constraint order_consumable_surcharge_chk check (surcharge_amount >= 0),
    constraint order_consumable_order_product_uq unique (order_id, product_id)
);

create index if not exists order_consumable_order_idx
    on sales.order_consumable(order_id);

create index if not exists consumable_rule_active_idx
    on sales.consumable_rule(active, basis);

