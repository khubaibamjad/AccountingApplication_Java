-- Product sold or bought in a purchase/sale entry (required for those types by the app).
ALTER TABLE journal_entries ADD COLUMN product VARCHAR(150);
