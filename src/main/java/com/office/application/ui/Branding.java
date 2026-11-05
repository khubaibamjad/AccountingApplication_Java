package com.office.application.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.Dialog;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;

/** App name, window icons and logo in one place. */
public final class Branding {
    private Branding() { }

    public static final String APP_NAME = "Texaxis Accounts";

    private static List<Image> icons;

    static List<Image> icons() {
        if (icons == null) {
            icons = new ArrayList<>();
            for (int s : new int[]{16, 32, 48, 64, 128, 256}) {
                var in = Branding.class.getResourceAsStream("/branding/icon-" + s + ".png");
                if (in != null) icons.add(new Image(in));
            }
        }
        return icons;
    }

    /** Puts the Texaxis icon on a window (title bar + taskbar). */
    static void apply(Window w) {
        if (w instanceof Stage s && s.getIcons().isEmpty()) s.getIcons().setAll(icons());
    }

    static void apply(Dialog<?> d) {
        apply(d.getDialogPane().getScene().getWindow());
    }

    static void apply(Alert a) {
        apply(a.getDialogPane().getScene().getWindow());
    }

    /** The full TEXAXIS wordmark, scaled to the given height. */
    static ImageView logo(double height) {
        var in = Branding.class.getResourceAsStream("/branding/logo.png");
        ImageView v = in == null ? new ImageView() : new ImageView(new Image(in));
        v.setFitHeight(height);
        v.setPreserveRatio(true);
        v.setSmooth(true);
        return v;
    }
}
