ALTER TABLE role_definitions
    ADD COLUMN can_delete BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE role_definitions
SET can_delete = TRUE
WHERE name = 'Administrátor';