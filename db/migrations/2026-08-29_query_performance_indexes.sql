-- Indexes for the read paths used by orders, shifts, Z-reports, and debt views.
-- PostgreSQL does not automatically index referencing columns of foreign keys.

create index if not exists orderdish_order_idx
    on sales.orderdish (orderid);

create index if not exists shiftperson_shift_person_idx
    on sales.shiftperson (shiftid, personid);

create index if not exists order_active_client_idx
    on sales."order" (clientid, orderid)
    where cancelled_at is null;

create index if not exists order_active_date_idx
    on sales."order" ("Date", orderid)
    where cancelled_at is null;

create index if not exists order_active_status_idx
    on sales."order" (status, orderid)
    where cancelled_at is null;
