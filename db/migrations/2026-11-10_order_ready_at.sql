-- Заказ «к определённому времени»: момент, к которому заказ должен быть готов.
-- NULL означает обычный режим «через N минут» (ORDER.time от created_at).
ALTER TABLE sales."order"
    ADD COLUMN IF NOT EXISTS ready_at timestamp without time zone;

ALTER TABLE sales."order"
    DROP CONSTRAINT IF EXISTS order_ready_at_after_created,
    ADD CONSTRAINT order_ready_at_after_created
        CHECK (ready_at IS NULL OR created_at IS NULL OR ready_at > created_at);
