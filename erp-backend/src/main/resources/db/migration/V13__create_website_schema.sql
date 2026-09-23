CREATE TABLE website_pages (
    id UUID PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    slug VARCHAR(200) NOT NULL UNIQUE,
    content_type VARCHAR(30) NOT NULL,
    owner_name VARCHAR(200) NOT NULL,
    monthly_visits INTEGER NOT NULL,
    has_contact_form BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO website_pages (id, title, slug, content_type, owner_name, monthly_visits, has_contact_form, status, updated_at) VALUES
    ('12000000-0000-0000-0000-000000000001', 'Úvodní stránka', '/', 'LANDING', 'Marketing', 24860, TRUE, 'PUBLISHED', CURRENT_TIMESTAMP - INTERVAL '1 day'),
    ('12000000-0000-0000-0000-000000000002', 'Katalog produktů', '/katalog', 'CATALOG', 'Obchod', 17320, FALSE, 'PUBLISHED', CURRENT_TIMESTAMP - INTERVAL '3 days'),
    ('12000000-0000-0000-0000-000000000003', 'Podzimní nabídka', '/podzimni-nabidka', 'CAMPAIGN', 'Marketing', 0, TRUE, 'DRAFT', CURRENT_TIMESTAMP - INTERVAL '2 hours'),
    ('12000000-0000-0000-0000-000000000004', 'Kontaktní formulář', '/kontakt', 'CONTENT', 'Zákaznická péče', 4380, TRUE, 'PUBLISHED', CURRENT_TIMESTAMP - INTERVAL '6 days');