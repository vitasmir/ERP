CREATE TABLE purchase_orders (
    id UUID PRIMARY KEY,
    order_number VARCHAR(30) NOT NULL UNIQUE,
    supplier_name VARCHAR(200) NOT NULL,
    requested_on DATE NOT NULL,
    expected_delivery_date DATE NOT NULL,
    total_amount NUMERIC(12,2) NOT NULL CHECK (total_amount >= 0),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO purchase_orders (id, order_number, supplier_name, requested_on, expected_delivery_date, total_amount, status) VALUES
    ('90000000-0000-0000-0000-000000000001', 'POZ-2026-0031', 'Masna Novák s.r.o.', CURRENT_DATE - 1, CURRENT_DATE + 4, 132800.00, 'REQUESTED'),
    ('90000000-0000-0000-0000-000000000002', 'POZ-2026-0030', 'Pekárna U Mlýna a.s.', CURRENT_DATE - 3, CURRENT_DATE + 2, 48700.00, 'REQUESTED'),
    ('90000000-0000-0000-0000-000000000003', 'OBJ-N-2026-0094', 'Nápoje Central s.r.o.', CURRENT_DATE - 7, CURRENT_DATE + 1, 216400.00, 'ORDERED');