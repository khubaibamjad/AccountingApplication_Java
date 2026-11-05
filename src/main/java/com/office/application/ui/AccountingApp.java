package com.office.application.ui;

import com.office.application.config.AppConfig;
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

    private Stage stage;
    private AppContext ctx;
    private AppConfig config;
    private javafx.animation.PauseTransition idle;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        // any unexpected error anywhere: show it instead of failing silently
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            e.printStackTrace();
            javafx.application.Platform.runLater(() -> Ui.error("Unexpected error: " + e));
        });

        DatabaseManager db;
        try {
            config = AppConfig.load();
            db = new DatabaseManager(config);
            db.migrate();
            ctx = new AppContext(db);
        } catch (RuntimeException e) {
            e.printStackTrace();
            Ui.error("The app could not start:\n\n" + e.getMessage()
                    + "\n\nSettings and data folder: " + (config != null ? config.dataDir() : System.getProperty("user.home") + "/AccountingApp"));
            javafx.application.Platform.exit();
            return;
        }

        // background: daily safety copy + quick corruption check (startup stays instant)
        Thread maintenance = new Thread(() -> {
            try {
                String health = db.integrityCheck();
                if (!"ok".equalsIgnoreCase(health))
                    javafx.application.Platform.runLater(() -> Ui.error("Database check found a problem:\n" + health
                            + "\n\nStop using the app and restore yesterday's backup (File > Show data & backups folder)."));
                else
                    db.dailyBackup(config.backupKeepDays());
            } catch (RuntimeException ex) {
                ex.printStackTrace();
            }
        }, "maintenance");
        maintenance.setDaemon(true);
        maintenance.start();

        LoginWindow.show(ctx.auth, config.companyName(), () -> openMain(db), javafx.application.Platform::exit);
    }

    /** Builds the main window the first time; afterwards just shows it again (after an unlock). */
    private void openMain(DatabaseManager db) {
        if (tabs != null) {
            stage.setTitle(title());
            stage.show();
            restartIdleTimer();
            refresh(tabs.getSelectionModel().getSelectedItem());
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
        MenuItem folder = new MenuItem("Show data, settings & backups folder");
        folder.setOnAction(e -> Ui.info("Data: " + db.getDbFile()
                + "\n\nSettings: " + config.configFile()
                + "\n\nAutomatic daily backups (last " + config.backupKeepDays() + " days): " + config.backupDir()
                + "\n\nTo restore: close the app, then copy a backup over AccountingDB."));
        MenuItem exit = new MenuItem("Exit");
        exit.setOnAction(e -> { ctx.auth.logout(); javafx.application.Platform.exit(); });

        MenuItem lock = new MenuItem("Lock now");
        lock.setOnAction(e -> lock(db));
        MenuItem changePw = new MenuItem("Change my password...");
        changePw.setOnAction(e -> AdminDialogs.changePassword(ctx.auth, stage));
        MenuItem activity = new MenuItem("Activity log...");
        activity.setOnAction(e -> AdminDialogs.activityLog(ctx.audit, stage));

        MenuBar menu = new MenuBar(
                new Menu("File", null, backup, folder, new SeparatorMenuItem(), exit),
                new Menu("Admin", null, lock, changePw, new SeparatorMenuItem(), activity));

        BorderPane root = new BorderPane(tabs);
        root.setTop(menu);
        Scene scene = new Scene(root, 1100, 720);
        scene.getStylesheets().add(getClass().getResource("/app.css").toExternalForm());

        // auto-lock after N idle minutes
        if (config.autoLockMinutes() > 0) {
            idle = new javafx.animation.PauseTransition(javafx.util.Duration.minutes(config.autoLockMinutes()));
            idle.setOnFinished(e -> { if (stage.isShowing()) lock(db); });
            scene.addEventFilter(javafx.scene.input.InputEvent.ANY, e -> restartIdleTimer());
        }
        stage.setOnCloseRequest(e -> { ctx.auth.logout(); javafx.application.Platform.exit(); });
        stage.setTitle(title());
        Branding.apply(stage);
        stage.setScene(scene);
        stage.show();
        restartIdleTimer();
        refresh(tabs.getSelectionModel().getSelectedItem());
    }

    private void lock(DatabaseManager db) {
        if (idle != null) idle.stop();
        ctx.auth.logout();
        // hide every other open window too (activity log, dialogs) so no data stays on screen
        for (javafx.stage.Window w : new java.util.ArrayList<>(javafx.stage.Window.getWindows()))
            if (w != stage) w.hide();
        stage.hide();
        LoginWindow.show(ctx.auth, config.companyName(), () -> openMain(db), javafx.application.Platform::exit);
    }

    private void restartIdleTimer() {
        if (idle != null) idle.playFromStart();
    }

    private String title() {
        String company = config.companyName();
        return (company.equalsIgnoreCase(Branding.APP_NAME) ? company : Branding.APP_NAME + " - " + company)
                + "  (" + com.office.application.security.Session.username() + ")";
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
        fc.setInitialFileName("TexaxisAccounts-backup-" + LocalDate.now() + ".db");
        File target = fc.showSaveDialog(stage);
        if (target == null) return;
        if (Ui.attempt(() -> db.backupTo(target.toPath()))) Ui.info("Backup saved to " + target);
    }
}
