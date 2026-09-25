-- Moving weighted-average valuation for the actual warehouse balance.
-- Historical sales and closed shifts are intentionally not recalculated.

-- Legacy code could create more than one row for the same warehouse/product.
-- Consolidate those rows before enforcing the business key.
WITH aggregated AS (
    SELECT warehouseid,
           productid,
           MIN(productwarehouseid) AS keeper_id,
           COALESCE(SUM(quantity), 0) AS total_quantity
    FROM sales.productwarehouse
    WHERE warehouseid IS NOT NULL AND productid IS NOT NULL
    GROUP BY warehouseid, productid
), updated AS (
    UPDATE sales.productwarehouse pw
    SET quantity = aggregated.total_quantity
    FROM aggregated
    WHERE pw.productwarehouseid = aggregated.keeper_id
    RETURNING pw.productwarehouseid
)
DELETE FROM sales.productwarehouse pw
USING aggregated
WHERE pw.warehouseid = aggregated.warehouseid
  AND pw.productid = aggregated.productid
  AND pw.productwarehouseid <> aggregated.keeper_id;

ALTER TABLE sales.productwarehouse
    ADD COLUMN IF NOT EXISTS inventory_value NUMERIC(19, 6) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS average_unit_cost NUMERIC(19, 6) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS valuation_initialized BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS valuation_updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now();

-- Cut-over opening valuation: current physical quantity at the card's default
-- base-unit price. It is explicit opening data, not a rewrite of old documents.
UPDATE sales.productwarehouse pw
SET inventory_value = ROUND(
        GREATEST(COALESCE(pw.quantity, 0), 0)::NUMERIC
        * CASE
              WHEN COALESCE(p.unit_factor, 1) > 0
                  THEN COALESCE(p.productprice, 0) / p.unit_factor
              ELSE COALESCE(p.productprice, 0)
          END,
        6
    ),
    average_unit_cost = CASE
        WHEN COALESCE(pw.quantity, 0) > 0 THEN ROUND(
            CASE
                WHEN COALESCE(p.unit_factor, 1) > 0
                    THEN COALESCE(p.productprice, 0) / p.unit_factor
                ELSE COALESCE(p.productprice, 0)
            END,
            6
        )
        ELSE 0
    END,
    valuation_initialized = TRUE,
    valuation_updated_at = now()
FROM sales.product p
WHERE p.productid = pw.productid
  AND pw.valuation_initialized = FALSE;

CREATE UNIQUE INDEX IF NOT EXISTS ux_productwarehouse_warehouse_product
    ON sales.productwarehouse (warehouseid, productid)
    WHERE warehouseid IS NOT NULL AND productid IS NOT NULL;

WITH aggregated AS (
    SELECT warehouseid,
           preparationid,
           MIN(preparationwarehouseid) AS keeper_id,
           COALESCE(SUM(quantity), 0) AS total_quantity
    FROM sales.preparationwarehouse
    GROUP BY warehouseid, preparationid
), updated AS (
    UPDATE sales.preparationwarehouse pw
    SET quantity = aggregated.total_quantity
    FROM aggregated
    WHERE pw.preparationwarehouseid = aggregated.keeper_id
    RETURNING pw.preparationwarehouseid
)
DELETE FROM sales.preparationwarehouse pw
USING aggregated
WHERE pw.warehouseid = aggregated.warehouseid
  AND pw.preparationid = aggregated.preparationid
  AND pw.preparationwarehouseid <> aggregated.keeper_id;

ALTER TABLE sales.preparationwarehouse
    ADD COLUMN IF NOT EXISTS inventory_value NUMERIC(19, 6) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS average_unit_cost NUMERIC(19, 6) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS valuation_initialized BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS valuation_updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now();

CREATE UNIQUE INDEX IF NOT EXISTS ux_preparationwarehouse_warehouse_preparation
    ON sales.preparationwarehouse (warehouseid, preparationid);

