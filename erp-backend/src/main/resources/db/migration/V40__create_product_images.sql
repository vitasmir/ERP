CREATE TABLE product_images (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    image_url VARCHAR(1000) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX product_images_one_active
    ON product_images(product_id)
    WHERE active;

INSERT INTO product_images (id, product_id, image_url, active, sort_order)
SELECT gen_random_uuid(), id, image_url, TRUE, 0
FROM products
WHERE image_url IS NOT NULL AND trim(image_url) <> '';