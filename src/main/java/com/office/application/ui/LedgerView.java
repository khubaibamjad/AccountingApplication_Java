package com.office.application.ui;

import com.office.application.model.Account;
import com.office.application.report.LedgerRow;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

import java.util.UUID;

/** Statement of one account (any account, including a party) for a period. */
final class LedgerView extends VBox implements Refreshable {

    private final AppContext ctx;
    private final ComboBox<Account> account = new ComboBox<>();
    private final TableView<LedgerRow> table = Ui.table();
    private final DateRangeBar range;

    LedgerView(AppContext ctx) {
        this.ctx = ctx;
        this.range = new DateRangeBar("This year", this::load);
        account.setPrefWidth(320);
        account.setOnAction(e -> load());

        table.getColumns().addAll(
                Ui.col("Date", LedgerRow::date, 100),
                Ui.col("Description", LedgerRow::description, 340),
                Ui.moneyCol("Debit", LedgerRow::debit, 120),
                Ui.moneyCol("Credit", LedgerRow::credit, 120),
                Ui.moneyCol("Balance", LedgerRow::balance, 140));
        table.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(LedgerRow item, boolean empty) {
                super.updateItem(item, empty);
                setStyle(item != null && item.date() == null ? "-fx-font-weight: bold;" : "");
            }
        });

        VBox page = Ui.page(Ui.title("Ledger / Statement"), Ui.row(new Label("Account:"), account), range, table);
        VBox.setVgrow(page, javafx.scene.layout.Priority.ALWAYS);
        getChildren().add(page);
    }

    void show(UUID accountId) {
        refresh();
        account.getItems().stream().filter(a -> a.id().equals(accountId)).findFirst().ifPresent(account::setValue);
        load();
    }

    @Override
    public void refresh() {
        Account keep = account.getValue();
        account.getItems().setAll(ctx.accounts.all());
        if (keep != null) account.getItems().stream().filter(a -> a.id().equals(keep.id())).findFirst().ifPresent(account::setValue);
        load();
    }

    private void load() {
        if (account.getValue() == null) { table.getItems().clear(); return; }
        table.getItems().setAll(ctx.reports.ledger(account.getValue(), range.from(), range.to()));
    }
}
