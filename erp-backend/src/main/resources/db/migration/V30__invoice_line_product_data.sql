ALTER TABLE invoice_lines
    ADD COLUMN product_id UUID,
    ADD COLUMN image_url VARCHAR(1000);