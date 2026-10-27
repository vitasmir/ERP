CREATE TABLE account_invoices (
    id UUID PRIMARY KEY,
    invoice_number VARCHAR(30) NOT NULL UNIQUE,
    partner_name VARCHAR(200) NOT NULL,
    issue_date DATE NOT NULL,
    due_date DATE NOT NULL,
    total_amount NUMERIC(12,2) NOT NULL CHECK (total_amount >= 0),
    paid_amount NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (paid_amount >= 0),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO account_invoices (id, invoice_number, partner_name, issue_date, due_date, total_amount, paid_amount, status) VALUES
    ('60000000-0000-0000-0000-000000000001', 'FV-2026-0018', 'Retail Group a.s.', CURRENT_DATE - 5, CURRENT_DATE + 9, 124580.00, 0, 'OPEN'),
    ('60000000-0000-0000-0000-000000000002', 'FV-2026-0017', 'Distribuce CZ s.r.o.', CURRENT_DATE - 12, CURRENT_DATE - 2, 48600.00, 0, 'OVERDUE'),
    ('60000000-0000-0000-0000-000000000003', 'FV-2026-0016', 'Fresh Foods s.r.o.', CURRENT_DATE - 17, CURRENT_DATE - 3, 76000.00, 76000.00, 'PAID');