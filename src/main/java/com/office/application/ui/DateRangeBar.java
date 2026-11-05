package com.office.application.ui;

import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import java.time.LocalDate;
import java.time.YearMonth;

/** "Period: [This month v]  From [..]  To [..]" - calls onChange whenever the range changes. */
final class DateRangeBar extends HBox {

    private final ComboBox<String> preset = new ComboBox<>();
    private final DatePicker from = new DatePicker();
    private final DatePicker to = new DatePicker();
    private boolean updating;

    DateRangeBar(String defaultPreset, Runnable onChange) {
        super(8);
        setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        preset.getItems().addAll("Today", "This month", "Last month", "This year", "Last year", "All time", "Custom");
        from.setPrefWidth(130);
        to.setPrefWidth(130);
        getChildren().addAll(new Label("Period:"), preset, new Label("From"), from, new Label("To"), to);

        preset.setOnAction(e -> {
            applyPreset(preset.getValue());
            onChange.run();
        });
        from.setOnAction(e -> { if (!updating) { preset.setValue("Custom"); onChange.run(); } });
        to.setOnAction(e -> { if (!updating) { preset.setValue("Custom"); onChange.run(); } });

        preset.setValue(defaultPreset);
        applyPreset(defaultPreset);
    }

    private void applyPreset(String p) {
        LocalDate today = LocalDate.now();
        YearMonth month = YearMonth.now();
        updating = true;
        switch (p) {
            case "Today" -> set(today, today);
            case "This month" -> set(month.atDay(1), month.atEndOfMonth());
            case "Last month" -> set(month.minusMonths(1).atDay(1), month.minusMonths(1).atEndOfMonth());
            case "This year" -> set(today.withDayOfYear(1), today.withMonth(12).withDayOfMonth(31));
            case "Last year" -> set(today.minusYears(1).withDayOfYear(1), today.minusYears(1).withMonth(12).withDayOfMonth(31));
            case "All time" -> set(LocalDate.of(2000, 1, 1), today.plusYears(10));
            default -> { }
        }
        updating = false;
    }

    private void set(LocalDate f, LocalDate t) {
        from.setValue(f);
        to.setValue(t);
    }

    LocalDate from() {
        return from.getValue() == null ? LocalDate.of(2000, 1, 1) : from.getValue();
    }

    LocalDate to() {
        return to.getValue() == null ? LocalDate.now() : to.getValue();
    }
}
