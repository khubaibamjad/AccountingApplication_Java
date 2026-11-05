package com.office.application.service;

import com.office.application.model.JournalEntry;
import com.office.application.model.JournalLine;
import com.office.application.repository.AccountRepository;
import com.office.application.repository.JournalRepository;

import java.math.BigDecimal;

public class JournalService {

    private final JournalRepository journals;
    private final AccountRepository accounts;

    public JournalService(JournalRepository journals, AccountRepository accounts) {
        this.journals = journals;
        this.accounts = accounts;
    }

    public void post(JournalEntry entry) {
        validate(entry);          // check first
        journals.save(entry);     // only save if valid
    }

    private void validate(JournalEntry entry) {
        // Rule 1: at least 2 lines
        if (entry.lines().size() < 2) {
            throw new ValidationException("An entry needs at least 2 lines");
        }

        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        for (JournalLine line : entry.lines()) {
            boolean hasDebit  = line.debit().signum()  > 0;
            boolean hasCredit = line.credit().signum() > 0;

            // Rule 2: no negative amounts
            if (line.debit().signum() < 0 || line.credit().signum() < 0) {
                throw new ValidationException("Amounts cannot be negative");
            }
            // Rule 3: exactly one of debit or credit
            if (hasDebit == hasCredit) {
                throw new ValidationException("Each line must have either a debit or a credit, not both or neither");
            }
            // Rule 4: the account must exist
            if (accounts.findById(line.accountId()).isEmpty()) {
                throw new ValidationException("Account not found: " + line.accountId());
            }

            totalDebit  = totalDebit.add(line.debit());
            totalCredit = totalCredit.add(line.credit());
        }

        // Rule 5: debits must equal credits
        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new ValidationException(
                    "Debits (" + totalDebit + ") must equal credits (" + totalCredit + ")");
        }
    }
}