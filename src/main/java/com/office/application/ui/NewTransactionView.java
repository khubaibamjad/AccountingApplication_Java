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
    private final TextField product = new TextField();
    private final TextField quantity = new TextField();
    private final TextField price = new TextField();
    private final Label total = new Label("0.00");
    private final TextField amount = new TextField();
    private final TextField paidNow = new TextField();
    private final TextField cost = new TextField();
    private final ComboBox<Account> payment = new ComboBox<>();
    private final TextField note = new TextField();
    private final ComboBox<String> direction = new ComboBox<>();
    private final Label hint = new Label();
    private final GridPane form = Ui.form();
    private final Button save = new Button("Save transaction");

    NewTransactionView(AppContext ctx) {
        this.ctx = ctx;
        type.getItems().setAll(TransactionType.values());
        type.setValue(TransactionType.PURCHASE);
        type.setOnAction(e -> layoutForType());
        product.setPromptText("required, e.g. Cement");
        quantity.setPromptText("kg, e.g. 1250.5");
        price.setPromptText("price per kg, e.g. 85");
        total.getStyleClass().add("card-value");
        quantity.textProperty().addListener((o, a, b) -> updateTotal());
        price.textProperty().addListener((o, a, b) -> updateTotal());
        Ui.qtyField(quantity);
        Ui.moneyField(price);
        Ui.moneyField(amount);
        Ui.moneyField(paidNow);
        Ui.moneyField(cost);
        amount.setPromptText("e.g. 200000");
        paidNow.setPromptText("leave empty if nothing paid now");
        cost.setPromptText("optional: what these goods cost you");
        note.setPromptText("optional, e.g. invoice #123, truck no.");
        party.setPromptText("select...");
        direction.getItems().setAll("They owe us", "We owe them");
        direction.setValue("They owe us");
        category.setPromptText("select...");
        for (Control c : List.of(type, date, party, category, product, quantity, price, amount, paidNow, cost, payment, note, direction))
            c.setMaxWidth(Double.MAX_VALUE);

        save.setDefaultButton(true);
        save.setOnAction(e -> save());
        hint.setWrapText(true);
        hint.setMaxWidth(560);
        hint.getStyleClass().add("hint");

        getChildren().add(Ui.page(Ui.title("New Transaction"), form, save, hint));
        layoutForType();
    }

    @Override
    public void refresh() {
        layoutForType();   // reloads parties, categories and cash/bank lists
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
            party.getItems().setAll(t == TransactionType.PARTY_OPENING ? ctx.parties.all() : ctx.parties.forSide(vendors));
            party.setValue(party.getItems().stream().filter(p -> keep != null && p.id().equals(keep.id())).findFirst().orElse(null));
            form.addRow(r++, new Label((t == TransactionType.PARTY_OPENING ? "Party" : vendors ? "Vendor" : "Customer") + " *"), party);
            if (t == TransactionType.PARTY_OPENING) {
                form.addRow(r++, new Label("Who owes? *"), direction);
                party.setOnAction(e -> { if (party.getValue() != null)
                    direction.setValue(party.getValue().type() == PartyType.CUSTOMER ? "They owe us" : "We owe them"); });
            } else {
                party.setOnAction(null);
            }
        }
        if (t.needsCategory()) {
            Account keep = category.getValue();
            category.getItems().setAll(t == TransactionType.EXPENSE
                    ? ctx.accounts.byType(AccountType.EXPENSE).stream()
                        .filter(a -> !a.id().equals(SystemAccounts.COST_OF_GOODS_SOLD)).toList()
                    // fixed assets: 1201-1299 (not cash, bank, inventory or party accounts)
                    : ctx.accounts.byType(AccountType.ASSET).stream()
                        .filter(a -> a.code().compareTo("1201") >= 0 && a.code().compareTo("1299") <= 0).toList());
            category.setValue(category.getItems().stream().filter(a -> keep != null && a.id().equals(keep.id())).findFirst().orElse(null));
            form.addRow(r++, new Label((t == TransactionType.EXPENSE ? "Expense category" : "Asset") + " *"), category);
        }
        if (t.needsProduct()) {
            form.addRow(r++, new Label("Product *"), product);
            form.addRow(r++, new Label("Quantity (kg) *"), quantity);
            form.addRow(r++, new Label("Price per kg *"), price);
            form.addRow(r++, new Label("Total amount"), total);      // calculated: quantity x price
            updateTotal();
        } else {
            form.addRow(r++, new Label("Amount *"), amount);
        }
        if (t.canPayPartNow()) form.addRow(r++, new Label(t == TransactionType.SALE ? "Received now" : "Paid now"), paidNow);
        if (t == TransactionType.SALE) form.addRow(r++, new Label("Cost of goods"), cost);
        if (t != TransactionType.PARTY_OPENING) {
            // owner investment may also be opening stock (Inventory 1200); everything else uses cash/bank 1000-1199
            Account keepPay = payment.getValue();
            payment.getItems().setAll(ctx.accounts.byType(AccountType.ASSET).stream()
                    .filter(a -> (a.code().compareTo("1000") >= 0 && a.code().compareTo("1199") <= 0)
                            || (t == TransactionType.OWNER_INVESTMENT && a.id().equals(SystemAccounts.INVENTORY)))
                    .toList());
            payment.setValue(payment.getItems().stream().filter(a -> keepPay != null && a.id().equals(keepPay.id())).findFirst()
                    .orElse(payment.getItems().stream().findFirst().orElse(null)));
            form.addRow(r++, new Label(t == TransactionType.PAYMENT_RECEIVED || t == TransactionType.OWNER_INVESTMENT
                    || t == TransactionType.SALE ? "Received into" : "Paid from"), payment);
        }
        form.addRow(r, new Label("Description"), note);

        hint.setText(switch (t) {
            case PURCHASE -> "Stock bought from a vendor. Whatever is not paid now is added to what you owe them.";
            case SALE -> "Goods sold to a customer. Whatever is not received now is added to what they owe you. "
                    + "Fill 'Cost of goods' with what those goods cost you, so profit is correct and stock goes down.";
            case PAYMENT_MADE -> "You paid a vendor (for earlier purchases). Reduces what you owe them.";
            case PAYMENT_RECEIVED -> "A customer paid you (for earlier sales). Reduces what they owe you. Do NOT record it as a new sale.";
            case EXPENSE -> "Fuel, rent, electricity, office food... Need another category? Add it in the Accounts tab (type EXPENSE).";
            case ASSET_PURCHASE -> "Things the business keeps for years (a car, furniture, computers). Not an expense, so it does not reduce profit. Fuel and repairs for them are expenses.";
            case OWNER_INVESTMENT -> "Money the owner puts in. Also use it once at the start for opening cash, bank and stock (choose Inventory) balances.";
            case PARTY_OPENING -> "Use once per party when you start using the app: what they already owed you, or you owed them, from before. Does not count as a sale or purchase.";
        });
    }

    /** Live total = quantity x price per kg, shown as the user types. */
    private void updateTotal() {
        try {
            var q = Ui.parseMoney(quantity.getText(), "Quantity");
            var p = Ui.parseMoney(price.getText(), "Price");
            var t = TransactionService.total(q, p);
            total.setText(t == null ? "0.00" : Ui.money(t));
        } catch (RuntimeException e) {
            total.setText("—");
        }
    }

    private static java.math.BigDecimal requiredNumber(TextField f, String field) {
        java.math.BigDecimal v = Ui.parseMoney(f.getText(), field);
        if (v == null) throw new com.office.application.service.ValidationException(field + " is required");
        return v;
    }

    private void save() {
        save.setDisable(true);   // no double-saving on a double click
        try {
            Ui.attempt(() -> {
                TransactionType t = type.getValue();
                TransactionService.Request req = new TransactionService.Request(
                        t, Ui.dateOf(date),
                        t.needsParty() ? party.getValue() : null,
                        t.needsCategory() ? category.getValue() : null,
                        payment.getValue(),
                        t.needsProduct() ? null : Ui.parseMoney(amount.getText(), "Amount"),
                        t.canPayPartNow() ? Ui.parseMoney(paidNow.getText(), t == TransactionType.SALE ? "Received now" : "Paid now") : null,
                        t == TransactionType.SALE ? Ui.parseMoney(cost.getText(), "Cost of goods") : null,
                        t.needsProduct() ? product.getText() : null,
                        note.getText(),
                        "They owe us".equals(direction.getValue()),
                        t.needsProduct() ? requiredNumber(quantity, "Quantity (kg)") : null,
                        t.needsProduct() ? requiredNumber(price, "Price per kg") : null);

                List<String> warnings = ctx.transactions.warnings(req);
                if (!warnings.isEmpty() && !Ui.confirm(String.join("\n\n", warnings) + "\n\nSave anyway?")) return;

                JournalEntry e = ctx.transactions.record(req);
                amount.clear();
                product.clear();
                quantity.clear();
                price.clear();
                paidNow.clear();
                cost.clear();
                note.clear();
                Ui.info("Saved: " + e.description()
                        + (e.product() == null ? "" : "\n" + e.product() + ": " + Ui.qty(e.quantityKg()) + " kg x " + Ui.money(e.pricePerKg()) + " per kg")
                        + "\nAmount: " + Ui.money(e.amount()));
            });
        } finally {
            save.setDisable(false);
        }
    }
}
