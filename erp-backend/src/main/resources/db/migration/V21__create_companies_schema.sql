CREATE TABLE erp_companies (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL UNIQUE,
    company_type VARCHAR(100) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO erp_companies (id, name, company_type, currency, status) VALUES
    ('e0000000-0000-0000-0000-000000000001', 'Retail Group a.s.', 'Centrála', 'CZK', 'ACTIVE'),
    ('e0000000-0000-0000-0000-000000000002', 'Distribuce CZ s.r.o.', 'Logistika', 'CZK', 'ACTIVE'),
    ('e0000000-0000-0000-0000-000000000003', 'Fresh Foods s.r.o.', 'Dodavatel', 'CZK', 'ACTIVE');