CREATE TABLE system_users (
    id UUID PRIMARY KEY,
    full_name VARCHAR(200) NOT NULL,
    role_name VARCHAR(120) NOT NULL,
    company_name VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL,
    last_access_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO system_users (id, full_name, role_name, company_name, status, last_access_at) VALUES
    ('16000000-0000-0000-0000-000000000001', 'Jan Král', 'Administrátor', 'Retail Group a.s.', 'ACTIVE', CURRENT_TIMESTAMP - INTERVAL '18 minutes'),
    ('16000000-0000-0000-0000-000000000002', 'Petra Nováková', 'Nákupčí', 'Retail Group a.s.', 'ACTIVE', CURRENT_TIMESTAMP - INTERVAL '1 hour 43 minutes'),
    ('16000000-0000-0000-0000-000000000003', 'Tomáš Marek', 'Logistika', 'Distribuce CZ', 'ACTIVE', CURRENT_TIMESTAMP - INTERVAL '18 hours');