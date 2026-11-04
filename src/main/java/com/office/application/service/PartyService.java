package com.office.application.service;

import com.office.application.model.Account;
import com.office.application.model.AccountType;
import com.office.application.model.Party;
import com.office.application.model.PartyType;
import com.office.application.repository.AccountRepository;
import com.office.application.repository.PartyRepository;

public class PartyService {

    // Code ranges for party accounts
    private static final int CUSTOMER_START = 1300, CUSTOMER_END = 1999;
    private static final int VENDOR_START   = 2100, VENDOR_END   = 2999;

    private final PartyRepository parties;
    private final AccountRepository accounts;

    public PartyService(PartyRepository parties, AccountRepository accounts) {
        this.parties = parties;
        this.accounts = accounts;
    }

    public Party createParty(String name, PartyType type, String phone, String region) {
        // Customers owe you money, so ASSET. Vendors (and BOTH) are owed money, so LIABILITY.
        AccountType accountType = (type == PartyType.CUSTOMER) ? AccountType.ASSET : AccountType.LIABILITY;
        String code = nextCode(type);

        Account account = Account.create(code, name, accountType);
        accounts.save(account);

        Party party = Party.create(name, type, phone, region, account.id());
        parties.save(party);
        return party;
    }

    // Finds the highest used code in the range and returns the next one
    private String nextCode(PartyType type) {
        int start = (type == PartyType.CUSTOMER) ? CUSTOMER_START : VENDOR_START;
        int end   = (type == PartyType.CUSTOMER) ? CUSTOMER_END   : VENDOR_END;

        int highest = accounts.findAll().stream()
                .map(Account::code)
                .filter(c -> c.matches("\\d+"))          // only numeric codes
                .mapToInt(Integer::parseInt)
                .filter(c -> c >= start && c <= end)
                .max()
                .orElse(start - 1);                      // none yet, so the first one is "start"

        if (highest >= end) {
            throw new IllegalStateException("No free account codes left for " + type);
        }
        return String.valueOf(highest + 1);
    }
}