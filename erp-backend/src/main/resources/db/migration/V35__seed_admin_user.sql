INSERT INTO employees (id, full_name, team_name, job_title, employment_start_date, status)
VALUES ('d0000000-0000-0000-0000-000000000005', 'Administrator', 'Správa systému', 'Administrátor', CURRENT_DATE, 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO role_definitions (id, name, initial, description, can_read, can_edit, can_manage, color)
VALUES ('25000000-0000-0000-0000-000000000004', 'Administrator', 'A', 'Plný přístup do všech modulů a nastavení.', TRUE, TRUE, TRUE, '#D9ED62')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_module_permissions (role_id, module_key)
SELECT '25000000-0000-0000-0000-000000000004', module_key
FROM erp_modules
ON CONFLICT DO NOTHING;

INSERT INTO system_users (
    id, full_name, role_name, company_name, status, last_access_at,
    employee_id, username, password_hash, color
)
VALUES (
    '16000000-0000-0000-0000-000000000004', 'Administrator', 'Administrator',
    'Retail Group a.s.', 'ACTIVE', NULL,
    'd0000000-0000-0000-0000-000000000005', 'admin',
    '210000:RVJQLWFkbWluLXNlZWQh:xam0vK91L4kcJQFZYoZRtQfQpofJY8wsPR6P0PiiGzw=',
    '#DCE9D7'
)
ON CONFLICT (username) DO UPDATE SET
    full_name = EXCLUDED.full_name,
    role_name = EXCLUDED.role_name,
    company_name = EXCLUDED.company_name,
    status = EXCLUDED.status,
    employee_id = EXCLUDED.employee_id,
    password_hash = EXCLUDED.password_hash,
    color = EXCLUDED.color;