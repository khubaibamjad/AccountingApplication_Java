-- Covering indexes: balance/report queries read only these indexes, never the full tables.
CREATE INDEX IF NOT EXISTS idx_lines_entry_cover ON journal_lines (entry_id, account_id, debit, credit);
CREATE INDEX IF NOT EXISTS idx_lines_account_cover ON journal_lines (account_id, debit, credit);
CREATE INDEX IF NOT EXISTS idx_entries_date_cover ON journal_entries (entry_date, id);
DROP INDEX IF EXISTS idx_lines_entry;
DROP INDEX IF EXISTS idx_lines_account;

-- Change counter: bumped by every insert/update/delete of journal lines, so cached report data
-- can never be stale (also works if two copies of the app share the file).
CREATE TABLE IF NOT EXISTS ledger_version (id INTEGER PRIMARY KEY CHECK (id = 1), version INTEGER NOT NULL);
INSERT OR IGNORE INTO ledger_version (id, version) VALUES (1, 0);
CREATE TRIGGER IF NOT EXISTS trg_lines_ins AFTER INSERT ON journal_lines BEGIN UPDATE ledger_version SET version = version + 1 WHERE id = 1; END;
CREATE TRIGGER IF NOT EXISTS trg_lines_del AFTER DELETE ON journal_lines BEGIN UPDATE ledger_version SET version = version + 1 WHERE id = 1; END;
CREATE TRIGGER IF NOT EXISTS trg_lines_upd AFTER UPDATE ON journal_lines BEGIN UPDATE ledger_version SET version = version + 1 WHERE id = 1; END;
CREATE TRIGGER IF NOT EXISTS trg_entries_upd AFTER UPDATE OF entry_date ON journal_entries BEGIN UPDATE ledger_version SET version = version + 1 WHERE id = 1; END;
