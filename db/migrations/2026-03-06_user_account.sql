-- Auth accounts table required for JWT login/roles
-- Run once:
-- \i db/migrations/2026-03-06_user_account.sql

create table if not exists sales.user_account (
    id serial not null,
    personid integer not null,
    username varchar not null,
    password_hash varchar(255),
    role varchar not null,
    is_active boolean default true,
    created_at timestamp default now(),
    constraint user_account_pk primary key (id),
    constraint user_account_unique unique (personid),
    constraint user_account_unique_1 unique (username),
    constraint user_account_person_fk foreign key (personid) references sales.person(personid),
    constraint person_role_chk check (role in ('OWNER', 'WORKER'))
);
