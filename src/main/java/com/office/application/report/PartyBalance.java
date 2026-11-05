package com.office.application.report;

import com.office.application.model.Party;

import java.math.BigDecimal;

/** net > 0: the party owes you (receivable). net < 0: you owe the party (payable). */
public record PartyBalance(Party party, BigDecimal net) {
    public String status() {
        return switch (net.signum()) {
            case 1 -> "They owe you";
            case -1 -> "You owe them";
            default -> "Settled";
        };
    }
}
