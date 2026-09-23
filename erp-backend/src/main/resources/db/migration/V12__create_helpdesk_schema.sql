CREATE TABLE helpdesk_tickets (
    id UUID PRIMARY KEY,
    ticket_number VARCHAR(30) NOT NULL UNIQUE,
    subject VARCHAR(250) NOT NULL,
    requester_name VARCHAR(200) NOT NULL,
    assigned_team VARCHAR(100) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    due_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO helpdesk_tickets (id, ticket_number, subject, requester_name, assigned_team, priority, due_at, status) VALUES
    ('11000000-0000-0000-0000-000000000001', 'HD-2026-0187', 'Tisk štítků na příjmu zboží nefunguje', 'Tomáš Marek', 'IT podpora', 'HIGH', CURRENT_TIMESTAMP + INTERVAL '2 hours', 'OPEN'),
    ('11000000-0000-0000-0000-000000000002', 'HD-2026-0186', 'Chybějící oprávnění pro objednávky', 'Petra Nováková', 'ERP podpora', 'MEDIUM', CURRENT_TIMESTAMP + INTERVAL '1 day', 'IN_PROGRESS'),
    ('11000000-0000-0000-0000-000000000003', 'HD-2026-0185', 'Nastavení nové pokladny Praha', 'Eva Horáková', 'IT podpora', 'LOW', CURRENT_TIMESTAMP + INTERVAL '3 days', 'OPEN'),
    ('11000000-0000-0000-0000-000000000004', 'HD-2026-0184', 'Synchronizace cen promo kampaně', 'Martin Dvořák', 'ERP podpora', 'HIGH', CURRENT_TIMESTAMP - INTERVAL '1 hour', 'RESOLVED');