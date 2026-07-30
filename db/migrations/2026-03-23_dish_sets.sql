CREATE TABLE IF NOT EXISTS sales.dish_set (
    setid integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    setname varchar(255) NOT NULL,
    price double precision NOT NULL DEFAULT 0,
    first_cost double precision NOT NULL DEFAULT 0,
    image_url varchar(1000)
);

CREATE TABLE IF NOT EXISTS sales.dish_set_item (
    set_item_id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    set_id integer NOT NULL,
    dish_id integer NOT NULL,
    qty integer NOT NULL DEFAULT 1
);

CREATE UNIQUE INDEX IF NOT EXISTS dish_set_name_uq_idx
    ON sales.dish_set (lower(setname));

CREATE INDEX IF NOT EXISTS dish_set_item_set_idx
    ON sales.dish_set_item (set_id);

CREATE INDEX IF NOT EXISTS dish_set_item_dish_idx
    ON sales.dish_set_item (dish_id);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_schema = 'sales'
          AND table_name = 'dish_set'
          AND constraint_name = 'dish_set_price_nonnegative_chk'
    ) THEN
        ALTER TABLE sales.dish_set
        ADD CONSTRAINT dish_set_price_nonnegative_chk
        CHECK (price >= 0);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_schema = 'sales'
          AND table_name = 'dish_set'
          AND constraint_name = 'dish_set_first_cost_nonnegative_chk'
    ) THEN
        ALTER TABLE sales.dish_set
        ADD CONSTRAINT dish_set_first_cost_nonnegative_chk
        CHECK (first_cost >= 0);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_schema = 'sales'
          AND table_name = 'dish_set_item'
          AND constraint_name = 'dish_set_item_qty_positive_chk'
    ) THEN
        ALTER TABLE sales.dish_set_item
        ADD CONSTRAINT dish_set_item_qty_positive_chk
        CHECK (qty > 0);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_schema = 'sales'
          AND table_name = 'dish_set_item'
          AND constraint_name = 'dish_set_item_set_fk'
    ) THEN
        ALTER TABLE sales.dish_set_item
        ADD CONSTRAINT dish_set_item_set_fk
        FOREIGN KEY (set_id)
        REFERENCES sales.dish_set(setid)
        ON DELETE CASCADE;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_schema = 'sales'
          AND table_name = 'dish_set_item'
          AND constraint_name = 'dish_set_item_dish_fk'
    ) THEN
        ALTER TABLE sales.dish_set_item
        ADD CONSTRAINT dish_set_item_dish_fk
        FOREIGN KEY (dish_id)
        REFERENCES sales.dish(dishid);
    END IF;
END $$;
