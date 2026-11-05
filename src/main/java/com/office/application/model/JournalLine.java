package com.office.application.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

public record JournalLine (UUID id, UUID accountId, BigDecimal debit, BigDecimal credit){

    public JournalLine {
        if (id == null) throw new IllegalArgumentException("id is required");
        if (accountId == null) throw new IllegalArgumentException("account is required");
        debit  = (debit  == null ? BigDecimal.ZERO : debit ).setScale(2, RoundingMode.HALF_UP);
        credit = (credit == null ? BigDecimal.ZERO : credit).setScale(2, RoundingMode.HALF_UP);
    }

    public static JournalLine debit(UUID accountId, BigDecimal amount) {
        return new JournalLine(UUID.randomUUID(), accountId, amount, BigDecimal.ZERO);
    }

    public static JournalLine credit(UUID accountId, BigDecimal amount) {
        return new JournalLine(UUID.randomUUID(), accountId, BigDecimal.ZERO, amount);
    }
}
