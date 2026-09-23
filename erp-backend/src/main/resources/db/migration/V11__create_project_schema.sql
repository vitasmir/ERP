CREATE TABLE projects (
    id UUID PRIMARY KEY,
    name VARCHAR(250) NOT NULL,
    owner_name VARCHAR(200) NOT NULL,
    department VARCHAR(100) NOT NULL,
    due_date DATE NOT NULL,
    progress INTEGER NOT NULL CHECK (progress BETWEEN 0 AND 100),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO projects (id, name, owner_name, department, due_date, progress, status) VALUES
    ('f0000000-0000-0000-0000-000000000001', 'Rozšíření chlazeného sortimentu', 'Eva Horáková', 'Prodej', CURRENT_DATE + 9, 72, 'IN_PROGRESS'),
    ('f0000000-0000-0000-0000-000000000002', 'Automatizace příjmu zboží', 'Tomáš Marek', 'Logistika', CURRENT_DATE + 18, 45, 'IN_PROGRESS'),
    ('f0000000-0000-0000-0000-000000000003', 'Příprava vánočního katalogu', 'Petra Nováková', 'Nákup', CURRENT_DATE + 42, 10, 'PLANNED'),
    ('f0000000-0000-0000-0000-000000000004', 'Revize výroby grilovacího sortimentu', 'Martin Dvořák', 'Výroba', CURRENT_DATE - 2, 100, 'COMPLETED');