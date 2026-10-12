CREATE TABLE purchase_receipts (
    id UUID PRIMARY KEY,
    purchase_order_id UUID NOT NULL REFERENCES purchase_orders(id),
    request_signature TEXT NOT NULL,
    actor_id UUID REFERENCES system_users(id) ON DELETE SET NULL,
    actor_name VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_purchase_receipts_order ON purchase_receipts(purchase_order_id);

CREATE TABLE purchase_receipt_lines (
    id BIGSERIAL PRIMARY KEY,
    receipt_id UUID NOT NULL REFERENCES purchase_receipts(id),
    purchase_order_line_id UUID REFERENCES purchase_order_lines(id),
    product_id UUID NOT NULL REFERENCES products(id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(12,2) NOT NULL CHECK (unit_price >= 0)
);

CREATE INDEX idx_purchase_receipt_lines_receipt ON purchase_receipt_lines(receipt_id);
