package com.office.application.ui;

import com.office.application.report.StatementLine;
import com.office.application.report.TrialBalanceRow;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.LocalDate;

final class ReportsView extends VBox implements Refreshable {

    private final AppContext ctx;
    private final DateRangeBar range;
    private final TableView<TrialBalanceRow> trial = Ui.table();
    private final TableView<StatementLine> pnl = statementTable();
    private final TableView<StatementLine> sheet = statementTable();
    private final Label trialTotals = new Label();
    private final Label asOfNote = new Label();

    ReportsView(AppContext ctx) {
        this.ctx = ctx;
        this.range = new DateRangeBar("This month", this::refresh);

        trial.getColumns().addAll(
                Ui.col("Code", r -> r.account().code(), 80),
                Ui.col("Account", r -> r.account().name(), 280),
                Ui.moneyCol("Debit", TrialBalanceRow::debit, 140),
                Ui.moneyCol("Credit", TrialBalanceRow::credit, 140));

        TabPane tabs = new TabPane(
                new Tab("Profit & Loss", Ui.page(pnl)),
                new Tab("Balance Sheet", Ui.page(sheet)),
                new Tab("Trial Balance", Ui.page(trial, trialTotals)));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabs, javafx.scene.layout.Priority.ALWAYS);
        asOfNote.getStyleClass().add("hint");

        VBox page = Ui.page(Ui.title("Reports"), range, asOfNote, tabs);
        VBox.setVgrow(page, javafx.scene.layout.Priority.ALWAYS);
        getChildren().add(page);
    }

    private static TableView<StatementLine> statementTable() {
        TableView<StatementLine> t = Ui.table();
        t.getColumns().addAll(Ui.col("", StatementLine::label, 420), Ui.moneyCol("Amount", StatementLine::amount, 160));
        t.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(StatementLine item, boolean empty) {
                super.updateItem(item, empty);
                setStyle(item != null && item.bold() ? "-fx-font-weight: bold;" : "");
            }
        });
        return t;
    }

    @Override
    public void refresh() {
        LocalDate from = range.from(), to = range.to();
        LocalDate asOf = to.isAfter(LocalDate.now()) ? LocalDate.now() : to;
        asOfNote.setText("Profit & Loss covers " + from + " to " + to
                + ". Balance Sheet and Trial Balance are as of " + asOf + ".");

        pnl.getItems().setAll(ctx.reports.profitAndLoss(from, to));
        sheet.getItems().setAll(ctx.reports.balanceSheet(asOf));
        trial.getItems().setAll(ctx.reports.trialBalance(asOf));

        BigDecimal d = trial.getItems().stream().map(TrialBalanceRow::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal c = trial.getItems().stream().map(TrialBalanceRow::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
        trialTotals.setText("Total debit " + Ui.money(d) + "   |   Total credit " + Ui.money(c)
                + (d.compareTo(c) == 0 ? "   ✔ balanced" : "   ✘ NOT balanced"));
    }
}
