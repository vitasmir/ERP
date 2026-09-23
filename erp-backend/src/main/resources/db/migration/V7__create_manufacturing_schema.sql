CREATE TABLE manufacturing_orders (
    id UUID PRIMARY KEY,
    order_number VARCHAR(30) NOT NULL UNIQUE,
    product_name VARCHAR(200) NOT NULL,
    work_center VARCHAR(200) NOT NULL,
    planned_quantity INTEGER NOT NULL CHECK (planned_quantity > 0),
    completed_quantity INTEGER NOT NULL DEFAULT 0 CHECK (completed_quantity >= 0),
    planned_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO manufacturing_orders (id, order_number, product_name, work_center, planned_quantity, completed_quantity, planned_date, status) VALUES
    ('b0000000-0000-0000-0000-000000000001', 'VP-2026-0028', 'Grilovací balení krkovice', 'Balení Brno', 18000, 6400, CURRENT_DATE, 'IN_PROGRESS'),
    ('b0000000-0000-0000-0000-000000000002', 'VP-2026-0029', 'Marinovaná krkovice', 'Marinování Brno', 12000, 0, CURRENT_DATE + 2, 'PLANNED'),
    ('b0000000-0000-0000-0000-000000000003', 'VP-2026-0027', 'Chlazené maso - porce', 'Porcování Praha', 9500, 9500, CURRENT_DATE - 1, 'COMPLETED');