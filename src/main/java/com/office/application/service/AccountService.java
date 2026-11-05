package com.office.application.service;

import com.office.application.model.Account;
import com.office.application.model.AccountType;
import com.office.application.repository.AccountRepository;
import com.office.application.repository.PartyRepository;
import com.office.application.security.AuditLog;

import java.util.List;

public class AccountService {

    private final AccountRepository accounts;
    private final PartyRepository parties;
    private final AuditLog audit;

    public AccountService(AccountRepository accounts, PartyRepository parties, AuditLog audit) {
        this.accounts = accounts;
        this.parties = parties;
        this.audit = audit;
    }

    public List<Account> all() {
        return accounts.findAll();
    }

    public List<Account> byType(AccountType type) {
        return accounts.findByType(type);
    }

    /** Creates an account with an automatically chosen internal code (users never type codes). */
    public Account create(String name, AccountType type) {
        if (type == null) throw new ValidationException("Choose an account type");
        return create(suggestCode(type), name, type);
    }

    public Account create(String code, String name, AccountType type) {
        if (type == null) throw new ValidationException("Choose an account type");
        String c = code == null ? "" : code.trim();
        if (!c.matches("\\d{4}")) throw new ValidationException("Code must be 4 digits, e.g. 5300");
        int n = Integer.parseInt(c);
        if (n < type.codeStart || n > type.codeEnd)
            throw new ValidationException(type + " codes must be between " + type.codeStart + " and " + type.codeEnd);
        if (accounts.findByCode(c).isPresent()) throw new ValidationException("Code " + c + " is already used");
        checkName(name, null);
        Account a = Account.create(c, name, type);
        accounts.save(a);
        audit.record("ACCOUNT_CREATED", a.code() + " " + a.name() + " (" + type + ")");
        return a;
    }

    public void rename(Account account, String newName) {
        if (parties.findAll().stream().anyMatch(p -> p.accountId().equals(account.id())))
            throw new ValidationException("This is a party's account. Rename the party in the Parties tab instead.");
        checkName(newName, account);
        accounts.save(account.withName(newName));
        audit.record("ACCOUNT_RENAMED", account.code() + " '" + account.name() + "' -> '" + newName.trim() + "'");
    }

    /** Next free code in the type's range, e.g. 5300 after 5200. */
    public String suggestCode(AccountType type) {
        return CodeAllocator.next(accounts.findAll(), type.codeStart, type.codeEnd, 100);
    }

    private void checkName(String name, Account self) {
        if (name == null || name.isBlank()) throw new ValidationException("Name is required");
        if (name.trim().length() > 150) throw new ValidationException("Name is too long (max 150 characters)");
        boolean taken = accounts.findAll().stream()
                .anyMatch(a -> a.name().equalsIgnoreCase(name.trim()) && (self == null || !a.id().equals(self.id())));
        if (taken) throw new ValidationException("An account named '" + name.trim() + "' already exists");
    }
}
