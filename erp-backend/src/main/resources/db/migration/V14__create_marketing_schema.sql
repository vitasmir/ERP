CREATE TABLE marketing_campaigns (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    audience VARCHAR(200) NOT NULL,
    channel VARCHAR(30) NOT NULL,
    owner_name VARCHAR(200) NOT NULL,
    budget NUMERIC(12, 2) NOT NULL,
    spent NUMERIC(12, 2) NOT NULL,
    lead_count INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    planned_start_date DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO marketing_campaigns (id, name, audience, channel, owner_name, budget, spent, lead_count, status, planned_start_date) VALUES
    ('13000000-0000-0000-0000-000000000001', 'Podzimní nabídka pro stálé zákazníky', 'Aktivní zákazníci B2B', 'EMAIL', 'Klára Procházková', 45000.00, 12450.00, 186, 'RUNNING', CURRENT_DATE - 7),
    ('13000000-0000-0000-0000-000000000002', 'Představení nového katalogu', 'Potenciální zákazníci', 'SOCIAL', 'Adam Veselý', 30000.00, 0.00, 0, 'PLANNED', CURRENT_DATE + 3),
    ('13000000-0000-0000-0000-000000000003', 'Reaktivace neaktivních klientů', 'Zákazníci bez nákupu 90 dní', 'EMAIL', 'Klára Procházková', 18000.00, 18000.00, 74, 'COMPLETED', CURRENT_DATE - 30),
    ('13000000-0000-0000-0000-000000000004', 'Lokální akce Praha', 'Maloobchod Praha', 'EVENT', 'Adam Veselý', 25000.00, 5300.00, 42, 'RUNNING', CURRENT_DATE - 2);