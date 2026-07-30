alter table sales."order"
    add column if not exists cancelled_at timestamp,
    add column if not exists cancel_reason varchar(500),
    add column if not exists version integer;

update sales."order"
set version = 0
where version is null;

alter table sales."order"
    alter column version set default 0,
    alter column version set not null;

create index if not exists order_active_shift_idx
    on sales."order" (shiftid, orderid)
    where cancelled_at is null;

