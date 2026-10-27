CREATE TABLE crm_leads (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    customer_name VARCHAR(200) NOT NULL,
    expected_revenue NUMERIC(12,2) NOT NULL CHECK (expected_revenue >= 0),
    probability INTEGER NOT NULL CHECK (probability BETWEEN 0 AND 100),
    stage VARCHAR(20) NOT NULL,
    expected_close_date DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO crm_leads (id, name, customer_name, expected_revenue, probability, stage, expected_close_date) VALUES
    ('70000000-0000-0000-0000-000000000001', 'Rámcová smlouva 2027', 'Retail Group a.s.', 850000.00, 70, 'PROPOSAL', CURRENT_DATE + 14),
    ('70000000-0000-0000-0000-000000000002', 'Modernizace skladů', 'Distribuce CZ s.r.o.', 420000.00, 45, 'QUALIFIED', CURRENT_DATE + 28),
    ('70000000-0000-0000-0000-000000000003', 'Dodávka promo sortimentu', 'Fresh Foods s.r.o.', 176000.00, 20, 'NEW', CURRENT_DATE + 45);