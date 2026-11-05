package com.office.application.repository;

import com.office.application.model.Account;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository {

    List<Account> findAll();

    Optional<Account> findByCode(String code);
    void save(Account account);

    Optional<Account> findById(UUID id);
}
