package com.office.application.ui;

import com.office.application.model.SystemAccounts;
import com.office.application.report.PartyBalance;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;

final class DashboardView extends VBox implements Refreshable {

    private final AppContext ctx;
    private final FlowPane cards = new FlowPane(12, 12);
    private final TableView<PartyBalance> open = Ui.table();

    DashboardView(AppContext ctx) {
        this.ctx = ctx;
        open.getColumns().addAll(
                Ui.col("Party", pb -> pb.party().name(), 220),
                Ui.col("Type", pb -> pb.party().type(), 100),
                Ui.col("Status", PartyBalance::status, 140),
                Ui.moneyCol("Amount", pb -> pb.net().abs(), 140));
        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        javafx.scene.layout.HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        VBox page = Ui.page(Ui.row(Ui.title("Dashboard"), spacer, Branding.logo(40)), cards, new Label("Open party balances"), open);
        getChildren().add(page);
        VBox.setVgrow(page, javafx.scene.layout.Priority.ALWAYS);
    }

    @Override
    public void refresh() {
        LocalDate today = LocalDate.now();
        YearMonth m = YearMonth.now();
        BigDecimal[] rp = ctx.reports.receivablesAndPayables(today);
        java.util.Map<java.util.UUID, BigDecimal> bal = ctx.reports.balances(today);
        BigDecimal cash = bal.getOrDefault(SystemAccounts.CASH, BigDecimal.ZERO);
        BigDecimal bank = bal.getOrDefault(SystemAccounts.BANK, BigDecimal.ZERO);
        BigDecimal profit = ctx.reports.netProfit(m.atDay(1), m.atEndOfMonth());

        cards.getChildren().setAll(
                card("Cash in hand", cash),
                card("Bank", bank),
                card("Customers owe you", rp[0]),
                card("You owe vendors", rp[1]),
                card((profit.signum() >= 0 ? "Profit" : "Loss") + " this month", profit.abs()));

        open.getItems().setAll(ctx.reports.partyBalances(today).stream()
                .filter(pb -> pb.net().signum() != 0)
                .sorted(Comparator.comparing((PartyBalance pb) -> pb.net().abs()).reversed())
                .toList());
    }

    private static VBox card(String label, BigDecimal value) {
        Label l = new Label(label);
        l.getStyleClass().add("card-label");
        Label v = new Label(Ui.money(value));
        v.getStyleClass().add("card-value");
        VBox box = new VBox(4, l, v);
        box.getStyleClass().add("card");
        return box;
    }
}
