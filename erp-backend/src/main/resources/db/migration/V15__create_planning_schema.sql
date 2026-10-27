CREATE TABLE planning_shifts (
    id UUID PRIMARY KEY,
    employee_name VARCHAR(200),
    role_name VARCHAR(150) NOT NULL,
    department VARCHAR(150) NOT NULL,
    start_at TIMESTAMP NOT NULL,
    end_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO planning_shifts (id, employee_name, role_name, department, start_at, end_at, status) VALUES
    ('14000000-0000-0000-0000-000000000001', 'Anna Novotná', 'Vedoucí směny', 'Prodejna Praha', CURRENT_DATE + INTERVAL '1 day 08:00', CURRENT_DATE + INTERVAL '1 day 16:00', 'PUBLISHED'),
    ('14000000-0000-0000-0000-000000000002', 'Petr Svoboda', 'Skladník', 'Sklad Praha', CURRENT_DATE + INTERVAL '1 day 06:00', CURRENT_DATE + INTERVAL '1 day 14:00', 'PUBLISHED'),
    ('14000000-0000-0000-0000-000000000003', NULL, 'Obchodní poradce', 'Prodejna Praha', CURRENT_DATE + INTERVAL '1 day 10:00', CURRENT_DATE + INTERVAL '1 day 18:00', 'DRAFT'),
    ('14000000-0000-0000-0000-000000000004', 'Lucie Křížová', 'Zákaznická péče', 'Backoffice', CURRENT_DATE + INTERVAL '2 day 08:30', CURRENT_DATE + INTERVAL '2 day 16:30', 'PUBLISHED');