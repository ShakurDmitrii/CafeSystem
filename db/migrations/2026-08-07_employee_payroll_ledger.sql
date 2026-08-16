create table if not exists sales.salary_accrual (
    id bigserial primary key,
    person_id integer not null,
    shift_id integer not null,
    worked_on date not null,
    daily_rate numeric(14, 2) not null,
    amount numeric(14, 2) not null,
    created_at timestamp not null default now(),
    constraint salary_accrual_person_fk foreign key (person_id)
        references sales.person(personid),
    constraint salary_accrual_shift_fk foreign key (shift_id)
        references sales.shift(id),
    constraint salary_accrual_daily_rate_nonnegative_chk check (daily_rate >= 0),
    constraint salary_accrual_amount_nonnegative_chk check (amount >= 0),
    constraint salary_accrual_person_shift_uq unique (person_id, shift_id)
);

create index if not exists salary_accrual_person_worked_idx
    on sales.salary_accrual (person_id, worked_on desc, id desc);

create table if not exists sales.salary_payment (
    id bigserial primary key,
    person_id integer not null,
    entry_type varchar(20) not null,
    amount numeric(14, 2) not null,
    balance_after numeric(14, 2) not null,
    idempotency_key varchar(100) not null,
    author_account_id integer not null,
    related_payment_id bigint,
    comment varchar(500),
    created_at timestamp not null default now(),
    constraint salary_payment_person_fk foreign key (person_id)
        references sales.person(personid),
    constraint salary_payment_author_fk foreign key (author_account_id)
        references sales.user_account(id),
    constraint salary_payment_related_fk foreign key (related_payment_id)
        references sales.salary_payment(id),
    constraint salary_payment_type_chk check (entry_type in ('PAYMENT', 'REVERSAL')),
    constraint salary_payment_amount_positive_chk check (amount > 0),
    constraint salary_payment_balance_nonnegative_chk check (balance_after >= 0),
    constraint salary_payment_relation_chk check (
        (entry_type = 'PAYMENT' and related_payment_id is null)
        or (entry_type = 'REVERSAL' and related_payment_id is not null)
    ),
    constraint salary_payment_idempotency_uq unique (idempotency_key)
);

create index if not exists salary_payment_person_created_idx
    on sales.salary_payment (person_id, created_at desc, id desc);

create unique index if not exists salary_payment_single_reversal_uq
    on sales.salary_payment (related_payment_id)
    where entry_type = 'REVERSAL';

comment on table sales.salary_accrual is
    'Immutable per-shift salary accruals created when a shift is closed. The server ledger starts with shifts closed after this migration.';
comment on table sales.salary_payment is
    'Immutable salary payment ledger. Corrections are stored as REVERSAL entries rather than deleting history.';
