CREATE TABLE role_definitions (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE,
    initial VARCHAR(1) NOT NULL,
    description VARCHAR(240) NOT NULL,
    can_read BOOLEAN NOT NULL DEFAULT TRUE,
    can_edit BOOLEAN NOT NULL DEFAULT FALSE,
    can_manage BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO role_definitions (id, name, initial, description, can_read, can_edit, can_manage) VALUES
    ('25000000-0000-0000-0000-000000000001', 'Administrátor', 'A', 'Plný přístup do všech modulů a nastavení.', TRUE, TRUE, TRUE),
    ('25000000-0000-0000-0000-000000000002', 'Nákupčí', 'N', 'Produkty, dodavatelé a promo kampaně.', TRUE, TRUE, FALSE),
    ('25000000-0000-0000-0000-000000000003', 'Logistika', 'L', 'Sklady, příjem a distribuce zboží.', TRUE, TRUE, FALSE);
