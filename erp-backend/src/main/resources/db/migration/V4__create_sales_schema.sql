CREATE TABLE sales_orders (
    id UUID PRIMARY KEY,
    order_number VARCHAR(30) NOT NULL UNIQUE,
    customer_name VARCHAR(200) NOT NULL,
    order_date DATE NOT NULL,
    delivery_date DATE NOT NULL,
    total_amount NUMERIC(12,2) NOT NULL CHECK (total_amount >= 0),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO sales_orders (id, order_number, customer_name, order_date, delivery_date, total_amount, status) VALUES
    ('80000000-0000-0000-0000-000000000001', 'NAB-2026-0042', 'Retail Group a.s.', CURRENT_DATE - 2, CURRENT_DATE + 5, 248500.00, 'QUOTE'),
    ('80000000-0000-0000-0000-000000000002', 'NAB-2026-0041', 'Fresh Foods s.r.o.', CURRENT_DATE - 1, CURRENT_DATE + 12, 76400.00, 'QUOTE'),
    ('80000000-0000-0000-0000-000000000003', 'OBJ-2026-0178', 'Distribuce CZ s.r.o.', CURRENT_DATE - 6, CURRENT_DATE + 2, 159900.00, 'CONFIRMED');