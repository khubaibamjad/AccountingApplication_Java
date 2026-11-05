package com.office.application.ui;

import com.office.application.model.Party;
import com.office.application.model.PartyType;
import com.office.application.report.PartyBalance;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.function.Consumer;

final class PartiesView extends VBox implements Refreshable {

    private final AppContext ctx;
    private final TableView<PartyBalance> table = Ui.table();
    private final TextField name = new TextField();
    private final ComboBox<PartyType> type = new ComboBox<>();
    private final TextField phone = new TextField();
    private final TextField region = new TextField();
    private final TextField search = new TextField();
    private Party editing;

    PartiesView(AppContext ctx, Consumer<Party> openLedger) {
        this.ctx = ctx;
        type.getItems().setAll(PartyType.values());
        type.setValue(PartyType.VENDOR);
        search.setPromptText("Search name or region");
        search.textProperty().addListener((o, a, b) -> refresh());

        table.getColumns().addAll(
                Ui.col("Name", pb -> pb.party().name(), 200),
                Ui.col("Type", pb -> pb.party().type(), 90),
                Ui.col("Phone", pb -> pb.party().phone(), 120),
                Ui.col("Region", pb -> pb.party().region(), 110),
                Ui.col("Status", PartyBalance::status, 120),
                Ui.moneyCol("Balance", pb -> pb.net().abs(), 120));
        table.setRowFactory(tv -> {
            TableRow<PartyBalance> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) openLedger.accept(row.getItem().party());
            });
            return row;
        });

        GridPane form = Ui.form();
        form.addRow(0, new Label("Name"), name);
        form.addRow(1, new Label("Type"), type);
        form.addRow(2, new Label("Phone"), phone);
        form.addRow(3, new Label("Region"), region);

        Button save = new Button("Save party");
        save.setOnAction(e -> save());
        Button edit = new Button("Edit selected");
        edit.setOnAction(e -> startEdit());
        Button clear = new Button("Clear");
        clear.setOnAction(e -> clearForm());
        Button ledger = new Button("Open ledger");
        ledger.setOnAction(e -> {
            PartyBalance sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) openLedger.accept(sel.party());
        });

        VBox page = Ui.page(Ui.title("Parties (customers & vendors)"), search, table,
                Ui.row(ledger, edit, exportBtn()), new Label("Add / edit party"), form, Ui.row(save, clear));
        VBox.setVgrow(page, javafx.scene.layout.Priority.ALWAYS);
        getChildren().add(page);
    }

    private Button exportBtn() {
        Button b = new Button("Export CSV");
        b.setOnAction(e -> Ui.exportCsv(table, "Parties " + LocalDate.now(), getScene().getWindow()));
        return b;
    }

    private void startEdit() {
        PartyBalance sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) { Ui.error("Select a party first"); return; }
        editing = sel.party();
        name.setText(editing.name());
        type.setValue(editing.type());
        type.setDisable(true);   // type decides the account type, so it can't change later
        phone.setText(editing.phone());
        region.setText(editing.region());
    }

    private void save() {
        boolean ok = Ui.attempt(() -> {
            if (editing == null) {
                ctx.parties.createParty(name.getText(), type.getValue(), phone.getText(), region.getText());
            } else {
                ctx.parties.update(new Party(editing.id(), name.getText(), editing.type(),
                        phone.getText(), region.getText(), editing.accountId()));
            }
        });
        if (ok) { clearForm(); refresh(); }
    }

    private void clearForm() {
        editing = null;
        name.clear(); phone.clear(); region.clear();
        type.setDisable(false);
    }

    @Override
    public void refresh() {
        String q = search.getText() == null ? "" : search.getText().trim().toLowerCase();
        table.getItems().setAll(ctx.reports.partyBalances(LocalDate.now()).stream()
                .filter(pb -> q.isEmpty() || pb.party().name().toLowerCase().contains(q)
                        || (pb.party().region() != null && pb.party().region().toLowerCase().contains(q)))
                .toList());
    }
}
