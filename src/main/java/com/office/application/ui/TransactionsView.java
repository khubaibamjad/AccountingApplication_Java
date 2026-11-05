package com.office.application.ui;

import com.office.application.model.Account;
import com.office.application.model.JournalEntry;
import com.office.application.model.JournalLine;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Every saved entry in a period; lets you delete a wrong one. */
final class TransactionsView extends VBox implements Refreshable {

    private final AppContext ctx;
    private final TableView<JournalEntry> table = Ui.table();
    private final DateRangeBar range;
    private Map<UUID, Account> accountsById = Map.of();

    TransactionsView(AppContext ctx) {
        this.ctx = ctx;
        this.range = new DateRangeBar("This month", this::refresh);
        table.getColumns().addAll(
                Ui.col("Date", JournalEntry::entryDate, 100),
                Ui.col("Description", JournalEntry::description, 280),
                Ui.col("Product", JournalEntry::product, 140),
                Ui.col("Details", this::details, 380),
                Ui.moneyCol("Amount", e -> e.lines().stream().map(JournalLine::debit).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO), 120));

        Button delete = new Button("Delete selected");
        delete.setOnAction(e -> {
            JournalEntry sel = table.getSelectionModel().getSelectedItem();
            if (sel == null) { Ui.error("Select a transaction first"); return; }
            if (Ui.confirm("Delete '" + sel.description() + "' on " + sel.entryDate() + "?\n\n"
                    + "If a payment was saved together with this sale/purchase, it is deleted too. This cannot be undone."))
                Ui.attempt(() -> { ctx.journal.delete(sel.id()); refresh(); });
        });

        Button export = new Button("Export CSV");
        export.setOnAction(e -> Ui.exportCsv(table, "Transactions " + range.from() + " to " + range.to(), getScene().getWindow()));
        VBox page = Ui.page(Ui.title("Transactions"), range, table, Ui.row(delete, export));
        VBox.setVgrow(page, javafx.scene.layout.Priority.ALWAYS);
        getChildren().add(page);
    }

    private String details(JournalEntry e) {
        return e.lines().stream().map(l -> {
            Account a = accountsById.get(l.accountId());
            String name = a == null ? "?" : a.name();
            return (l.debit().signum() > 0 ? "Dr " : "Cr ") + name + " " + Ui.money(l.debit().max(l.credit()));
        }).collect(Collectors.joining(" | "));
    }

    @Override
    public void refresh() {
        accountsById = ctx.accounts.all().stream().collect(Collectors.toMap(Account::id, Function.identity()));
        table.getItems().setAll(ctx.journal.entries(range.from(), range.to()));
    }
}
