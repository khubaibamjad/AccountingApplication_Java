package com.office.application.ui;

import com.office.application.model.*;
import com.office.application.service.TransactionService;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.List;

/** One form for every everyday transaction. Fields show/hide based on the type. */
final class NewTransactionView extends VBox implements Refreshable {

    private final AppContext ctx;
    private final ComboBox<TransactionType> type = new ComboBox<>();
    private final DatePicker date = new DatePicker(LocalDate.now());
    private final ComboBox<Party> party = new ComboBox<>();
    private final ComboBox<Account> category = new ComboBox<>();
    private final TextField amount = new TextField();
    private final TextField paidNow = new TextField();
    private final ComboBox<Account> payment = new ComboBox<>();
    private final TextField note = new TextField();
    private final Label hint = new Label();
    private final GridPane form = Ui.form();

    NewTransactionView(AppContext ctx) {
        this.ctx = ctx;
        type.getItems().setAll(TransactionType.values());
        type.setValue(TransactionType.PURCHASE);
        type.setOnAction(e -> layoutForType());
        amount.setPromptText("e.g. 200000");
        paidNow.setPromptText("0 = all on credit");
        note.setPromptText("optional, e.g. invoice #123");
        for (Control c : List.of(type, date, party, category, amount, paidNow, payment, note)) c.setMaxWidth(Double.MAX_VALUE);

        Button save = new Button("Save transaction");
        save.setDefaultButton(true);
        save.setOnAction(e -> save());
        hint.setWrapText(true);
        hint.getStyleClass().add("hint");

        getChildren().add(Ui.page(Ui.title("New Transaction"), form, save, hint));
        layoutForType();
    }

    @Override
    public void refresh() {
        Account keepPayment = payment.getValue();
        payment.getItems().setAll(ctx.accounts.byType(AccountType.ASSET).stream()
                .filter(a -> a.id().equals(SystemAccounts.CASH) || a.id().equals(SystemAccounts.BANK)
                        || (a.code().compareTo("1000") >= 0 && a.code().compareTo("1199") <= 0))
                .toList());
        payment.setValue(keepPayment != null && payment.getItems().contains(keepPayment)
                ? keepPayment : payment.getItems().stream().findFirst().orElse(null));
        category.getItems().setAll(ctx.accounts.byType(AccountType.EXPENSE));
        layoutForType();
    }

    private void layoutForType() {
        TransactionType t = type.getValue();
        form.getChildren().clear();
        int r = 0;
        form.addRow(r++, new Label("Type"), type);
        form.addRow(r++, new Label("Date"), date);
        if (t.needsParty()) {
            boolean vendors = t == TransactionType.PURCHASE || t == TransactionType.PAYMENT_MADE;
            Party keep = party.getValue();
            party.getItems().setAll(ctx.parties.forSide(vendors));
            party.setValue(party.getItems().contains(keep) ? keep : null);
            form.addRow(r++, new Label(vendors ? "Vendor" : "Customer"), party);
        }
        if (t == TransactionType.EXPENSE) form.addRow(r++, new Label("Expense category"), category);
        form.addRow(r++, new Label(t.canPayPartNow() ? "Total amount" : "Amount"), amount);
        if (t.canPayPartNow()) form.addRow(r++, new Label(t == TransactionType.SALE ? "Received now" : "Paid now"), paidNow);
        form.addRow(r++, new Label(t == TransactionType.PAYMENT_RECEIVED || t == TransactionType.OWNER_INVESTMENT
                || t == TransactionType.SALE ? "Received into" : "Paid from"), payment);
        form.addRow(r, new Label("Note"), note);

        hint.setText(switch (t) {
            case PURCHASE -> "Stock bought from a vendor. Whatever is not paid now is added to what you owe them.";
            case SALE -> "Goods sold to a customer. Whatever is not received now is added to what they owe you.";
            case PAYMENT_MADE -> "You paid a vendor. Reduces what you owe them.";
            case PAYMENT_RECEIVED -> "A customer paid you. Reduces what they owe you.";
            case EXPENSE -> "Rent, salaries, electricity... Add new categories in the Accounts tab (type EXPENSE).";
            case OWNER_INVESTMENT -> "Money the owner puts into the business. Use it once to record your opening cash/bank.";
        });
    }

    private void save() {
        Ui.attempt(() -> {
            TransactionType t = type.getValue();
            TransactionService.Request req = new TransactionService.Request(
                    t, date.getValue(),
                    t.needsParty() ? party.getValue() : null,
                    t == TransactionType.EXPENSE ? category.getValue() : null,
                    payment.getValue(),
                    Ui.parseMoney(amount.getText(), "Amount"),
                    t.canPayPartNow() ? Ui.parseMoney(paidNow.getText(), "Paid now") : null,
                    note.getText());
            JournalEntry e = ctx.transactions.record(req);
            amount.clear();
            paidNow.clear();
            note.clear();
            Ui.info("Saved: " + e.description());
        });
    }
}
