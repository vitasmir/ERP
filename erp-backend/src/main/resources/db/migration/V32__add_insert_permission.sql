ALTER TABLE role_definitions
    ADD COLUMN can_insert BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE role_definitions
SET can_insert = TRUE
WHERE name = 'Administrátor';