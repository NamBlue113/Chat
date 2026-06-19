package com.messenger.client.ui;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.AWTException;
import java.awt.Image;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.TrayIcon.MessageType;
import java.net.URL;

public class NotificationManager {

    private static final Logger logger = LoggerFactory.getLogger(NotificationManager.class);

    private volatile TrayIcon trayIcon;
    private volatile boolean traySupported;
    private final Object trayLock = new Object();

    public NotificationManager() {
        traySupported = SystemTray.isSupported();
        if (traySupported) {
            // Initialize tray icon on a background thread to avoid blocking
            // the JavaFX Application Thread and to prevent AWT/JavaFX conflicts
            new Thread(() -> {
                try {
                    SystemTray tray = SystemTray.getSystemTray();
                    URL iconUrl = getClass().getResource("/images/app-icon.png");
                    Image image;
                    if (iconUrl != null) {
                        image = Toolkit.getDefaultToolkit().getImage(iconUrl);
                    } else {
                        image = Toolkit.getDefaultToolkit().createImage(new byte[0]);
                    }
                    synchronized (trayLock) {
                        trayIcon = new TrayIcon(image, "Messenger");
                        trayIcon.setImageAutoSize(true);
                        trayIcon.addActionListener(e -> {
                            // Restore window on double-click
                            Platform.runLater(() -> {
                                if (com.messenger.client.ChatClient.getInstance() != null) {
                                    var stage = com.messenger.client.ChatClient.getInstance().getPrimaryStage();
                                    if (stage != null) {
                                        stage.setIconified(false);
                                        stage.toFront();
                                    }
                                }
                            });
                        });
                        tray.add(trayIcon);
                    }
                    logger.info("System tray initialized");
                } catch (AWTException e) {
                    logger.warn("Failed to initialize system tray: {}", e.getMessage());
                    traySupported = false;
                } catch (Exception e) {
                    logger.warn("Unexpected error initializing system tray: {}", e.getMessage());
                    traySupported = false;
                }
            }, "tray-init").start();
        }
    }

    public void showNotification(String title, String message) {
        if (!traySupported) return;
        TrayIcon icon;
        synchronized (trayLock) {
            icon = trayIcon;
        }
        if (icon != null) {
            try {
                icon.displayMessage(title, message, MessageType.INFO);
            } catch (Exception e) {
                logger.warn("Failed to show notification: {}", e.getMessage());
            }
        }
    }

    public void showWarning(String title, String message) {
        if (!traySupported) return;
        TrayIcon icon;
        synchronized (trayLock) {
            icon = trayIcon;
        }
        if (icon != null) {
            try {
                icon.displayMessage(title, message, MessageType.WARNING);
            } catch (Exception e) {
                logger.warn("Failed to show warning: {}", e.getMessage());
            }
        }
    }

    public void showError(String title, String message) {
        if (!traySupported) return;
        TrayIcon icon;
        synchronized (trayLock) {
            icon = trayIcon;
        }
        if (icon != null) {
            try {
                icon.displayMessage(title, message, MessageType.ERROR);
            } catch (Exception e) {
                logger.warn("Failed to show error notification: {}", e.getMessage());
            }
        }
    }

    public void shutdown() {
        if (!traySupported) return;
        synchronized (trayLock) {
            if (trayIcon != null) {
                try {
                    SystemTray.getSystemTray().remove(trayIcon);
                } catch (Exception e) {
                    logger.warn("Failed to remove tray icon: {}", e.getMessage());
                }
                trayIcon = null;
            }
        }
    }
}