CREATE TABLE IF NOT EXISTS sales.preparation_stock_movements (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    movement_date TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    warehouse_id INTEGER NOT NULL REFERENCES sales.warehouse(warehouseid),
    preparation_id INTEGER NOT NULL REFERENCES sales.preparation(preparationid),
    qty_in NUMERIC(19, 6) NOT NULL DEFAULT 0,
    qty_out NUMERIC(19, 6) NOT NULL DEFAULT 0,
    unit_cost NUMERIC(19, 6) NOT NULL DEFAULT 0,
    amount NUMERIC(19, 6) NOT NULL DEFAULT 0,
    movement_type VARCHAR(40) NOT NULL,
    source_type VARCHAR(40),
    source_id INTEGER,
    created_by VARCHAR(100),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_preparation_stock_movements_source
    ON sales.preparation_stock_movements (source_type, source_id);
CREATE INDEX IF NOT EXISTS ix_preparation_stock_movements_balance_date
    ON sales.preparation_stock_movements (warehouse_id, preparation_id, movement_date, id);

ALTER TABLE sales.stock_movements
    ALTER COLUMN document_id DROP NOT NULL,
    ALTER COLUMN unit_cost TYPE NUMERIC(19, 6),
    ALTER COLUMN amount TYPE NUMERIC(19, 6),
    ADD COLUMN IF NOT EXISTS movement_type VARCHAR(40),
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(40),
    ADD COLUMN IF NOT EXISTS source_id INTEGER,
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS reason TEXT;

UPDATE sales.stock_movements sm
SET movement_type = COALESCE(sm.movement_type,
        CASE
            WHEN COALESCE(sm.qty_in, 0) > 0 THEN 'receipt'
            WHEN COALESCE(sm.qty_out, 0) > 0 THEN 'writeoff'
            ELSE 'adjustment'
        END),
    source_type = COALESCE(sm.source_type, 'inventory_document'),
    source_id = COALESCE(sm.source_id, sm.document_id)
WHERE sm.movement_type IS NULL
   OR sm.source_type IS NULL
   OR sm.source_id IS NULL;

CREATE INDEX IF NOT EXISTS ix_stock_movements_source
    ON sales.stock_movements (source_type, source_id);
CREATE INDEX IF NOT EXISTS ix_stock_movements_warehouse_product_date
    ON sales.stock_movements (warehouse_id, product_id, movement_date, id);

-- A supplier link is an offer, not a second ingredient card.
ALTER TABLE sales.product_supplier
    ADD COLUMN IF NOT EXISTS default_price NUMERIC(19, 6),
    ADD COLUMN IF NOT EXISTS purchase_unit VARCHAR(16),
    ADD COLUMN IF NOT EXISTS purchase_unit_factor NUMERIC(19, 6),
    ADD COLUMN IF NOT EXISTS supplier_sku VARCHAR(100),
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS last_price_at TIMESTAMP WITHOUT TIME ZONE;

UPDATE sales.product_supplier ps
SET default_price = COALESCE(ps.default_price, p.productprice),
    purchase_unit = COALESCE(ps.purchase_unit, p.unit),
    purchase_unit_factor = COALESCE(ps.purchase_unit_factor, p.unit_factor)
FROM sales.product p
WHERE p.productid = ps.product_id
  AND p.supplierid = ps.supplier_id;

ALTER TABLE sales.supplier_price_history
    DROP CONSTRAINT IF EXISTS supplier_price_history_unique,
    DROP CONSTRAINT IF EXISTS supplier_price_history_unique_1;

CREATE UNIQUE INDEX IF NOT EXISTS ux_supplier_price_history_active_offer
    ON sales.supplier_price_history (supplier_id, product_id)
    WHERE valid_to IS NULL;
CREATE INDEX IF NOT EXISTS ix_supplier_price_history_offer_date
    ON sales.supplier_price_history (supplier_id, product_id, valid_from DESC, id DESC);

-- Legacy duplicates require an Owner decision and must not be merged automatically.
-- Prevent new duplicates while allowing existing cards to be edited in other fields.
CREATE INDEX IF NOT EXISTS ix_product_normalized_name
    ON sales.product (LOWER(BTRIM(productname)));

CREATE OR REPLACE FUNCTION sales.prevent_duplicate_product_name()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM sales.product existing
        WHERE LOWER(BTRIM(existing.productname)) = LOWER(BTRIM(NEW.productname))
          AND existing.productid <> COALESCE(NEW.productid, -1)
    ) THEN
        RAISE EXCEPTION 'Ingredient with normalized name "%" already exists', NEW.productname
            USING ERRCODE = 'unique_violation';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_product_prevent_duplicate_name ON sales.product;
CREATE TRIGGER trg_product_prevent_duplicate_name
    BEFORE INSERT OR UPDATE OF productname ON sales.product
    FOR EACH ROW
    EXECUTE FUNCTION sales.prevent_duplicate_product_name();
