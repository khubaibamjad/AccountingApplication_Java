package com.office.application.ui;

import com.office.application.security.AuthService;
import com.office.application.security.Session;
import com.office.application.service.ValidationException;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Admin login (username is always "admin"). If the admin still has the default password,
 * the same window then requires a new password before the books open.
 */
final class LoginWindow {

    private LoginWindow() { }

    static void show(AuthService auth, String company, Runnable onSuccess, Runnable onCancel) {
        auth.ensureDefaultAdmin();
        Stage stage = new Stage();
        stage.setTitle(company + " - Login");
        stage.setResizable(false);

        PasswordField pass = new PasswordField();
        PasswordField next = new PasswordField();
        PasswordField confirm = new PasswordField();
        Label error = new Label();
        error.setStyle("-fx-text-fill: #c0392b;");
        error.setWrapText(true);
        error.setMaxWidth(340);
        Label sub = new Label("Log in as admin");
        sub.getStyleClass().add("hint");
        sub.setWrapText(true);
        sub.setMaxWidth(340);
        Button ok = new Button("Log in");
        ok.setDefaultButton(true);

        TextField user = new TextField(AuthService.ADMIN);
        user.setEditable(false);
        user.setFocusTraversable(false);
        GridPane g = Ui.form();
        g.getColumnConstraints().get(1).setPrefWidth(200);
        g.addRow(0, new Label("Username"), user);
        g.addRow(1, new Label("Password"), pass);

        VBox box = new VBox(12, Ui.title(company), sub, g, ok, error);
        box.setPadding(new Insets(24));

        final boolean[] done = {false};
        final boolean[] changing = {false};
        ok.setOnAction(e -> {
            try {
                if (!changing[0]) {
                    auth.login(pass.getText().toCharArray());
                    pass.clear();
                    if (auth.mustChangePassword()) {          // first login: switch the form to "set new password"
                        changing[0] = true;
                        g.getChildren().clear();
                        g.addRow(0, new Label("Current password"), pass);
                        g.addRow(1, new Label("New password"), next);
                        g.addRow(2, new Label("Confirm new"), confirm);
                        sub.setText("First login: set your own password now. 8+ characters with a letter and a number. "
                                + "The default password will stop working.");
                        ok.setText("Save password & open");
                        error.setText("");
                        pass.requestFocus();
                        stage.sizeToScene();
                        return;
                    }
                } else {
                    auth.changePassword(Session.username(), pass.getText().toCharArray(),
                            next.getText().toCharArray(), confirm.getText().toCharArray());
                }
                done[0] = true;
                stage.close();
                onSuccess.run();
            } catch (ValidationException ex) {
                error.setText(ex.getMessage());
                pass.clear(); next.clear(); confirm.clear();
                pass.requestFocus();
            } catch (RuntimeException ex) {
                ex.printStackTrace();
                error.setText("Error: " + ex.getMessage());
            }
        });
        // closing the window without finishing (also mid password change) never opens the books
        stage.setOnHidden(e -> { if (!done[0]) { auth.logout(); onCancel.run(); } });

        Scene scene = new Scene(box);
        scene.getStylesheets().add(LoginWindow.class.getResource("/app.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
        pass.requestFocus();
    }
}
