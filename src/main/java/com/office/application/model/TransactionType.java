package com.office.application.model;

/** The simple "business" transactions the New Transaction screen offers. */
public enum TransactionType {
    PURCHASE("Purchase (from vendor)"),
    SALE("Sale (to customer)"),
    PAYMENT_MADE("Payment made (to vendor)"),
    PAYMENT_RECEIVED("Payment received (from customer)"),
    EXPENSE("Expense"),
    ASSET_PURCHASE("Buy asset (vehicle, equipment...)"),
    OWNER_INVESTMENT("Owner investment / opening cash");

    private final String label;

    TransactionType(String label) {
        this.label = label;
    }

    public boolean needsParty() {
        return this != EXPENSE && this != ASSET_PURCHASE && this != OWNER_INVESTMENT;
    }

    /** Expense and asset purchases need a category / asset account. */
    public boolean needsCategory() {
        return this == EXPENSE || this == ASSET_PURCHASE;
    }

    /** Purchases and sales must say which product was bought/sold. */
    public boolean needsProduct() {
        return this == PURCHASE || this == SALE;
    }

    public boolean canPayPartNow() {
        return this == PURCHASE || this == SALE;
    }

    @Override
    public String toString() {
        return label;
    }
}
