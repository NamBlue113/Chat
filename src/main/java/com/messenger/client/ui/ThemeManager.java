package com.messenger.client.ui;

import javafx.scene.Scene;

public class ThemeManager {

    private boolean darkMode = true;

    private static final String DARK_THEME = "/css/dark-theme.css";
    private static final String LIGHT_THEME = "/css/light-theme.css";

    public ThemeManager() {
        this.darkMode = true;
    }

    public void applyTo(Scene scene) {
        if (scene == null) return;
        String theme = darkMode ? DARK_THEME : LIGHT_THEME;
        scene.getStylesheets().clear();
        scene.getStylesheets().add(getClass().getResource(theme).toExternalForm());
    }

    public void toggle(Scene scene) {
        darkMode = !darkMode;
        applyTo(scene);
    }

    public boolean isDarkMode() {
        return darkMode;
    }

    public void setDarkMode(boolean dark) {
        this.darkMode = dark;
    }
}
