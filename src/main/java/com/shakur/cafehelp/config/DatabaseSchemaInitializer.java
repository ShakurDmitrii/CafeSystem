package com.shakur.cafehelp.config;

import jakarta.annotation.PostConstruct;
import org.jooq.DSLContext;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

@Component
@DependsOn("databaseMigrationRunner")
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
        ensureShiftInventorySnapshotSchema();
        ensureInventoryShiftReportSchema();
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
        if (!isIdentityColumn("person", "personid")) {
            dsl.execute("""
                ALTER TABLE sales.person
                ALTER COLUMN personid SET DEFAULT nextval('sales.person_personid_seq')
                """);
            dsl.execute("""
                ALTER SEQUENCE sales.person_personid_seq
                OWNED BY sales.person.personid
                """);
        }
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
        if (!isIdentityColumn("user_account", "id")) {
            dsl.execute("""
                ALTER TABLE sales.user_account
                ALTER COLUMN id SET DEFAULT nextval('sales.user_account_id_seq')
                """);
            dsl.execute("""
                ALTER SEQUENCE sales.user_account_id_seq
                OWNED BY sales.user_account.id
                """);
        }
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
        if (!isIdentityColumn("shiftperson", "shiftpersonid")) {
            dsl.execute("""
                ALTER TABLE sales.shiftperson
                ALTER COLUMN shiftpersonid SET DEFAULT nextval('sales.shiftperson_shiftpersonid_seq')
                """);
            dsl.execute("""
                ALTER SEQUENCE sales.shiftperson_shiftpersonid_seq
                OWNED BY sales.shiftperson.shiftpersonid
                """);
        }
    }

    private boolean isIdentityColumn(String tableName, String columnName) {
        var result = dsl.fetchOne(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM information_schema.columns
                    WHERE table_schema = 'sales'
                      AND table_name = ?
                      AND column_name = ?
                      AND is_identity = 'YES'
                )
                """,
                tableName,
                columnName
        );
        return result != null && Boolean.TRUE.equals(result.get(0, Boolean.class));
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

    private void ensureInventoryShiftReportSchema() {
        dsl.execute("""
            CREATE TABLE IF NOT EXISTS sales.inventory_shift_report (
                id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                warehouse_id integer NOT NULL,
                shift_id integer NOT NULL,
                snapshot_available boolean NOT NULL DEFAULT false,
                created_at timestamp NOT NULL DEFAULT now(),
                updated_at timestamp NOT NULL DEFAULT now(),
                applied_at timestamp NULL
            )
            """);

        dsl.execute("""
            CREATE TABLE IF NOT EXISTS sales.inventory_shift_report_line (
                id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                report_id integer NOT NULL,
                product_id integer NOT NULL,
                product_name varchar(255) NOT NULL,
                unit varchar(50) NOT NULL DEFAULT 'g',
                opening_qty double precision NOT NULL DEFAULT 0,
                movement_in_qty double precision NOT NULL DEFAULT 0,
                movement_out_qty double precision NOT NULL DEFAULT 0,
                movement_net_qty double precision NOT NULL DEFAULT 0,
                sold_qty double precision NOT NULL DEFAULT 0,
                expected_qty double precision NOT NULL DEFAULT 0,
                system_qty double precision NOT NULL DEFAULT 0,
                actual_qty double precision NULL,
                discrepancy_qty double precision NULL,
                shortage_qty double precision NOT NULL DEFAULT 0,
                shortage_flag boolean NOT NULL DEFAULT false,
                sort_order integer NOT NULL DEFAULT 0
            )
            """);

        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report
            ADD COLUMN IF NOT EXISTS warehouse_id integer
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report
            ADD COLUMN IF NOT EXISTS shift_id integer
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report
            ADD COLUMN IF NOT EXISTS snapshot_available boolean
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report
            ADD COLUMN IF NOT EXISTS created_at timestamp
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report
            ADD COLUMN IF NOT EXISTS updated_at timestamp
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report
            ADD COLUMN IF NOT EXISTS applied_at timestamp
            """);
        dsl.execute("""
            UPDATE sales.inventory_shift_report
            SET created_at = now()
            WHERE created_at IS NULL
            """);
        dsl.execute("""
            UPDATE sales.inventory_shift_report
            SET updated_at = COALESCE(updated_at, created_at, now())
            WHERE updated_at IS NULL
            """);
        dsl.execute("""
            UPDATE sales.inventory_shift_report
            SET snapshot_available = false
            WHERE snapshot_available IS NULL
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report
            ALTER COLUMN warehouse_id SET NOT NULL
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report
            ALTER COLUMN shift_id SET NOT NULL
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report
            ALTER COLUMN snapshot_available SET DEFAULT false
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report
            ALTER COLUMN snapshot_available SET NOT NULL
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report
            ALTER COLUMN created_at SET DEFAULT now()
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report
            ALTER COLUMN created_at SET NOT NULL
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report
            ALTER COLUMN updated_at SET DEFAULT now()
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report
            ALTER COLUMN updated_at SET NOT NULL
            """);

        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report_line
            ADD COLUMN IF NOT EXISTS report_id integer
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report_line
            ADD COLUMN IF NOT EXISTS product_id integer
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report_line
            ADD COLUMN IF NOT EXISTS product_name varchar(255)
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report_line
            ADD COLUMN IF NOT EXISTS unit varchar(50)
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report_line
            ADD COLUMN IF NOT EXISTS opening_qty double precision
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report_line
            ADD COLUMN IF NOT EXISTS movement_in_qty double precision
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report_line
            ADD COLUMN IF NOT EXISTS movement_out_qty double precision
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report_line
            ADD COLUMN IF NOT EXISTS movement_net_qty double precision
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report_line
            ADD COLUMN IF NOT EXISTS sold_qty double precision
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report_line
            ADD COLUMN IF NOT EXISTS expected_qty double precision
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report_line
            ADD COLUMN IF NOT EXISTS system_qty double precision
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report_line
            ADD COLUMN IF NOT EXISTS actual_qty double precision
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report_line
            ADD COLUMN IF NOT EXISTS discrepancy_qty double precision
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report_line
            ADD COLUMN IF NOT EXISTS shortage_qty double precision
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report_line
            ADD COLUMN IF NOT EXISTS shortage_flag boolean
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report_line
            ADD COLUMN IF NOT EXISTS sort_order integer
            """);
        dsl.execute("""
            UPDATE sales.inventory_shift_report_line
            SET unit = 'g'
            WHERE unit IS NULL OR unit = ''
            """);
        dsl.execute("""
            UPDATE sales.inventory_shift_report_line
            SET sold_qty = 0
            WHERE sold_qty IS NULL
            """);
        dsl.execute("""
            UPDATE sales.inventory_shift_report_line
            SET opening_qty = 0
            WHERE opening_qty IS NULL
            """);
        dsl.execute("""
            UPDATE sales.inventory_shift_report_line
            SET movement_in_qty = 0
            WHERE movement_in_qty IS NULL
            """);
        dsl.execute("""
            UPDATE sales.inventory_shift_report_line
            SET movement_out_qty = 0
            WHERE movement_out_qty IS NULL
            """);
        dsl.execute("""
            UPDATE sales.inventory_shift_report_line
            SET movement_net_qty = 0
            WHERE movement_net_qty IS NULL
            """);
        dsl.execute("""
            UPDATE sales.inventory_shift_report_line
            SET expected_qty = 0
            WHERE expected_qty IS NULL
            """);
        dsl.execute("""
            UPDATE sales.inventory_shift_report_line
            SET system_qty = 0
            WHERE system_qty IS NULL
            """);
        dsl.execute("""
            UPDATE sales.inventory_shift_report_line
            SET shortage_qty = 0
            WHERE shortage_qty IS NULL
            """);
        dsl.execute("""
            UPDATE sales.inventory_shift_report_line
            SET shortage_flag = false
            WHERE shortage_flag IS NULL
            """);
        dsl.execute("""
            UPDATE sales.inventory_shift_report_line
            SET sort_order = 0
            WHERE sort_order IS NULL
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report_line
            ALTER COLUMN report_id SET NOT NULL
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report_line
            ALTER COLUMN product_id SET NOT NULL
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report_line
            ALTER COLUMN product_name SET NOT NULL
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report_line
            ALTER COLUMN unit SET DEFAULT 'g'
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report_line
            ALTER COLUMN unit SET NOT NULL
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report_line
            ALTER COLUMN opening_qty SET DEFAULT 0
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report_line
            ALTER COLUMN opening_qty SET NOT NULL
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report_line
            ALTER COLUMN movement_in_qty SET DEFAULT 0
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report_line
            ALTER COLUMN movement_in_qty SET NOT NULL
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report_line
            ALTER COLUMN movement_out_qty SET DEFAULT 0
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report_line
            ALTER COLUMN movement_out_qty SET NOT NULL
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report_line
            ALTER COLUMN movement_net_qty SET DEFAULT 0
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report_line
            ALTER COLUMN movement_net_qty SET NOT NULL
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report_line
            ALTER COLUMN sold_qty SET DEFAULT 0
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report_line
            ALTER COLUMN sold_qty SET NOT NULL
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report_line
            ALTER COLUMN expected_qty SET DEFAULT 0
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report_line
            ALTER COLUMN expected_qty SET NOT NULL
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report_line
            ALTER COLUMN system_qty SET DEFAULT 0
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report_line
            ALTER COLUMN system_qty SET NOT NULL
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report_line
            ALTER COLUMN shortage_qty SET DEFAULT 0
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report_line
            ALTER COLUMN shortage_qty SET NOT NULL
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report_line
            ALTER COLUMN shortage_flag SET DEFAULT false
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report_line
            ALTER COLUMN shortage_flag SET NOT NULL
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report_line
            ALTER COLUMN sort_order SET DEFAULT 0
            """);
        dsl.execute("""
            ALTER TABLE sales.inventory_shift_report_line
            ALTER COLUMN sort_order SET NOT NULL
            """);

        dsl.execute("""
            CREATE UNIQUE INDEX IF NOT EXISTS inventory_shift_report_wh_shift_uq_idx
            ON sales.inventory_shift_report (warehouse_id, shift_id)
            """);
        dsl.execute("""
            CREATE UNIQUE INDEX IF NOT EXISTS inventory_shift_report_line_report_product_uq_idx
            ON sales.inventory_shift_report_line (report_id, product_id)
            """);
        dsl.execute("""
            CREATE INDEX IF NOT EXISTS inventory_shift_report_line_report_sort_idx
            ON sales.inventory_shift_report_line (report_id, sort_order)
            """);

        dsl.execute("""
            DO $$
            BEGIN
                IF NOT EXISTS (
                    SELECT 1
                    FROM information_schema.table_constraints
                    WHERE constraint_schema = 'sales'
                      AND table_name = 'inventory_shift_report'
                      AND constraint_name = 'inventory_shift_report_warehouse_fk'
                ) THEN
                    ALTER TABLE sales.inventory_shift_report
                    ADD CONSTRAINT inventory_shift_report_warehouse_fk
                    FOREIGN KEY (warehouse_id)
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
                      AND table_name = 'inventory_shift_report'
                      AND constraint_name = 'inventory_shift_report_shift_fk'
                ) THEN
                    ALTER TABLE sales.inventory_shift_report
                    ADD CONSTRAINT inventory_shift_report_shift_fk
                    FOREIGN KEY (shift_id)
                    REFERENCES sales.shift(id)
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
                      AND table_name = 'inventory_shift_report_line'
                      AND constraint_name = 'inventory_shift_report_line_report_fk'
                ) THEN
                    ALTER TABLE sales.inventory_shift_report_line
                    ADD CONSTRAINT inventory_shift_report_line_report_fk
                    FOREIGN KEY (report_id)
                    REFERENCES sales.inventory_shift_report(id)
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
                      AND table_name = 'inventory_shift_report_line'
                      AND constraint_name = 'inventory_shift_report_line_product_fk'
                ) THEN
                    ALTER TABLE sales.inventory_shift_report_line
                    ADD CONSTRAINT inventory_shift_report_line_product_fk
                    FOREIGN KEY (product_id)
                    REFERENCES sales.product(productid);
                END IF;
            END $$;
            """);
    }

    private void ensureShiftInventorySnapshotSchema() {
        dsl.execute("""
            CREATE TABLE IF NOT EXISTS sales.shift_inventory_snapshot (
                id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                shift_id integer NOT NULL,
                warehouse_id integer NOT NULL,
                product_id integer NOT NULL,
                quantity double precision NOT NULL DEFAULT 0,
                created_at timestamp NOT NULL DEFAULT now()
            )
            """);

        dsl.execute("""
            ALTER TABLE sales.shift_inventory_snapshot
            ADD COLUMN IF NOT EXISTS shift_id integer
            """);
        dsl.execute("""
            ALTER TABLE sales.shift_inventory_snapshot
            ADD COLUMN IF NOT EXISTS warehouse_id integer
            """);
        dsl.execute("""
            ALTER TABLE sales.shift_inventory_snapshot
            ADD COLUMN IF NOT EXISTS product_id integer
            """);
        dsl.execute("""
            ALTER TABLE sales.shift_inventory_snapshot
            ADD COLUMN IF NOT EXISTS quantity double precision
            """);
        dsl.execute("""
            ALTER TABLE sales.shift_inventory_snapshot
            ADD COLUMN IF NOT EXISTS created_at timestamp
            """);
        dsl.execute("""
            UPDATE sales.shift_inventory_snapshot
            SET quantity = 0
            WHERE quantity IS NULL
            """);
        dsl.execute("""
            UPDATE sales.shift_inventory_snapshot
            SET created_at = now()
            WHERE created_at IS NULL
            """);
        dsl.execute("""
            ALTER TABLE sales.shift_inventory_snapshot
            ALTER COLUMN shift_id SET NOT NULL
            """);
        dsl.execute("""
            ALTER TABLE sales.shift_inventory_snapshot
            ALTER COLUMN warehouse_id SET NOT NULL
            """);
        dsl.execute("""
            ALTER TABLE sales.shift_inventory_snapshot
            ALTER COLUMN product_id SET NOT NULL
            """);
        dsl.execute("""
            ALTER TABLE sales.shift_inventory_snapshot
            ALTER COLUMN quantity SET DEFAULT 0
            """);
        dsl.execute("""
            ALTER TABLE sales.shift_inventory_snapshot
            ALTER COLUMN quantity SET NOT NULL
            """);
        dsl.execute("""
            ALTER TABLE sales.shift_inventory_snapshot
            ALTER COLUMN created_at SET DEFAULT now()
            """);
        dsl.execute("""
            ALTER TABLE sales.shift_inventory_snapshot
            ALTER COLUMN created_at SET NOT NULL
            """);

        dsl.execute("""
            CREATE UNIQUE INDEX IF NOT EXISTS shift_inventory_snapshot_shift_wh_product_uq_idx
            ON sales.shift_inventory_snapshot (shift_id, warehouse_id, product_id)
            """);
        dsl.execute("""
            CREATE INDEX IF NOT EXISTS shift_inventory_snapshot_shift_wh_idx
            ON sales.shift_inventory_snapshot (shift_id, warehouse_id)
            """);

        dsl.execute("""
            DO $$
            BEGIN
                IF NOT EXISTS (
                    SELECT 1
                    FROM information_schema.table_constraints
                    WHERE constraint_schema = 'sales'
                      AND table_name = 'shift_inventory_snapshot'
                      AND constraint_name = 'shift_inventory_snapshot_shift_fk'
                ) THEN
                    ALTER TABLE sales.shift_inventory_snapshot
                    ADD CONSTRAINT shift_inventory_snapshot_shift_fk
                    FOREIGN KEY (shift_id)
                    REFERENCES sales.shift(id)
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
                      AND table_name = 'shift_inventory_snapshot'
                      AND constraint_name = 'shift_inventory_snapshot_warehouse_fk'
                ) THEN
                    ALTER TABLE sales.shift_inventory_snapshot
                    ADD CONSTRAINT shift_inventory_snapshot_warehouse_fk
                    FOREIGN KEY (warehouse_id)
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
                      AND table_name = 'shift_inventory_snapshot'
                      AND constraint_name = 'shift_inventory_snapshot_product_fk'
                ) THEN
                    ALTER TABLE sales.shift_inventory_snapshot
                    ADD CONSTRAINT shift_inventory_snapshot_product_fk
                    FOREIGN KEY (product_id)
                    REFERENCES sales.product(productid);
                END IF;
            END $$;
            """);
    }
}
