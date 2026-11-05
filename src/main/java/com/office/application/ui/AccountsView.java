package com.office.application.ui;

import com.office.application.model.Account;
import com.office.application.model.AccountType;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.function.Consumer;

final class AccountsView extends VBox implements Refreshable {

    private record Row(Account account, BigDecimal balance) { }

    private final AppContext ctx;
    private final TableView<Row> table = Ui.table();
    private final TextField name = new TextField();
    private final ComboBox<AccountType> type = new ComboBox<>();

    AccountsView(AppContext ctx, Consumer<Account> openLedger) {
        this.ctx = ctx;
        table.getColumns().addAll(
                Ui.col("Name", r -> r.account().name(), 300),
                Ui.col("Type", r -> r.account().type().shortLabel(), 120),
                Ui.moneyCol("Balance", Row::balance, 140));
        table.setRowFactory(tv -> {
            TableRow<Row> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) openLedger.accept(row.getItem().account());
            });
            return row;
        });

        type.getItems().setAll(AccountType.EXPENSE, AccountType.ASSET, AccountType.INCOME, AccountType.LIABILITY, AccountType.EQUITY);
        type.setValue(AccountType.EXPENSE);
        type.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(AccountType t) { return t == null ? "" : t.label(); }
            @Override public AccountType fromString(String s) { return null; }
        });
        name.setPromptText("e.g. Courier Charges");

        GridPane form = Ui.form();
        form.addRow(0, new Label("Type"), type);
        form.addRow(1, new Label("Name"), name);

        Button add = new Button("Add account");
        add.setOnAction(e -> {
            if (Ui.attempt(() -> ctx.accounts.create(name.getText(), type.getValue()))) {
                name.clear(); refresh();
            }
        });
        Button rename = new Button("Rename selected");
        rename.setOnAction(e -> {
            Row sel = table.getSelectionModel().getSelectedItem();
            if (sel == null) { Ui.error("Select an account first"); return; }
            TextInputDialog d = new TextInputDialog(sel.account().name());
            d.setHeaderText(null);
            d.setTitle(Branding.APP_NAME);
            Branding.apply(d);
            d.setContentText("New name:");
            d.showAndWait().ifPresent(n -> { if (Ui.attempt(() -> ctx.accounts.rename(sel.account(), n))) refresh(); });
        });

        VBox page = Ui.page(Ui.title("Chart of Accounts"),
                new Label("Double-click an account to open its ledger. Each customer and vendor gets its own account automatically."),
                table, Ui.row(rename, export(table, "Accounts")), new Label("Add account (e.g. a new expense category)"), form, add);
        VBox.setVgrow(page, javafx.scene.layout.Priority.ALWAYS);
        getChildren().add(page);
    }

    private static Button export(TableView<?> t, String name) {
        Button b = new Button("Export CSV");
        b.setOnAction(e -> Ui.exportCsv(t, name + " " + LocalDate.now(), b.getScene().getWindow()));
        return b;
    }

    @Override
    public void refresh() {
        java.util.Map<java.util.UUID, BigDecimal> bal = ctx.reports.balances(LocalDate.now());   // one query for all
        table.getItems().setAll(ctx.accounts.all().stream()
                .map(a -> new Row(a, bal.getOrDefault(a.id(), BigDecimal.ZERO)))
                .toList());
    }
}
