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
        s.setTitle("Activity log");
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
        return d.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
    }
}
