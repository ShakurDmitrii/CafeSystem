ALTER TABLE sales."order"
    ADD COLUMN IF NOT EXISTS cash_received numeric(14, 2),
    ADD COLUMN IF NOT EXISTS cash_change numeric(14, 2);

ALTER TABLE sales."order"
    DROP CONSTRAINT IF EXISTS order_cash_received_non_negative,
    ADD CONSTRAINT order_cash_received_non_negative
        CHECK (cash_received IS NULL OR cash_received >= 0),
    DROP CONSTRAINT IF EXISTS order_cash_change_non_negative,
    ADD CONSTRAINT order_cash_change_non_negative
        CHECK (cash_change IS NULL OR cash_change >= 0);

