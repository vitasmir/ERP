ALTER TABLE inventory_items
    ADD COLUMN ordered_from_central INTEGER NOT NULL DEFAULT 0
        CHECK (ordered_from_central >= 0);