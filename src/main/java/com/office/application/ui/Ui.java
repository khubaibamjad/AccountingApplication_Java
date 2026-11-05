package com.office.application.ui;

import com.office.application.service.ValidationException;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Function;

/** Small shared helpers for building screens. */
final class Ui {
    private Ui() { }


    static String money(BigDecimal v) {
        return com.office.application.service.Money.format(v);
    }

    static String qty(BigDecimal v) {
        return com.office.application.service.Money.qty(v);
    }

    static BigDecimal parseMoney(String text, String field) {
        return com.office.application.service.Money.parse(text, field);
    }

    /** Re-shows the typed number with separators when the user leaves the box: 1000000 -> 1,000,000.00 */
    static void moneyField(TextField f) {
        formatOnExit(f, false);
    }

    /** Same for kg: 1250.5 -> 1,250.5 */
    static void qtyField(TextField f) {
        formatOnExit(f, true);
    }

    private static void formatOnExit(TextField f, boolean quantity) {
        f.setStyle("-fx-alignment: CENTER-RIGHT;");
        // block letters and symbols as they are typed or pasted: digits, commas, one '.', 2 (money) or 3 (kg) decimals
        int decimals = quantity ? 3 : 2;
        f.setTextFormatter(new TextFormatter<String>(change ->
                com.office.application.service.Money.isValidInput(change.getControlNewText(), decimals) ? change : null));
        f.focusedProperty().addListener((o, was, now) -> {
            if (now) return;
            try {
                BigDecimal v = com.office.application.service.Money.parse(f.getText(), "value");
                if (v != null) f.setText(quantity ? qty(v) : money(v));
            } catch (ValidationException ignored) {
                // leave the text as typed; saving will show the proper message
            }
        });
    }

    /** NTN box: only digits and dashes can be typed. */
    static void ntnField(TextField f) {
        f.setTextFormatter(new TextFormatter<String>(change ->
                com.office.application.service.Money.isValidNtnInput(change.getControlNewText()) ? change : null));
    }

    /** Phone box: only digits, spaces, + - ( ) can be typed. */
    static void phoneField(TextField f) {
        f.setTextFormatter(new TextFormatter<String>(change ->
                com.office.application.service.Money.isValidPhoneInput(change.getControlNewText()) ? change : null));
    }

    /** Right-aligned quantity column (kg, up to 3 decimals). */
    static <S> TableColumn<S, String> qtyCol(String title, Function<S, BigDecimal> value, double width) {
        TableColumn<S, String> c = col(title, s -> qty(value.apply(s)), width);
        c.setStyle("-fx-alignment: CENTER-RIGHT;");
        return c;
    }

    static <S, T> TableColumn<S, T> col(String title, Function<S, T> value, double width) {
        TableColumn<S, T> c = new TableColumn<>(title);
        c.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(value.apply(cd.getValue())));
        c.setPrefWidth(width);
        return c;
    }

    /** Right-aligned money column. */
    static <S> TableColumn<S, String> moneyCol(String title, Function<S, BigDecimal> value, double width) {
        TableColumn<S, String> c = col(title, s -> money(value.apply(s)), width);
        c.setStyle("-fx-alignment: CENTER-RIGHT;");
        return c;
    }

    static <S> TableView<S> table() {
        TableView<S> t = new TableView<>();
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        t.setPlaceholder(new Label("Nothing to show"));
        VBox.setVgrow(t, Priority.ALWAYS);
        return t;
    }

    static Label title(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("title");
        return l;
    }

    static VBox page(javafx.scene.Node... children) {
        VBox v = new VBox(12, children);
        v.setPadding(new Insets(16));
        return v;
    }

    static GridPane form() {
        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(8);
        g.getColumnConstraints().addAll(new ColumnConstraints(130), new ColumnConstraints(260));
        return g;
    }

    static HBox row(javafx.scene.Node... children) {
        HBox h = new HBox(8, children);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    /** Runs an action and shows a friendly message if a rule is broken. Returns true on success. */
    static boolean attempt(Runnable action) {
        try {
            action.run();
            return true;
        } catch (ValidationException | IllegalArgumentException e) {
            error(e.getMessage());
        } catch (RuntimeException e) {
            e.printStackTrace();
            error("Something went wrong: " + e.getMessage());
        }
        return false;
    }

    static void error(String message) {
        Alert a = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        a.setHeaderText(null);
        a.setTitle(Branding.APP_NAME);
        Branding.apply(a);
        a.showAndWait();
    }

    static void info(String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        a.setHeaderText(null);
        a.setTitle(Branding.APP_NAME);
        Branding.apply(a);
        a.showAndWait();
    }

    /** Reads what the user typed into a DatePicker even if they never pressed Enter. */
    static java.time.LocalDate dateOf(DatePicker picker) {
        String text = picker.getEditor().getText();
        if (text == null || text.isBlank()) return null;
        try {
            java.time.LocalDate d = picker.getConverter().fromString(text.trim());
            picker.setValue(d);
            return d;
        } catch (RuntimeException e) {
            throw new ValidationException("'" + text.trim() + "' is not a valid date");
        }
    }

    /** Saves any table as a CSV file (opens in Excel), using the text shown in each column. */
    static void exportCsv(TableView<?> table, String suggestedName, javafx.stage.Window owner) {
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.setTitle("Export to CSV");
        fc.setInitialFileName(suggestedName.replaceAll("[^A-Za-z0-9 _-]", "") + ".csv");
        fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("CSV (Excel)", "*.csv"));
        java.io.File f = fc.showSaveDialog(owner);
        if (f == null) return;
        attempt(() -> {
            StringBuilder sb = new StringBuilder("\uFEFF");   // BOM so Excel reads UTF-8
            java.util.List<? extends TableColumn<?, ?>> cols = table.getColumns();
            sb.append(cols.stream().map(c -> csv(c.getText())).collect(java.util.stream.Collectors.joining(","))).append("\r\n");
            for (int r = 0; r < table.getItems().size(); r++) {
                final int row = r;
                sb.append(cols.stream().map(c -> {
                    Object v = c.getCellData(row);
                    return csv(v == null ? "" : v.toString());
                }).collect(java.util.stream.Collectors.joining(","))).append("\r\n");
            }
            try {
                java.nio.file.Files.writeString(f.toPath(), sb.toString(), java.nio.charset.StandardCharsets.UTF_8);
            } catch (java.io.IOException e) {
                throw new ValidationException("Could not save the file: " + e.getMessage());
            }
            info("Exported " + table.getItems().size() + " rows to " + f.getName());
        });
    }

    private static String csv(String s) {
        String t = s.strip();
        if (t.startsWith("=") || t.startsWith("+") || t.startsWith("@")) t = "'" + t;   // stop Excel running it as a formula
        return (t.contains(",") || t.contains("\"") || t.contains("\n")) ? "\"" + t.replace("\"", "\"\"") + "\"" : t;
    }

    static boolean confirm(String message) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        a.setHeaderText(null);
        a.setTitle(Branding.APP_NAME);
        Branding.apply(a);
        Optional<ButtonType> r = a.showAndWait();
        return r.isPresent() && r.get() == ButtonType.YES;
    }
}
