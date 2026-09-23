CREATE TABLE documents (
    id UUID PRIMARY KEY,
    title VARCHAR(250) NOT NULL,
    category VARCHAR(100) NOT NULL,
    owner_name VARCHAR(200) NOT NULL,
    reference_code VARCHAR(60) NOT NULL,
    updated_on DATE NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO documents (id, title, category, owner_name, reference_code, updated_on, status) VALUES
    ('e0000000-0000-0000-0000-000000000001', 'Smlouva o dodávce masa 2027', 'Smlouvy', 'Petra Nováková', 'DOC-2026-0042', CURRENT_DATE - 1, 'PENDING_APPROVAL'),
    ('e0000000-0000-0000-0000-000000000002', 'Postup příjmu zboží na sklad', 'Procesy', 'Tomáš Marek', 'DOC-2026-0041', CURRENT_DATE - 3, 'APPROVED'),
    ('e0000000-0000-0000-0000-000000000003', 'Vyhodnocení promo kampaně - září', 'Reporty', 'Eva Horáková', 'DOC-2026-0040', CURRENT_DATE, 'PENDING_APPROVAL');