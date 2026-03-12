-- Dish categories
-- Run once in PostgreSQL (DBeaver/psql):
-- \i db/migrations/2026-03-10_dish_categories.sql

create table if not exists sales.dish_category (
    category_id serial primary key,
    name varchar(255) not null unique,
    created_at timestamptz not null default now()
);

alter table sales.dish
    add column if not exists category_id integer;

do $$
begin
    if not exists (
        select 1 from information_schema.table_constraints tc
        where tc.table_schema = 'sales'
          and tc.table_name = 'dish'
          and tc.constraint_name = 'dish_category_fk'
    ) then
        alter table sales.dish
            add constraint dish_category_fk
            foreign key (category_id)
            references sales.dish_category(category_id)
            on delete set null;
    end if;
end $$;

create index if not exists dish_category_id_idx on sales.dish(category_id);

-- Backfill from legacy dish.category text
insert into sales.dish_category (name)
select distinct trim(category)
from sales.dish
where category is not null and trim(category) <> ''
on conflict (name) do nothing;

update sales.dish d
set category_id = c.category_id
from sales.dish_category c
where d.category_id is null
  and d.category is not null
  and trim(d.category) <> ''
  and c.name = trim(d.category);
