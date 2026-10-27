ALTER TABLE purchase_orders
    ADD COLUMN supplier_id UUID REFERENCES suppliers(id);

UPDATE purchase_orders purchase
SET supplier_id = supplier.id
FROM suppliers supplier
WHERE LOWER(BTRIM(purchase.supplier_name)) = LOWER(BTRIM(supplier.name));
