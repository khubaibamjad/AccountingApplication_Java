package com.office.application.repository;

import com.office.application.model.Account;

import java.util.List;

public interface AccountRepository {

    List<Account> findAll();
}
