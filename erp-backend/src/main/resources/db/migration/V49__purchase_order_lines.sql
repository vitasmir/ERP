CREATE TABLE purchase_order_lines (
    id UUID PRIMARY KEY,
    purchase_order_id UUID NOT NULL REFERENCES purchase_orders(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(12,2) NOT NULL CHECK (unit_price >= 0),
    received_quantity INTEGER NOT NULL DEFAULT 0 CHECK (received_quantity >= 0),
    CONSTRAINT purchase_order_lines_received_not_exceed_quantity CHECK (received_quantity <= quantity)
);

CREATE INDEX idx_purchase_order_lines_order ON purchase_order_lines(purchase_order_id);
