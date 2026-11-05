package com.office.application.ui;

import com.office.application.db.DatabaseManager;
import com.office.application.report.ReportService;
import com.office.application.repository.AccountRepository;
import com.office.application.repository.JournalRepository;
import com.office.application.repository.PartyRepository;
import com.office.application.repository.sqlite.SqliteAccountRepository;
import com.office.application.repository.sqlite.SqliteJournalRepository;
import com.office.application.repository.sqlite.SqlitePartyRepository;
import com.office.application.security.AuditLog;
import com.office.application.security.AuthService;
import com.office.application.service.*;

/** Wires every object together once (what Spring's @Autowired would do for you). */
public final class AppContext {

    public final DatabaseManager db;
    public final AccountService accounts;
    public final PartyService parties;
    public final JournalService journal;
    public final TransactionService transactions;
    public final ReportService reports;
    public final AuditLog audit;
    public final AuthService auth;

    public AppContext(DatabaseManager db) {
        this.db = db;
        AccountRepository accountRepo = new SqliteAccountRepository(db);
        PartyRepository partyRepo = new SqlitePartyRepository(db);
        JournalRepository journalRepo = new SqliteJournalRepository(db);

        this.audit = new AuditLog(db);
        this.auth = new AuthService(db, audit);
        this.accounts = new AccountService(accountRepo, partyRepo, audit);
        this.parties = new PartyService(partyRepo, accountRepo, audit);
        this.journal = new JournalService(journalRepo, accountRepo, audit);
        this.reports = new ReportService(journalRepo, accountRepo, partyRepo);
        this.transactions = new TransactionService(journal, reports);
    }
}
