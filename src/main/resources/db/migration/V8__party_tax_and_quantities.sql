-- Registered (NTN) vs unregistered parties. Existing parties become unregistered.
ALTER TABLE parties ADD COLUMN registered INTEGER NOT NULL DEFAULT 0;
ALTER TABLE parties ADD COLUMN ntn VARCHAR(20);
-- Product quantity and rate on purchases/sales (amount = quantity x price per kg).
ALTER TABLE journal_entries ADD COLUMN quantity_kg DECIMAL(15, 3);
ALTER TABLE journal_entries ADD COLUMN price_per_kg DECIMAL(15, 2);
