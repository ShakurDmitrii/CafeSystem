alter table sales.client
    add column if not exists normalized_number varchar(32);

with ranked_numbers as (
    select clientid,
           case
               when left(regexp_replace(coalesce(number, ''), '\D', '', 'g'), 1) = '8'
                    and length(regexp_replace(coalesce(number, ''), '\D', '', 'g')) = 11
                   then '7' || substring(regexp_replace(number, '\D', '', 'g') from 2)
               else regexp_replace(coalesce(number, ''), '\D', '', 'g')
           end as normalized,
           row_number() over (
               partition by case
                   when left(regexp_replace(coalesce(number, ''), '\D', '', 'g'), 1) = '8'
                        and length(regexp_replace(coalesce(number, ''), '\D', '', 'g')) = 11
                       then '7' || substring(regexp_replace(number, '\D', '', 'g') from 2)
                   else regexp_replace(coalesce(number, ''), '\D', '', 'g')
               end
               order by clientid
           ) as duplicate_number
    from sales.client
)
update sales.client as client
set normalized_number = case
    when ranked_numbers.normalized = '' or ranked_numbers.duplicate_number > 1 then null
    else ranked_numbers.normalized
end
from ranked_numbers
where ranked_numbers.clientid = client.clientid;

create unique index if not exists client_normalized_number_uq_idx
    on sales.client (normalized_number)
    where normalized_number is not null;

alter table sales."order"
    add column if not exists debt_original_amount numeric(12, 2),
    add column if not exists debt_remaining_amount numeric(12, 2),
    add column if not exists inventory_consumed boolean,
    add column if not exists paid_at timestamp;

do $$
declare
    fallback_client_id integer;
begin
    if exists (
        select 1 from sales."order" where duty = true and clientid is null
    ) then
        insert into sales.client (fullname, number, normalized_number)
        values ('Неидентифицированные долги', null, null)
        returning clientid into fallback_client_id;

        update sales."order"
        set clientid = fallback_client_id
        where duty = true and clientid is null;
    end if;
end $$;

update sales."order"
set debt_payment_date = coalesce("Date", current_date)
where duty = true
  and debt_payment_date is null;

alter table sales.shift
    add column if not exists closed_at timestamp;

update sales.shift
set closed_at = data + endtime
where endtime is not null
  and closed_at is null;

update sales."order"
set debt_original_amount = round(amount::numeric, 2),
    debt_remaining_amount = round(amount::numeric, 2)
where duty = true
  and debt_original_amount is null;

update sales."order"
set inventory_consumed = coalesce(is_paid, false) or coalesce(duty, false)
where inventory_consumed is null;

update sales."order"
set paid_at = coalesce(created_at, "Date"::timestamp)
where is_paid = true
  and paid_at is null;

alter table sales."order"
    alter column inventory_consumed set default false,
    alter column inventory_consumed set not null;

do $$
begin
    if not exists (
        select 1
        from information_schema.table_constraints
        where constraint_schema = 'sales'
          and table_name = 'order'
          and constraint_name = 'order_debt_amounts_chk'
    ) then
        alter table sales."order"
            add constraint order_debt_amounts_chk check (
                (duty = false and coalesce(debt_remaining_amount, 0) = 0)
                or
                (duty = true
                    and clientid is not null
                    and debt_payment_date is not null
                    and debt_original_amount > 0
                    and debt_remaining_amount > 0
                    and debt_remaining_amount <= debt_original_amount)
            ) not valid;
    end if;
end $$;

create table if not exists sales.debt_payment (
    id bigint generated always as identity primary key,
    order_id integer not null,
    client_id integer not null,
    amount numeric(12, 2) not null,
    remaining_after numeric(12, 2) not null,
    payment_type varchar(20) not null,
    idempotency_key varchar(100) not null,
    created_at timestamp not null default now(),
    constraint debt_payment_amount_positive_chk check (amount > 0),
    constraint debt_payment_remaining_nonnegative_chk check (remaining_after >= 0),
    constraint debt_payment_order_fk foreign key (order_id)
        references sales."order"(orderid),
    constraint debt_payment_client_fk foreign key (client_id)
        references sales.client(clientid)
);

create unique index if not exists debt_payment_idempotency_uq_idx
    on sales.debt_payment (idempotency_key);

create index if not exists debt_payment_order_created_idx
    on sales.debt_payment (order_id, created_at desc);

alter table sales.client_vk_link_code
    add column if not exists code_fingerprint varchar(64);

create unique index if not exists client_vk_link_code_fingerprint_active_uq_idx
    on sales.client_vk_link_code (code_fingerprint)
    where used_at is null and code_fingerprint is not null;

create table if not exists sales.client_vk_link_attempt (
    vk_user_id bigint primary key,
    failed_attempts integer not null default 0,
    window_started_at timestamp not null default now(),
    blocked_until timestamp,
    updated_at timestamp not null default now(),
    constraint client_vk_link_attempt_nonnegative_chk check (failed_attempts >= 0)
);

create table if not exists sales.client_vk_link_event (
    id bigint generated always as identity primary key,
    client_id integer,
    vk_user_id bigint not null,
    event_type varchar(20) not null,
    vk_domain varchar(255),
    created_at timestamp not null default now(),
    constraint client_vk_link_event_type_chk check (event_type in ('LINKED', 'RELINKED', 'UNLINKED')),
    constraint client_vk_link_event_client_fk foreign key (client_id)
        references sales.client(clientid) on delete set null
);

create index if not exists client_vk_link_event_user_created_idx
    on sales.client_vk_link_event (vk_user_id, created_at desc);
