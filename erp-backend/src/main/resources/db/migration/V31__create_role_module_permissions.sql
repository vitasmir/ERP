CREATE TABLE erp_modules (
    module_key VARCHAR(80) PRIMARY KEY,
    display_name VARCHAR(120) NOT NULL,
    sort_order INTEGER NOT NULL
);

CREATE TABLE role_module_permissions (
    role_id UUID NOT NULL REFERENCES role_definitions(id) ON DELETE CASCADE,
    module_key VARCHAR(80) NOT NULL REFERENCES erp_modules(module_key) ON DELETE CASCADE,
    PRIMARY KEY (role_id, module_key)
);

INSERT INTO erp_modules (module_key, display_name, sort_order) VALUES
    ('accounting', 'Účetnictví', 10), ('catalog', 'Katalog', 20), ('crm', 'CRM', 30),
    ('dashboard', 'Přehled', 40), ('documents', 'Dokumenty', 50), ('helpdesk', 'Helpdesk', 60),
    ('hr', 'Lidské zdroje', 70), ('inventory', 'Sklad', 80), ('manufacturing', 'Výroba', 90),
    ('marketing', 'Marketing', 100), ('planning', 'Plánování', 110), ('pos', 'Prodejní místo', 120),
    ('projects', 'Projekty', 130), ('promo-campaigns', 'Promo kampaně', 140), ('purchase', 'Nákup', 150),
    ('sales', 'Prodej', 160), ('website', 'Web', 170);

INSERT INTO role_module_permissions (role_id, module_key)
SELECT '25000000-0000-0000-0000-000000000001', module_key FROM erp_modules;

INSERT INTO role_module_permissions (role_id, module_key) VALUES
    ('25000000-0000-0000-0000-000000000002', 'catalog'),
    ('25000000-0000-0000-0000-000000000002', 'crm'),
    ('25000000-0000-0000-0000-000000000002', 'marketing'),
    ('25000000-0000-0000-0000-000000000002', 'promo-campaigns'),
    ('25000000-0000-0000-0000-000000000002', 'purchase'),
    ('25000000-0000-0000-0000-000000000002', 'sales'),
    ('25000000-0000-0000-0000-000000000003', 'inventory');