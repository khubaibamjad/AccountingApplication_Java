CREATE INDEX IF NOT EXISTS idx_entries_date ON journal_entries (entry_date);
CREATE INDEX IF NOT EXISTS idx_lines_account ON journal_lines (account_id);
CREATE INDEX IF NOT EXISTS idx_lines_entry ON journal_lines (entry_id);
