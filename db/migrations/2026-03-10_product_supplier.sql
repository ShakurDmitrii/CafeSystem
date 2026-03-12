CREATE TABLE IF NOT EXISTS sales.product_supplier (
    product_id  INT NOT NULL REFERENCES sales.product(productid) ON DELETE CASCADE,
    supplier_id INT NOT NULL REFERENCES sales.supplier(supplierid) ON DELETE CASCADE,
    PRIMARY KEY (product_id, supplier_id)
);

INSERT INTO sales.product_supplier (product_id, supplier_id)
SELECT p.productid, p.supplierid
FROM sales.product p
WHERE p.supplierid IS NOT NULL
ON CONFLICT DO NOTHING;
