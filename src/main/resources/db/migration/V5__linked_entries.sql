-- A payment taken together with a sale/purchase points to that sale/purchase,
-- so deleting the sale also deletes its payment.
ALTER TABLE journal_entries ADD COLUMN linked_entry_id VARCHAR(36);
CREATE INDEX IF NOT EXISTS idx_entries_linked ON journal_entries (linked_entry_id);
