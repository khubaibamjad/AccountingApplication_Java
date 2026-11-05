package com.office.application.service;

import com.office.application.model.Account;
import com.office.application.model.AccountType;
import com.office.application.model.Party;
import com.office.application.model.PartyType;
import com.office.application.repository.AccountRepository;
import com.office.application.repository.PartyRepository;
import com.office.application.security.AuditLog;

import java.util.List;

public class PartyService {

    // Code ranges for party accounts
    private static final int CUSTOMER_START = 1300, CUSTOMER_END = 1999;
    private static final int VENDOR_START   = 2100, VENDOR_END   = 2999;

    private final PartyRepository parties;
    private final AccountRepository accounts;
    private final AuditLog audit;

    public PartyService(PartyRepository parties, AccountRepository accounts, AuditLog audit) {
        this.parties = parties;
        this.accounts = accounts;
        this.audit = audit;
    }

    public List<Party> all() {
        return parties.findAll();
    }

    /** Vendors + BOTH (for purchases/payments made) or customers + BOTH (for sales/receipts). */
    public List<Party> forSide(boolean vendors) {
        PartyType side = vendors ? PartyType.VENDOR : PartyType.CUSTOMER;
        return parties.findAll().stream()
                .filter(p -> p.type() == side || p.type() == PartyType.BOTH)
                .toList();
    }

    /** Creates the party and its own ledger account together. */
    /** Unregistered party (no NTN). */
    public Party createParty(String name, PartyType type, String phone, String region) {
        return createParty(name, type, phone, region, false, null);
    }

    public Party createParty(String name, PartyType type, String phone, String region, boolean registered, String ntn) {
        if (type == null) throw new ValidationException("Choose a party type");
        checkDetails(name, phone, region, null);
        String cleanNtn = checkNtn(registered, ntn, null);

        // Customers owe you (ASSET). Vendors and BOTH are owed (LIABILITY); the sign of the balance tells the direction.
        boolean customer = type == PartyType.CUSTOMER;
        AccountType accountType = customer ? AccountType.ASSET : AccountType.LIABILITY;
        String code = customer
                ? CodeAllocator.next(accounts.findAll(), CUSTOMER_START, CUSTOMER_END, 1)
                : CodeAllocator.next(accounts.findAll(), VENDOR_START, VENDOR_END, 1);

        Account account = Account.create(code, name, accountType);
        Party party = Party.create(name, type, phone, region, account.id(), registered, cleanNtn);
        parties.saveWithAccount(party, account);
        audit.record("PARTY_CREATED", type + " '" + party.name() + "' " + party.status()
                + (registered ? " NTN " + cleanNtn : "") + " (account " + code + ")");
        return party;
    }

    /** Updates contact details; keeps the party's account name in sync. */
    public void update(Party input) {
        checkDetails(input.name(), input.phone(), input.region(), input);
        String cleanNtn = checkNtn(input.registered(), input.ntn(), input);
        Party party = new Party(input.id(), input.name(), input.type(), input.phone(), input.region(), input.accountId(),
                input.registered(), cleanNtn);
        parties.save(party);
        audit.record("PARTY_UPDATED", "'" + party.name() + "' " + party.status() + (party.registered() ? " NTN " + cleanNtn : "")
                + " phone=" + party.phone() + " region=" + party.region());
        accounts.findById(party.accountId())
                .ifPresent(a -> accounts.save(a.withName(party.name())));
    }

    /**
     * Registered parties need a valid, unique NTN: 7 digits with optional check digit (1234567 or 1234567-8),
     * or a 13-digit CNIC (3520212345671 or 35202-1234567-1). Returns the cleaned NTN, or null if unregistered.
     */
    private String checkNtn(boolean registered, String ntn, Party self) {
        if (!registered) return null;
        if (ntn == null || ntn.isBlank()) throw new ValidationException("NTN is required for a registered party");
        String n = ntn.trim().replace(" ", "");
        if (!n.matches("\\d{7}(-?\\d)?") && !n.matches("\\d{13}") && !n.matches("\\d{5}-\\d{7}-\\d"))
            throw new ValidationException("NTN must be 7 digits (e.g. 1234567 or 1234567-8) or a 13-digit CNIC");
        String digits = n.replace("-", "");
        boolean taken = parties.findAll().stream()
                .anyMatch(p -> p.ntn() != null && p.ntn().replace("-", "").equals(digits)
                        && (self == null || !p.id().equals(self.id())));
        if (taken) throw new ValidationException("Another party already has NTN " + n);
        return n;
    }

    private void checkDetails(String name, String phone, String region, Party self) {
        if (name == null || name.isBlank()) throw new ValidationException("Party name is required");
        String n = name.trim();
        if (n.length() > 150) throw new ValidationException("Party name is too long (max 150 characters)");
        if (phone != null && !phone.isBlank() && !phone.trim().matches("[0-9+()\\-\\s]{3,30}"))
            throw new ValidationException("Phone can only contain digits, spaces, + - ( ) (3 to 30 characters)");
        if (region != null && region.trim().length() > 50) throw new ValidationException("Region is too long (max 50 characters)");

        boolean partyTaken = parties.findAll().stream()
                .anyMatch(p -> p.name().equalsIgnoreCase(n) && (self == null || !p.id().equals(self.id())));
        if (partyTaken) throw new ValidationException("A party named '" + n + "' already exists");
        boolean accountTaken = accounts.findAll().stream()
                .anyMatch(a -> a.name().equalsIgnoreCase(n) && (self == null || !a.id().equals(self.accountId())));
        if (accountTaken) throw new ValidationException("'" + n + "' is already the name of an account. Use a different name.");
    }
}
