package com.office.application.ui;

import com.office.application.security.AuditLog;
import com.office.application.security.AuthService;
import com.office.application.security.Session;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Change password, activity log. */
final class AdminDialogs {
    private AdminDialogs() { }

    static void changePassword(AuthService auth, Window owner) {
        PasswordField cur = new PasswordField(), next = new PasswordField(), conf = new PasswordField();
        GridPane g = Ui.form();
        g.addRow(0, new Label("Current password"), cur);
        g.addRow(1, new Label("New password"), next);
        g.addRow(2, new Label("Confirm new"), conf);
        if (dialog("Change password", g, owner)) {
            if (Ui.attempt(() -> auth.changePassword(Session.username(),
                    cur.getText().toCharArray(), next.getText().toCharArray(), conf.getText().toCharArray())))
                Ui.info("Password changed.");
        }
    }

    static void activityLog(AuditLog audit, Window owner) {
        TableView<AuditLog.Entry> t = Ui.table();
        t.getColumns().addAll(
                Ui.col("When", AuditLog.Entry::at, 150),
                Ui.col("User", AuditLog.Entry::username, 100),
                Ui.col("Action", AuditLog.Entry::action, 140),
                Ui.col("Details", AuditLog.Entry::details, 520));
        Ui.attempt(() -> t.getItems().setAll(audit.latest(2000)));
        Button export = new Button("Export CSV");
        export.setOnAction(e -> Ui.exportCsv(t, "Activity log " + java.time.LocalDate.now(), t.getScene().getWindow()));
        VBox page = Ui.page(Ui.title("Activity log (latest 2000)"), t, export);
        Stage s = new Stage();
        s.initOwner(owner);
        s.setTitle(Branding.APP_NAME + " - Activity log");
        Branding.apply(s);
        Scene scene = new Scene(page, 960, 560);
        scene.getStylesheets().add(AdminDialogs.class.getResource("/app.css").toExternalForm());
        s.setScene(scene);
        s.show();
    }

    private static boolean dialog(String title, GridPane content, Window owner) {
        Dialog<ButtonType> d = new Dialog<>();
        d.initOwner(owner);
        d.setTitle(title);
        d.setHeaderText(null);
        d.getDialogPane().setContent(content);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Branding.apply(d);
        return d.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
    }

    /** Shows the PC security results. */
    static void securityReport(java.util.List<com.office.application.security.SystemSecurityCheck.Item> items, Window owner) {
        TableView<com.office.application.security.SystemSecurityCheck.Item> t = Ui.table();
        t.getColumns().addAll(
                Ui.col("", i -> switch (i.status()) { case OK -> "✔"; case WARNING -> "⚠"; default -> "?"; }, 40),
                Ui.col("Check", com.office.application.security.SystemSecurityCheck.Item::name, 170),
                Ui.col("Result", com.office.application.security.SystemSecurityCheck.Item::detail, 330),
                Ui.col("How to fix", com.office.application.security.SystemSecurityCheck.Item::howToFix, 420));
        t.getItems().setAll(items);
        long w = com.office.application.security.SystemSecurityCheck.warnings(items);
        Label summary = new Label(w == 0 ? "No problems found." : w + " problem(s) found. Please fix them to keep the books safe.");
        summary.setStyle(w == 0 ? "-fx-text-fill: #1e8449;" : "-fx-text-fill: #c0392b; -fx-font-weight: bold;");
        VBox page = Ui.page(Ui.title("PC security check"), summary, t,
                new Label("This check only reads Windows settings; it never changes anything."));
        Stage s = new Stage();
        s.initOwner(owner);
        s.setTitle(Branding.APP_NAME + " - PC security check");
        Branding.apply(s);
        Scene scene = new Scene(page, 1000, 420);
        scene.getStylesheets().add(AdminDialogs.class.getResource("/app.css").toExternalForm());
        s.setScene(scene);
        s.show();
    }
}
