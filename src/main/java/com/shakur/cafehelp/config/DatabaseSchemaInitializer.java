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
    }
}
