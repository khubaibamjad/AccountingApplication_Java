package com.office.ledger;

import com.office.ledger.config.AppConfig;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    static void main() {
        var config = AppConfig.load();
        System.out.println("config is null? " + (config == null));
        System.out.println(config);
    }
}
