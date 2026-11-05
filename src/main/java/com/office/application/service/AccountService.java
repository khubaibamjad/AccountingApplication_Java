package com.office.application.service;

import com.office.application.model.Account;
import com.office.application.model.AccountType;
import com.office.application.repository.AccountRepository;

import java.util.List;

public class AccountService {

    private final AccountRepository accounts;

    public AccountService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    public List<Account> all() {
        return accounts.findAll();
    }

    public List<Account> byType(AccountType type) {
        return accounts.findByType(type);
    }

    public Account create(String code, String name, AccountType type) {
        if (code == null || !code.trim().matches("\\d{4}")) throw new ValidationException("Code must be 4 digits, e.g. 5300");
        if (name == null || name.isBlank()) throw new ValidationException("Name is required");
        if (type == null) throw new ValidationException("Choose an account type");
        if (accounts.findByCode(code.trim()).isPresent()) throw new ValidationException("Code " + code.trim() + " is already used");
        Account a = Account.create(code, name, type);
        accounts.save(a);
        return a;
    }

    public void rename(Account account, String newName) {
        if (newName == null || newName.isBlank()) throw new ValidationException("Name is required");
        accounts.save(account.withName(newName));
    }

    /** Next free code in the type's range, e.g. 5300 after 5200. */
    public String suggestCode(AccountType type) {
        return CodeAllocator.next(accounts.findAll(), type.codeStart, type.codeEnd, 100);
    }
}
