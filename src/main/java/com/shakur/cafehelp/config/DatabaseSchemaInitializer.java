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
        ensurePersonIdentity();
        ensurePersonArchiveColumn();
        ensureShiftPersonIdentity();
        ensureUserAccountIdentity();
        ensurePreparationSchema();
        ensureDishSetSchema();
        ensureOrderDishSetSchema();
        fixPackAmountOvercount();

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

    private void ensureOrderDishSetSchema() {
        dsl.execute("""
            ALTER TABLE sales.orderdish
            ADD COLUMN IF NOT EXISTS set_id integer
            """);
        dsl.execute("""
            ALTER TABLE sales.orderdish
            ALTER COLUMN dishid DROP NOT NULL
            """);
        dsl.execute("""
            CREATE INDEX IF NOT EXISTS orderdish_set_idx
            ON sales.orderdish (set_id)
            """);
        dsl.execute("""
            DO $$
            BEGIN
                IF NOT EXISTS (
                    SELECT 1
                    FROM information_schema.table_constraints
                    WHERE constraint_schema = 'sales'
                      AND table_name = 'orderdish'
                      AND constraint_name = 'orderdish_set_fk'
                ) THEN
                    ALTER TABLE sales.orderdish
                    ADD CONSTRAINT orderdish_set_fk
                    FOREIGN KEY (set_id)
                    REFERENCES sales.dish_set(setid);
                END IF;
            END $$;
            """);
        dsl.execute("""
            DO $$
            BEGIN
                IF NOT EXISTS (
                    SELECT 1
                    FROM information_schema.table_constraints
                    WHERE constraint_schema = 'sales'
                      AND table_name = 'orderdish'
                      AND constraint_name = 'orderdish_item_target_chk'
                ) THEN
                    ALTER TABLE sales.orderdish
                    ADD CONSTRAINT orderdish_item_target_chk
                    CHECK (
                        (CASE WHEN dishid IS NOT NULL THEN 1 ELSE 0 END) +
                        (CASE WHEN set_id IS NOT NULL THEN 1 ELSE 0 END) = 1
                    );
                END IF;
            END $$;
            """);
    }

    private void ensurePersonArchiveColumn() {
        dsl.execute("""
            ALTER TABLE sales.person
            ADD COLUMN IF NOT EXISTS archived boolean
            """);
        dsl.execute("""
            UPDATE sales.person
            SET archived = false
            WHERE archived IS NULL
            """);
        dsl.execute("""
            ALTER TABLE sales.person
            ALTER COLUMN archived SET DEFAULT false
            """);
        dsl.execute("""
            ALTER TABLE sales.person
            ALTER COLUMN archived SET NOT NULL
            """);
    }

    private void ensurePersonIdentity() {
        dsl.execute("""
            CREATE SEQUENCE IF NOT EXISTS sales.person_personid_seq
            """);
        dsl.execute("""
            SELECT setval(
                'sales.person_personid_seq',
                COALESCE((SELECT MAX(personid) FROM sales.person), 0) + 1,
                false
            )
            """);
        dsl.execute("""
            ALTER TABLE sales.person
            ALTER COLUMN personid SET DEFAULT nextval('sales.person_personid_seq')
            """);
        dsl.execute("""
            ALTER SEQUENCE sales.person_personid_seq
            OWNED BY sales.person.personid
            """);
    }

    private void ensureUserAccountIdentity() {
        dsl.execute("""
            CREATE SEQUENCE IF NOT EXISTS sales.user_account_id_seq
            """);
        dsl.execute("""
            SELECT setval(
                'sales.user_account_id_seq',
                COALESCE((SELECT MAX(id) FROM sales.user_account), 0) + 1,
                false
            )
            """);
        dsl.execute("""
            ALTER TABLE sales.user_account
            ALTER COLUMN id SET DEFAULT nextval('sales.user_account_id_seq')
            """);
        dsl.execute("""
            ALTER SEQUENCE sales.user_account_id_seq
            OWNED BY sales.user_account.id
            """);
    }

    private void ensureShiftPersonIdentity() {
        dsl.execute("""
            CREATE SEQUENCE IF NOT EXISTS sales.shiftperson_shiftpersonid_seq
            """);
        dsl.execute("""
            SELECT setval(
                'sales.shiftperson_shiftpersonid_seq',
                COALESCE((SELECT MAX(shiftpersonid) FROM sales.shiftperson), 0) + 1,
                false
            )
            """);
        dsl.execute("""
            ALTER TABLE sales.shiftperson
            ALTER COLUMN shiftpersonid SET DEFAULT nextval('sales.shiftperson_shiftpersonid_seq')
            """);
        dsl.execute("""
            ALTER SEQUENCE sales.shiftperson_shiftpersonid_seq
            OWNED BY sales.shiftperson.shiftpersonid
            """);
    }

    private void fixPackAmountOvercount() {
        dsl.execute("""
            UPDATE sales.inventory_document_lines AS l
            SET line_total = l.unit_price * (l.qty / p.unit_factor)
            FROM sales.inventory_documents AS d,
                 sales.product AS p
            WHERE d.id = l.document_id
              AND p.productid = l.product_id
              AND d.doc_type = 'receipt'
              AND p.unit = p.base_unit
              AND p.unit_factor > 1
              AND l.unit_price IS NOT NULL
              AND l.qty IS NOT NULL
              AND l.line_total IS NOT NULL
              AND l.line_total = l.unit_price * l.qty
            """);

        dsl.execute("""
            UPDATE sales.stock_movements AS sm
            SET amount = sm.unit_cost * (sm.qty_in / p.unit_factor)
            FROM sales.inventory_documents AS d,
                 sales.product AS p
            WHERE d.id = sm.document_id
              AND p.productid = sm.product_id
              AND d.doc_type = 'receipt'
              AND p.unit = p.base_unit
              AND p.unit_factor > 1
              AND sm.unit_cost IS NOT NULL
              AND sm.qty_in IS NOT NULL
              AND sm.qty_in > 0
              AND sm.amount IS NOT NULL
              AND sm.amount = sm.unit_cost * sm.qty_in
            """);
    }

    private void ensurePreparationSchema() {
        dsl.execute("""
            CREATE TABLE IF NOT EXISTS sales.preparation (
                preparationid integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                preparationname varchar(255) NOT NULL,
                output_weight double precision NOT NULL DEFAULT 1
            )
            """);
        dsl.execute("""
            CREATE TABLE IF NOT EXISTS sales.preparationwarehouse (
                warehouseid integer NOT NULL,
                preparationid integer NOT NULL,
                preparationwarehouseid integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                quantity double precision NOT NULL DEFAULT 0
            )
            """);

        dsl.execute("""
            ALTER TABLE sales.preparation
            ADD COLUMN IF NOT EXISTS preparationname varchar(255)
            """);
        dsl.execute("""
            ALTER TABLE sales.preparation
            ADD COLUMN IF NOT EXISTS output_weight double precision
            """);
        dsl.execute("""
            ALTER TABLE sales.preparation
            ALTER COLUMN output_weight SET DEFAULT 1
            """);
        dsl.execute("""
            UPDATE sales.preparation
            SET output_weight = 1
            WHERE output_weight IS NULL OR output_weight <= 0
            """);
        dsl.execute("""
            ALTER TABLE sales.preparation
            ALTER COLUMN preparationname SET NOT NULL
            """);
        dsl.execute("""
            ALTER TABLE sales.preparation
            ALTER COLUMN output_weight SET NOT NULL
            """);
        dsl.execute("""
            ALTER TABLE sales.preparationwarehouse
            ADD COLUMN IF NOT EXISTS warehouseid integer
            """);
        dsl.execute("""
            ALTER TABLE sales.preparationwarehouse
            ADD COLUMN IF NOT EXISTS preparationid integer
            """);
        dsl.execute("""
            ALTER TABLE sales.preparationwarehouse
            ADD COLUMN IF NOT EXISTS quantity double precision
            """);
        dsl.execute("""
            UPDATE sales.preparationwarehouse
            SET quantity = 0
            WHERE quantity IS NULL
            """);
        dsl.execute("""
            ALTER TABLE sales.preparationwarehouse
            ALTER COLUMN quantity SET DEFAULT 0
            """);
        dsl.execute("""
            ALTER TABLE sales.preparationwarehouse
            ALTER COLUMN warehouseid SET NOT NULL
            """);
        dsl.execute("""
            ALTER TABLE sales.preparationwarehouse
            ALTER COLUMN preparationid SET NOT NULL
            """);
        dsl.execute("""
            ALTER TABLE sales.preparationwarehouse
            ALTER COLUMN quantity SET NOT NULL
            """);

        dsl.execute("""
            ALTER TABLE sales.techproduct
            ADD COLUMN IF NOT EXISTS preparation_id integer
            """);
        dsl.execute("""
            ALTER TABLE sales.techproduct
            ADD COLUMN IF NOT EXISTS ingredient_preparation_id integer
            """);
        dsl.execute("""
            ALTER TABLE sales.techproduct
            ALTER COLUMN "DishId" DROP NOT NULL
            """);
        dsl.execute("""
            ALTER TABLE sales.techproduct
            ALTER COLUMN productid DROP NOT NULL
            """);

        dsl.execute("""
            CREATE UNIQUE INDEX IF NOT EXISTS preparation_name_uq_idx
            ON sales.preparation (lower(preparationname))
            """);
        dsl.execute("""
            CREATE INDEX IF NOT EXISTS preparationwarehouse_wh_idx
            ON sales.preparationwarehouse (warehouseid)
            """);
        dsl.execute("""
            CREATE INDEX IF NOT EXISTS preparationwarehouse_prep_idx
            ON sales.preparationwarehouse (preparationid)
            """);
        dsl.execute("""
            CREATE INDEX IF NOT EXISTS techproduct_preparation_owner_idx
            ON sales.techproduct (preparation_id)
            """);
        dsl.execute("""
            CREATE INDEX IF NOT EXISTS techproduct_preparation_ingredient_idx
            ON sales.techproduct (ingredient_preparation_id)
            """);

        dsl.execute("""
            DO $$
            BEGIN
                IF NOT EXISTS (
                    SELECT 1
                    FROM information_schema.table_constraints
                    WHERE constraint_schema = 'sales'
                      AND table_name = 'preparation'
                      AND constraint_name = 'preparation_output_weight_positive_chk'
                ) THEN
                    ALTER TABLE sales.preparation
                    ADD CONSTRAINT preparation_output_weight_positive_chk
                    CHECK (output_weight > 0);
                END IF;
            END $$;
            """);
        dsl.execute("""
            DO $$
            BEGIN
                IF NOT EXISTS (
                    SELECT 1
                    FROM information_schema.table_constraints
                    WHERE constraint_schema = 'sales'
                      AND table_name = 'preparationwarehouse'
                      AND constraint_name = 'preparationwarehouse_warehouse_fk'
                ) THEN
                    ALTER TABLE sales.preparationwarehouse
                    ADD CONSTRAINT preparationwarehouse_warehouse_fk
                    FOREIGN KEY (warehouseid)
                    REFERENCES sales.warehouse(warehouseid)
                    ON DELETE CASCADE;
                END IF;
            END $$;
            """);
        dsl.execute("""
            DO $$
            BEGIN
                IF NOT EXISTS (
                    SELECT 1
                    FROM information_schema.table_constraints
                    WHERE constraint_schema = 'sales'
                      AND table_name = 'preparationwarehouse'
                      AND constraint_name = 'preparationwarehouse_preparation_fk'
                ) THEN
                    ALTER TABLE sales.preparationwarehouse
                    ADD CONSTRAINT preparationwarehouse_preparation_fk
                    FOREIGN KEY (preparationid)
                    REFERENCES sales.preparation(preparationid)
                    ON DELETE CASCADE;
                END IF;
            END $$;
            """);
        dsl.execute("""
            DO $$
            BEGIN
                IF NOT EXISTS (
                    SELECT 1
                    FROM information_schema.table_constraints
                    WHERE constraint_schema = 'sales'
                      AND table_name = 'preparationwarehouse'
                      AND constraint_name = 'preparationwarehouse_quantity_nonnegative_chk'
                ) THEN
                    ALTER TABLE sales.preparationwarehouse
                    ADD CONSTRAINT preparationwarehouse_quantity_nonnegative_chk
                    CHECK (quantity >= 0);
                END IF;
            END $$;
            """);

        dsl.execute("""
            DO $$
            BEGIN
                IF NOT EXISTS (
                    SELECT 1
                    FROM information_schema.table_constraints
                    WHERE constraint_schema = 'sales'
                      AND table_name = 'techproduct'
                      AND constraint_name = 'techproduct_preparation_owner_fk'
                ) THEN
                    ALTER TABLE sales.techproduct
                    ADD CONSTRAINT techproduct_preparation_owner_fk
                    FOREIGN KEY (preparation_id)
                    REFERENCES sales.preparation(preparationid)
                    ON DELETE CASCADE;
                END IF;
            END $$;
            """);

        dsl.execute("""
            DO $$
            BEGIN
                IF NOT EXISTS (
                    SELECT 1
                    FROM information_schema.table_constraints
                    WHERE constraint_schema = 'sales'
                      AND table_name = 'techproduct'
                      AND constraint_name = 'techproduct_ingredient_preparation_fk'
                ) THEN
                    ALTER TABLE sales.techproduct
                    ADD CONSTRAINT techproduct_ingredient_preparation_fk
                    FOREIGN KEY (ingredient_preparation_id)
                    REFERENCES sales.preparation(preparationid);
                END IF;
            END $$;
            """);

        dsl.execute("""
            DO $$
            BEGIN
                IF NOT EXISTS (
                    SELECT 1
                    FROM information_schema.table_constraints
                    WHERE constraint_schema = 'sales'
                      AND table_name = 'techproduct'
                      AND constraint_name = 'techproduct_owner_chk'
                ) THEN
                    ALTER TABLE sales.techproduct
                    ADD CONSTRAINT techproduct_owner_chk
                    CHECK (
                        (CASE WHEN "DishId" IS NOT NULL THEN 1 ELSE 0 END) +
                        (CASE WHEN preparation_id IS NOT NULL THEN 1 ELSE 0 END) = 1
                    );
                END IF;
            END $$;
            """);

        dsl.execute("""
            DO $$
            BEGIN
                IF NOT EXISTS (
                    SELECT 1
                    FROM information_schema.table_constraints
                    WHERE constraint_schema = 'sales'
                      AND table_name = 'techproduct'
                      AND constraint_name = 'techproduct_ingredient_chk'
                ) THEN
                    ALTER TABLE sales.techproduct
                    ADD CONSTRAINT techproduct_ingredient_chk
                    CHECK (
                        (CASE WHEN productid IS NOT NULL THEN 1 ELSE 0 END) +
                        (CASE WHEN ingredient_preparation_id IS NOT NULL THEN 1 ELSE 0 END) = 1
                    );
                END IF;
            END $$;
            """);
    }

    private void ensureDishSetSchema() {
        dsl.execute("""
            CREATE TABLE IF NOT EXISTS sales.dish_set (
                setid integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                setname varchar(255) NOT NULL,
                price double precision NOT NULL DEFAULT 0,
                first_cost double precision NOT NULL DEFAULT 0,
                image_url varchar(1000)
            )
            """);

        dsl.execute("""
            CREATE TABLE IF NOT EXISTS sales.dish_set_item (
                set_item_id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                set_id integer NOT NULL,
                dish_id integer NOT NULL,
                qty integer NOT NULL DEFAULT 1
            )
            """);

        dsl.execute("""
            ALTER TABLE sales.dish_set
            ADD COLUMN IF NOT EXISTS setname varchar(255)
            """);
        dsl.execute("""
            ALTER TABLE sales.dish_set
            ADD COLUMN IF NOT EXISTS price double precision
            """);
        dsl.execute("""
            ALTER TABLE sales.dish_set
            ADD COLUMN IF NOT EXISTS first_cost double precision
            """);
        dsl.execute("""
            ALTER TABLE sales.dish_set
            ADD COLUMN IF NOT EXISTS image_url varchar(1000)
            """);
        dsl.execute("""
            UPDATE sales.dish_set
            SET price = 0
            WHERE price IS NULL
            """);
        dsl.execute("""
            UPDATE sales.dish_set
            SET first_cost = 0
            WHERE first_cost IS NULL
            """);
        dsl.execute("""
            ALTER TABLE sales.dish_set
            ALTER COLUMN setname SET NOT NULL
            """);
        dsl.execute("""
            ALTER TABLE sales.dish_set
            ALTER COLUMN price SET DEFAULT 0
            """);
        dsl.execute("""
            ALTER TABLE sales.dish_set
            ALTER COLUMN price SET NOT NULL
            """);
        dsl.execute("""
            ALTER TABLE sales.dish_set
            ALTER COLUMN first_cost SET DEFAULT 0
            """);
        dsl.execute("""
            ALTER TABLE sales.dish_set
            ALTER COLUMN first_cost SET NOT NULL
            """);

        dsl.execute("""
            ALTER TABLE sales.dish_set_item
            ADD COLUMN IF NOT EXISTS set_id integer
            """);
        dsl.execute("""
            ALTER TABLE sales.dish_set_item
            ADD COLUMN IF NOT EXISTS dish_id integer
            """);
        dsl.execute("""
            ALTER TABLE sales.dish_set_item
            ADD COLUMN IF NOT EXISTS qty integer
            """);
        dsl.execute("""
            UPDATE sales.dish_set_item
            SET qty = 1
            WHERE qty IS NULL OR qty <= 0
            """);
        dsl.execute("""
            ALTER TABLE sales.dish_set_item
            ALTER COLUMN set_id SET NOT NULL
            """);
        dsl.execute("""
            ALTER TABLE sales.dish_set_item
            ALTER COLUMN dish_id SET NOT NULL
            """);
        dsl.execute("""
            ALTER TABLE sales.dish_set_item
            ALTER COLUMN qty SET DEFAULT 1
            """);
        dsl.execute("""
            ALTER TABLE sales.dish_set_item
            ALTER COLUMN qty SET NOT NULL
            """);

        dsl.execute("""
            CREATE UNIQUE INDEX IF NOT EXISTS dish_set_name_uq_idx
            ON sales.dish_set (lower(setname))
            """);
        dsl.execute("""
            CREATE INDEX IF NOT EXISTS dish_set_item_set_idx
            ON sales.dish_set_item (set_id)
            """);
        dsl.execute("""
            CREATE INDEX IF NOT EXISTS dish_set_item_dish_idx
            ON sales.dish_set_item (dish_id)
            """);

        dsl.execute("""
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
            """);
        dsl.execute("""
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
            """);
        dsl.execute("""
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
            """);
        dsl.execute("""
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
            """);
        dsl.execute("""
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
            """);
    }
}
