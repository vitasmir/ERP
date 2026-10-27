CREATE TABLE product_categories (
    id UUID PRIMARY KEY,
    parent_id UUID REFERENCES product_categories(id),
    name VARCHAR(160) NOT NULL,
    slug VARCHAR(180) NOT NULL UNIQUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

ALTER TABLE products
    ADD COLUMN description TEXT NOT NULL DEFAULT '',
    ADD COLUMN price NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN category_id UUID REFERENCES product_categories(id),
    ADD COLUMN image_url VARCHAR(500),
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE homepage_settings (
    id UUID PRIMARY KEY,
    design VARCHAR(40) NOT NULL,
    headline VARCHAR(200) NOT NULL,
    subheadline VARCHAR(500) NOT NULL,
    text_x NUMERIC(5,2) NOT NULL DEFAULT 50,
    text_y NUMERIC(5,2) NOT NULL DEFAULT 50,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE delivery_options (
    id UUID PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    label VARCHAR(100) NOT NULL,
    preparation_days INTEGER NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO product_categories (id, parent_id, name, slug, sort_order) VALUES
    ('60000000-0000-0000-0000-000000000001', NULL, 'Potraviny', 'potraviny', 10),
    ('60000000-0000-0000-0000-000000000002', '60000000-0000-0000-0000-000000000001', 'Maso', 'maso', 10);

UPDATE products
SET description = 'Čerstvé maso pro každodenní vaření.', price = 189.90,
    category_id = '60000000-0000-0000-0000-000000000002'
WHERE sku = 'PORK-NECK-01';

INSERT INTO homepage_settings (id, design, headline, subheadline, text_x, text_y)
VALUES ('70000000-0000-0000-0000-000000000001', 'BOTANICAL', 'Dobré jídlo začíná výběrem',
        'Objevte čerstvé produkty z vašich oblíbených prodejen.', 18, 34);

INSERT INTO delivery_options (id, code, label, preparation_days) VALUES
    ('80000000-0000-0000-0000-000000000001', 'HOME', 'Doručení domů', 1),
    ('80000000-0000-0000-0000-000000000002', 'BOX', 'Doručení do boxu', 2);