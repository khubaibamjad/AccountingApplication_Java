package com.office.application.ui;

import com.office.application.db.DatabaseManager;
import com.office.application.model.Account;
import com.office.application.model.Party;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.time.LocalDate;

public class AccountingApp extends Application {

    private TabPane tabs;
    private LedgerView ledger;
    private Tab ledgerTab;

    @Override
    public void start(Stage stage) {
        // any unexpected error anywhere: show it instead of failing silently
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            e.printStackTrace();
            javafx.application.Platform.runLater(() -> Ui.error("Unexpected error: " + e));
        });

        DatabaseManager db;
        AppContext ctx;
        try {
            db = new DatabaseManager();
            db.migrate();
            ctx = new AppContext(db);
            // automatic daily safety copy, in the background so startup stays instant
            Thread backup = new Thread(() -> {
                try { db.dailyBackup(); } catch (RuntimeException ex) { ex.printStackTrace(); }
            }, "daily-backup");
            backup.setDaemon(true);
            backup.start();
        } catch (RuntimeException e) {
            e.printStackTrace();
            Ui.error("The database could not be opened:\n\n" + e.getMessage()
                    + "\n\nYour data is in " + System.getProperty("user.home") + "/AccountingApp. The app will close.");
            javafx.application.Platform.exit();
            return;
        }

        ledger = new LedgerView(ctx);
        ledgerTab = tab("Ledger", ledger);
        tabs = new TabPane(
                tab("Dashboard", new DashboardView(ctx)),
                tab("New Transaction", new NewTransactionView(ctx)),
                tab("Transactions", new TransactionsView(ctx)),
                tab("Parties", new PartiesView(ctx, (Party p) -> openLedger(p.accountId()))),
                tab("Accounts", new AccountsView(ctx, (Account a) -> openLedger(a.id()))),
                ledgerTab,
                tab("Reports", new ReportsView(ctx)));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        // reload a screen's data every time its tab is opened
        tabs.getSelectionModel().selectedItemProperty().addListener((o, old, now) -> refresh(now));

        MenuItem backup = new MenuItem("Backup database...");
        backup.setOnAction(e -> backup(stage, db));
        MenuItem folder = new MenuItem("Show data & backups folder");
        folder.setOnAction(e -> Ui.info("Your data: " + db.getDbFile()
                + "\n\nAutomatic daily backups (last 30 days): " + db.getDbFile().getParent().resolve("backups")
                + "\n\nTo restore: close the app, then copy a backup over AccountingDB."));
        MenuItem exit = new MenuItem("Exit");
        exit.setOnAction(e -> stage.close());
        MenuBar menu = new MenuBar(new Menu("File", null, backup, folder, new SeparatorMenuItem(), exit));

        BorderPane root = new BorderPane(tabs);
        root.setTop(menu);
        Scene scene = new Scene(root, 1100, 720);
        scene.getStylesheets().add(getClass().getResource("/app.css").toExternalForm());
        stage.setTitle("Office Ledger");
        stage.setScene(scene);
        stage.show();
        refresh(tabs.getSelectionModel().getSelectedItem());
    }

    private static Tab tab(String title, javafx.scene.Node content) {
        Tab t = new Tab(title, new ScrollPane(content) {{
            setFitToWidth(true);
            setFitToHeight(true);
        }});
        t.setUserData(content);
        return t;
    }

    private void refresh(Tab t) {
        if (t != null && t.getUserData() instanceof Refreshable r) Ui.attempt(r::refresh);
    }

    private void openLedger(java.util.UUID accountId) {
        tabs.getSelectionModel().select(ledgerTab);
        ledger.show(accountId);
    }

    private void backup(Stage stage, DatabaseManager db) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save backup");
        fc.setInitialFileName("OfficeLedger-backup-" + LocalDate.now() + ".db");
        File target = fc.showSaveDialog(stage);
        if (target == null) return;
        if (Ui.attempt(() -> db.backupTo(target.toPath()))) Ui.info("Backup saved to " + target);
    }
}
