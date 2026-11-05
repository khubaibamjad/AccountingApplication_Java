package com.office.application.service;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** One place for number formatting: 1,000,000.00 everywhere, same on every PC. */
public final class Money {
    private Money() { }

    private static final DecimalFormatSymbols US = DecimalFormatSymbols.getInstance(Locale.US);

    /** 1234567.5 -> "1,234,567.50" */
    public static String format(BigDecimal v) {
        return v == null ? "" : new DecimalFormat("#,##0.00", US).format(v);
    }

    /** 1250.5 -> "1,250.5" ; 2 -> "2" (up to 3 decimals, kg) */
    public static String qty(BigDecimal v) {
        return v == null ? "" : new DecimalFormat("#,##0.###", US).format(v);
    }

    /** Accepts "1,000,000.50", "1000000.5", " 1 000 " ; returns null for empty text. */
    public static BigDecimal parse(String text, String field) {
        if (text == null || text.isBlank()) return null;
        try {
            return new BigDecimal(text.replace(",", "").replace(" ", "").trim());
        } catch (NumberFormatException e) {
            throw new ValidationException(field + " must be a number");
        }
    }
}
