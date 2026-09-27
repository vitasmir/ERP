INSERT INTO erp_modules (module_key, display_name, sort_order) VALUES
    ('companies', 'Společnosti', 5),
    ('users', 'Uživatelé', 6),
    ('roles', 'Role a oprávnění', 7),
    ('settings', 'Nastavení', 8),
    ('auth', 'Přihlášení', 9),
    ('promo-campaigns', 'Promo kampaně', 140)
ON CONFLICT (module_key) DO NOTHING;

INSERT INTO role_module_permissions (role_id, module_key)
SELECT rd.id, module.module_key
FROM role_definitions rd
CROSS JOIN erp_modules module
WHERE lower(rd.name) IN ('administrátor', 'administrator', 'admin')
ON CONFLICT DO NOTHING;