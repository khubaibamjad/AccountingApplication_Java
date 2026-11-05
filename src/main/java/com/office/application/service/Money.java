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

    /**
     * What a number box accepts while typing: digits, thousands commas and one decimal point with at most
     * `decimals` digits after it. Empty is allowed (field not filled yet). No letters, no minus sign.
     */
    public static boolean isValidInput(String text, int decimals) {
        if (text == null || text.isEmpty()) return true;
        if (text.length() > 22) return false;
        String frac = decimals > 0 ? "(\\.[0-9]{0," + decimals + "})?" : "";
        return text.matches("[0-9][0-9,]*" + frac) || (decimals > 0 && text.matches("\\.[0-9]{0," + decimals + "}"));
    }

    /** NTN / CNIC box: digits and dashes only, max 15 characters. */
    public static boolean isValidNtnInput(String text) {
        return text == null || text.matches("[0-9-]{0,15}");
    }

    /** Phone box: digits, spaces, + - ( ), max 30 characters. */
    public static boolean isValidPhoneInput(String text) {
        return text == null || text.matches("[0-9+()\\-\\s]{0,30}");
    }
}
