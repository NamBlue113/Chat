package com.messenger.client.ui;

import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.text.Font;

import java.io.InputStream;

/**
 * Utility for loading icon images from /icon/ directory.
 * Falls back to Unicode emoji text if the image file is missing.
 */
public class IconManager {

    private static final String ICON_PATH = "/icon/";
    private static final double ICON_SIZE = 20;
    private static final String TOOLTIP_STYLE =
        "-fx-background-color: #2C2F45; -fx-text-fill: #E0E0E0; -fx-background-radius: 6; -fx-padding: 6 10; -fx-font-size: 12px;";

    private static Tooltip makeTooltip(String text) {
        if (text == null || text.isEmpty()) return null;
        Tooltip tt = new Tooltip(text);
        tt.setStyle(TOOLTIP_STYLE);
        return tt;
    }

    public static Image loadImage(String iconName) {
        String path = ICON_PATH + iconName;
        try (InputStream is = IconManager.class.getResourceAsStream(path)) {
            if (is != null) {
                return new Image(is);
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static ImageView createIconView(String iconName, double size) {
        Image img = loadImage(iconName);
        if (img != null) {
            ImageView iv = new ImageView(img);
            iv.setFitWidth(size);
            iv.setFitHeight(size);
            iv.setPreserveRatio(true);
            return iv;
        }
        return null;
    }

    public static Button createIconButton(String iconName, String emojiFallback, String tooltip) {
        Button btn = new Button();
        btn.getStyleClass().add("btn-icon");
        Tooltip tt = makeTooltip(tooltip);
        if (tt != null) btn.setTooltip(tt);

        ImageView iv = createIconView(iconName, ICON_SIZE);
        if (iv != null) {
            btn.setGraphic(iv);
        } else {
            btn.setText(emojiFallback);
            btn.setFont(Font.font(18));
        }
        return btn;
    }

    public static Button createHeaderButton(String iconName, String emojiFallback, String tooltip) {
        Button btn = new Button();
        btn.getStyleClass().add("btn-header-action");
        Tooltip tt = makeTooltip(tooltip);
        if (tt != null) btn.setTooltip(tt);

        ImageView iv = createIconView(iconName, 22);
        if (iv != null) {
            btn.setGraphic(iv);
        } else {
            btn.setText(emojiFallback);
            btn.setFont(Font.font(18));
        }
        return btn;
    }

    public static Button createInputButton(String iconName, String emojiFallback, String tooltip) {
        Button btn = new Button();
        btn.getStyleClass().add("btn-input-icon");
        Tooltip tt = makeTooltip(tooltip);
        if (tt != null) btn.setTooltip(tt);

        ImageView iv = createIconView(iconName, 20);
        if (iv != null) {
            btn.setGraphic(iv);
        } else {
            btn.setText(emojiFallback);
            btn.setFont(Font.font(18));
        }
        return btn;
    }

    public static void setTooltip(javafx.scene.Node node, String text) {
        if (text != null && !text.isEmpty()) {
            Tooltip tt = makeTooltip(text);
            if (tt != null) Tooltip.install(node, tt);
        }
    }
}
