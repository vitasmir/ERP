CREATE TABLE erp_settings (
    id UUID PRIMARY KEY,
    company_name VARCHAR(200) NOT NULL,
    company_email VARCHAR(200) NOT NULL,
    currency_code VARCHAR(10) NOT NULL,
    timezone VARCHAR(100) NOT NULL,
    fiscal_year_start_month INTEGER NOT NULL,
    default_payment_terms_days INTEGER NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO erp_settings (id, company_name, company_email, currency_code, timezone, fiscal_year_start_month, default_payment_terms_days) VALUES
    ('15000000-0000-0000-0000-000000000001', 'Northstar ERP s.r.o.', 'finance@northstar.example', 'CZK', 'Europe/Prague', 1, 14);