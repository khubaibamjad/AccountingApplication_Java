package com.office.application;

import com.office.application.ui.AccountingApp;
import javafx.application.Application;

/**
 * Entry point. Launching through a class that does NOT extend Application
 * lets the app run from a plain jar without JavaFX module flags.
 */
public class Main {
    public static void main(String[] args) {
        Application.launch(AccountingApp.class, args);
    }
}
