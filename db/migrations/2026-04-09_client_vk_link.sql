create table if not exists sales.client_vk_link (
    id bigint generated always as identity primary key,
    client_id integer not null,
    vk_user_id bigint not null,
    vk_domain varchar(255),
    verified_at timestamp not null default now(),
    created_at timestamp not null default now()
);

create unique index if not exists client_vk_link_vk_user_uq_idx
    on sales.client_vk_link (vk_user_id);

create unique index if not exists client_vk_link_client_uq_idx
    on sales.client_vk_link (client_id);

do $$
begin
    if not exists (
        select 1
        from information_schema.table_constraints
        where constraint_schema = 'sales'
          and table_name = 'client_vk_link'
          and constraint_name = 'client_vk_link_client_fk'
    ) then
        alter table sales.client_vk_link
            add constraint client_vk_link_client_fk
            foreign key (client_id)
            references sales.client(clientid)
            on delete cascade;
    end if;
end $$;

create table if not exists sales.client_vk_link_code (
    id bigint generated always as identity primary key,
    client_id integer not null,
    code_hash varchar(255) not null,
    expires_at timestamp not null,
    used_at timestamp,
    created_at timestamp not null default now()
);

create index if not exists client_vk_link_code_client_active_idx
    on sales.client_vk_link_code (client_id, expires_at)
    where used_at is null;

create index if not exists client_vk_link_code_expires_idx
    on sales.client_vk_link_code (expires_at);

do $$
begin
    if not exists (
        select 1
        from information_schema.table_constraints
        where constraint_schema = 'sales'
          and table_name = 'client_vk_link_code'
          and constraint_name = 'client_vk_link_code_client_fk'
    ) then
        alter table sales.client_vk_link_code
            add constraint client_vk_link_code_client_fk
            foreign key (client_id)
            references sales.client(clientid)
            on delete cascade;
    end if;
end $$;
