package com.shakur.cafehelp.config;

import jakarta.annotation.PostConstruct;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

@Component
public class DatabaseSchemaInitializer {
    private final DSLContext dsl;

    public DatabaseSchemaInitializer(DSLContext dsl) {
        this.dsl = dsl;
    }

    @PostConstruct
    public void ensureOrderDeliveryColumns() {
        dsl.execute("""
            ALTER TABLE sales."order"
            ADD COLUMN IF NOT EXISTS delivery_phone varchar(50)
            """);
        dsl.execute("""
            ALTER TABLE sales."order"
            ADD COLUMN IF NOT EXISTS delivery_address varchar(255)
            """);
        dsl.execute("""
            ALTER TABLE sales."order"
            ADD COLUMN IF NOT EXISTS payment_type varchar(20)
            """);
        dsl.execute("""
            ALTER TABLE sales."order"
            ADD COLUMN IF NOT EXISTS is_paid boolean
            """);
        dsl.execute("""
            UPDATE sales."order"
            SET payment_type = 'cash'
            WHERE payment_type IS NULL
            """);
        dsl.execute("""
            UPDATE sales."order"
            SET is_paid = true
            WHERE is_paid IS NULL
            """);

        // Legacy schema had UNIQUE(personcode) on shift, which blocks creating
        // more than one shift per employee ever. Keep only one OPEN shift.
        dsl.execute("""
            DO $$
            BEGIN
                IF EXISTS (
                    SELECT 1
                    FROM information_schema.table_constraints
                    WHERE constraint_schema = 'sales'
                      AND table_name = 'shift'
                      AND constraint_name = 'shift_unique'
                ) THEN
                    ALTER TABLE sales.shift DROP CONSTRAINT shift_unique;
                END IF;
            END $$;
            """);

        dsl.execute("""
            CREATE UNIQUE INDEX IF NOT EXISTS shift_open_unique_person_idx
            ON sales.shift (personcode)
            WHERE endtime IS NULL AND personcode IS NOT NULL
            """);
    }
}
