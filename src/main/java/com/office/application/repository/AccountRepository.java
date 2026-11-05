package com.office.application.repository;

import com.office.application.model.Account;
import com.office.application.model.AccountType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository {
    List<Account> findAll();
    List<Account> findByType(AccountType type);
    Optional<Account> findById(UUID id);
    Optional<Account> findByCode(String code);
    void save(Account account);
}
