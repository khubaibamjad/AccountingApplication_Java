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
    private final ComboBox<String> taxStatus = new ComboBox<>();
    private final TextField ntn = new TextField();
    private final ComboBox<String> filter = new ComboBox<>();
    private Party editing;

    PartiesView(AppContext ctx, Consumer<Party> openLedger) {
        this.ctx = ctx;
        type.getItems().setAll(PartyType.values());
        type.setValue(PartyType.VENDOR);
        type.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(PartyType t) { return t == null ? "" : t.label(); }
            @Override public PartyType fromString(String s) { return null; }
        });
        search.setPromptText("Search name, region or NTN");
        search.textProperty().addListener((o, a, b) -> refresh());
        filter.getItems().setAll("All parties", "Registered only", "Unregistered only");
        filter.setValue("All parties");
        filter.setOnAction(e -> refresh());

        taxStatus.getItems().setAll("Unregistered", "Registered");
        taxStatus.setValue("Unregistered");
        ntn.setPromptText("required when registered, e.g. 1234567-8");
        ntn.setDisable(true);
        taxStatus.setOnAction(e -> {
            boolean reg = "Registered".equals(taxStatus.getValue());
            ntn.setDisable(!reg);
            if (!reg) ntn.clear();
        });

        table.getColumns().addAll(
                Ui.col("Name", pb -> pb.party().name(), 200),
                Ui.col("Type", pb -> pb.party().type().label(), 120),
                Ui.col("Phone", pb -> pb.party().phone(), 120),
                Ui.col("Region", pb -> pb.party().region(), 110),
                Ui.col("Tax status", pb -> pb.party().status(), 100),
                Ui.col("NTN", pb -> pb.party().ntn(), 110),
                Ui.col("Balance status", PartyBalance::status, 120),
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
        form.addRow(2, new Label("Tax status"), taxStatus);
        form.addRow(3, new Label("NTN"), ntn);
        form.addRow(4, new Label("Phone"), phone);
        form.addRow(5, new Label("Region"), region);

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

        VBox page = Ui.page(Ui.title("Parties (customers & vendors)"), Ui.row(search, filter), table,
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
        taxStatus.setValue(editing.status());
        ntn.setDisable(!editing.registered());
        ntn.setText(editing.ntn());
    }

    private void save() {
        boolean ok = Ui.attempt(() -> {
            boolean reg = "Registered".equals(taxStatus.getValue());
            if (name.getText() == null || name.getText().isBlank())
                throw new com.office.application.service.ValidationException("Party name is required");
            if (editing == null) {
                ctx.parties.createParty(name.getText(), type.getValue(), phone.getText(), region.getText(), reg, ntn.getText());
            } else {
                ctx.parties.update(new Party(editing.id(), name.getText(), editing.type(),
                        phone.getText(), region.getText(), editing.accountId(), reg, ntn.getText()));
            }
        });
        if (ok) { clearForm(); refresh(); }
    }

    private void clearForm() {
        editing = null;
        name.clear(); phone.clear(); region.clear(); ntn.clear();
        taxStatus.setValue("Unregistered");
        ntn.setDisable(true);
        type.setDisable(false);
    }

    @Override
    public void refresh() {
        String q = search.getText() == null ? "" : search.getText().trim().toLowerCase();
        String f = filter.getValue();
        table.getItems().setAll(ctx.reports.partyBalances(LocalDate.now()).stream()
                .filter(pb -> !"Registered only".equals(f) || pb.party().registered())
                .filter(pb -> !"Unregistered only".equals(f) || !pb.party().registered())
                .filter(pb -> q.isEmpty() || pb.party().name().toLowerCase().contains(q)
                        || (pb.party().region() != null && pb.party().region().toLowerCase().contains(q))
                        || (pb.party().ntn() != null && pb.party().ntn().contains(q)))
                .toList());
    }
}
