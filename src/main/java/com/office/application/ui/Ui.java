package com.office.application.ui;

import com.office.application.service.ValidationException;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.Optional;
import java.util.function.Function;

/** Small shared helpers for building screens. */
final class Ui {
    private Ui() { }

    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");

    static String money(BigDecimal v) {
        return v == null ? "" : MONEY.format(v);
    }

    static BigDecimal parseMoney(String text, String field) {
        if (text == null || text.isBlank()) return null;
        try {
            return new BigDecimal(text.replace(",", "").trim());
        } catch (NumberFormatException e) {
            throw new ValidationException(field + " must be a number");
        }
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
        a.showAndWait();
    }

    static void info(String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        a.setHeaderText(null);
        a.showAndWait();
    }

    static boolean confirm(String message) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        a.setHeaderText(null);
        Optional<ButtonType> r = a.showAndWait();
        return r.isPresent() && r.get() == ButtonType.YES;
    }
}
