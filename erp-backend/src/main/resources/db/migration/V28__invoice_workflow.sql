ALTER TABLE account_invoices
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN sales_order_id UUID UNIQUE REFERENCES sales_orders(id),
    ADD CONSTRAINT invoice_payment_limit CHECK (paid_amount <= total_amount);

CREATE TABLE invoice_lines (
    invoice_id UUID NOT NULL REFERENCES account_invoices(id) ON DELETE CASCADE,
    line_index INTEGER NOT NULL,
    description VARCHAR(240) NOT NULL,
    quantity NUMERIC(12,3) NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(12,2) NOT NULL CHECK (unit_price >= 0),
    vat_rate NUMERIC(5,2) NOT NULL CHECK (vat_rate >= 0 AND vat_rate <= 100),
    PRIMARY KEY (invoice_id, line_index)
);

INSERT INTO invoice_lines (invoice_id, line_index, description, quantity, unit_price, vat_rate)
SELECT id, 0, 'Imported opening balance', 1, total_amount, 0 FROM account_invoices;

CREATE TABLE invoice_payments (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL REFERENCES account_invoices(id),
    amount NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    paid_on DATE NOT NULL,
    reference VARCHAR(160) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO invoice_payments (id, invoice_id, amount, paid_on, reference)
SELECT id, id, paid_amount, issue_date, 'Imported opening balance'
FROM account_invoices WHERE paid_amount > 0;

CREATE TABLE invoice_events (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL REFERENCES account_invoices(id),
    action VARCHAR(60) NOT NULL,
    details VARCHAR(1000) NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE invoice_attachments (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL REFERENCES account_invoices(id),
    filename VARCHAR(240) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    content BYTEA NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);