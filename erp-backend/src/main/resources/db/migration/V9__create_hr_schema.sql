CREATE TABLE employees (
    id UUID PRIMARY KEY,
    full_name VARCHAR(200) NOT NULL,
    team_name VARCHAR(100) NOT NULL,
    job_title VARCHAR(150) NOT NULL,
    employment_start_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO employees (id, full_name, team_name, job_title, employment_start_date, status) VALUES
    ('d0000000-0000-0000-0000-000000000001', 'Petra Nováková', 'Nákup', 'Vedoucí nákupu', CURRENT_DATE - 420, 'ACTIVE'),
    ('d0000000-0000-0000-0000-000000000002', 'Tomáš Marek', 'Logistika', 'Koordinátor skladu', CURRENT_DATE - 290, 'ACTIVE'),
    ('d0000000-0000-0000-0000-000000000003', 'Eva Horáková', 'Prodej', 'Vedoucí prodejny', CURRENT_DATE - 180, 'ACTIVE'),
    ('d0000000-0000-0000-0000-000000000004', 'Martin Dvořák', 'Výroba', 'Mistr výroby', CURRENT_DATE + 7, 'ONBOARDING');