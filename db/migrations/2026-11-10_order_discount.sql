ALTER TABLE sales."order"
    ADD COLUMN IF NOT EXISTS discount_amount numeric(14, 2) NOT NULL DEFAULT 0;

ALTER TABLE sales."order"
    DROP CONSTRAINT IF EXISTS order_discount_amount_non_negative,
    ADD CONSTRAINT order_discount_amount_non_negative
        CHECK (discount_amount >= 0);
