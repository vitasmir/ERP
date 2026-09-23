CREATE TABLE inventory_items (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products(id),
    location_name VARCHAR(200) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity >= 0),
    reorder_level INTEGER NOT NULL CHECK (reorder_level >= 0),
    unit_cost NUMERIC(12,2) NOT NULL CHECK (unit_cost >= 0),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO inventory_items (id, product_id, location_name, quantity, reorder_level, unit_cost) VALUES
    ('a0000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', 'Centrální sklad Brno', 78420, 95000, 82.00),
    ('a0000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000001', 'Praha Vinohrady', 21350, 18000, 82.00),
    ('a0000000-0000-0000-0000-000000000003', '20000000-0000-0000-0000-000000000001', 'Brno Heršpice', 12600, 22000, 82.00);