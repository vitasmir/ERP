CREATE TABLE pos_transactions (
    id UUID PRIMARY KEY,
    receipt_number VARCHAR(30) NOT NULL UNIQUE,
    store_name VARCHAR(200) NOT NULL,
    opened_at TIMESTAMP NOT NULL,
    item_count INTEGER NOT NULL CHECK (item_count > 0),
    total_amount NUMERIC(12,2) NOT NULL CHECK (total_amount >= 0),
    payment_method VARCHAR(20),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO pos_transactions (id, receipt_number, store_name, opened_at, item_count, total_amount, payment_method, status) VALUES
    ('c0000000-0000-0000-0000-000000000001', 'PKL-2026-09123', 'Brno Heršpice', CURRENT_TIMESTAMP - INTERVAL '8 minutes', 5, 1287.50, NULL, 'OPEN'),
    ('c0000000-0000-0000-0000-000000000002', 'PKL-2026-09122', 'Praha Vinohrady', CURRENT_TIMESTAMP - INTERVAL '19 minutes', 3, 649.90, NULL, 'OPEN'),
    ('c0000000-0000-0000-0000-000000000003', 'PKL-2026-09121', 'Brno Heršpice', CURRENT_TIMESTAMP - INTERVAL '34 minutes', 7, 2345.70, 'CARD', 'PAID');