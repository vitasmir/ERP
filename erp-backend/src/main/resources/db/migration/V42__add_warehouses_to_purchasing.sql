CREATE TABLE warehouses (
    id UUID PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    owner_type VARCHAR(20) NOT NULL CHECK (owner_type IN ('COMPANY', 'SUPPLIER')),
    supplier_id UUID REFERENCES suppliers(id),
    active BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO warehouses (id, code, name, owner_type, supplier_id) VALUES
    ('b0000000-0000-0000-0000-000000000001', 'COMP-BRNO', 'Centrální sklad Brno', 'COMPANY', NULL),
    ('b0000000-0000-0000-0000-000000000002', 'COMP-PRAHA', 'Praha Vinohrady', 'COMPANY', NULL),
    ('b0000000-0000-0000-0000-000000000003', 'COMP-HERS', 'Brno Heršpice', 'COMPANY', NULL),
    ('b0000000-0000-0000-0000-000000000004', 'SUP-FRESH', 'Fresh Foods s.r.o. - expedice', 'SUPPLIER', '10000000-0000-0000-0000-000000000001');

ALTER TABLE inventory_items
    ADD COLUMN warehouse_id UUID REFERENCES warehouses(id);

UPDATE inventory_items SET warehouse_id = 'b0000000-0000-0000-0000-000000000001'
    WHERE location_name = 'Centrální sklad Brno';
UPDATE inventory_items SET warehouse_id = 'b0000000-0000-0000-0000-000000000002'
    WHERE location_name = 'Praha Vinohrady';
UPDATE inventory_items SET warehouse_id = 'b0000000-0000-0000-0000-000000000003'
    WHERE location_name = 'Brno Heršpice';

ALTER TABLE purchase_orders
    ADD COLUMN source_warehouse_id UUID REFERENCES warehouses(id),
    ADD COLUMN destination_warehouse_id UUID REFERENCES warehouses(id),
    ADD COLUMN product_id UUID REFERENCES products(id),
    ADD COLUMN quantity INTEGER CHECK (quantity IS NULL OR quantity > 0),
    ADD COLUMN received_quantity INTEGER NOT NULL DEFAULT 0 CHECK (received_quantity >= 0);

UPDATE purchase_orders SET destination_warehouse_id = 'b0000000-0000-0000-0000-000000000001'
    WHERE destination_warehouse_id IS NULL;