package com.tinsider.ui.theme;

import com.tinsider.config.TerminalTheme;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.function.Supplier;

/**
 * Windows Native Desktop Window Manager (DWM) integration via JNA.
 * Handles caption bar coloring, caption text coloring, and theme synchronization
 * across newly opened stages and dialogs.
 */
public final class DwmUtil {

    private DwmUtil() {
        // Utility class
    }

    private interface MyDwmAPI extends com.sun.jna.win32.StdCallLibrary {
        MyDwmAPI INSTANCE = com.sun.jna.Native.load("dwmapi", MyDwmAPI.class, com.sun.jna.win32.W32APIOptions.DEFAULT_OPTIONS);

        int DwmSetWindowAttribute(
            com.sun.jna.platform.win32.WinDef.HWND hwnd,
            int dwAttribute,
            com.sun.jna.ptr.IntByReference pvAttribute,
            int cbAttribute
        );
    }

    /**
     * Applies native DWM title bar caption and text coloring matching the current theme.
     */
    public static void applyNativeDwm(Stage stage, TerminalTheme theme) {
        if (stage == null || theme == null) return;
        try {
            String title = stage.getTitle();
            if (title == null || title.isEmpty()) {
                title = "Custom Color";
                stage.setTitle(title);
            }
            com.sun.jna.platform.win32.WinDef.HWND hwnd = com.sun.jna.platform.win32.User32.INSTANCE.FindWindow(null, title);
            if (hwnd != null) {
                com.jediterm.core.Color titleBg = theme.getColor("titlebar.background", theme.getBackgroundColor());
                com.jediterm.core.Color labelFg = theme.getColor("global.label.foreground", theme.getTextColor());
                int colorref = ((titleBg.getBlue() & 0xFF) << 16) | ((titleBg.getGreen() & 0xFF) << 8) | (titleBg.getRed() & 0xFF);
                int textColor = ((labelFg.getBlue() & 0xFF) << 16) | ((labelFg.getGreen() & 0xFF) << 8) | (labelFg.getRed() & 0xFF);
                MyDwmAPI.INSTANCE.DwmSetWindowAttribute(hwnd, 35, new com.sun.jna.ptr.IntByReference(colorref), 4); // DWMWA_CAPTION_COLOR
                MyDwmAPI.INSTANCE.DwmSetWindowAttribute(hwnd, 36, new com.sun.jna.ptr.IntByReference(textColor), 4); // DWMWA_TEXT_COLOR
            }
        } catch (Throwable ignored) {
            // Safe fallback on non-Windows platforms or if JNA encounters an error
        }
    }

    /**
     * Attaches showing listeners to a Window/Stage to apply DWM styling when displayed.
     */
    public static void applyDwmToWindow(Window win, Supplier<TerminalTheme> themeSupplier) {
        if (win instanceof Stage stage) {
            stage.showingProperty().addListener((obs, wasShowing, isShowing) -> {
                if (isShowing) {
                    Platform.runLater(() -> applyNativeDwm(stage, themeSupplier.get()));
                }
            });
            if (stage.isShowing()) {
                Platform.runLater(() -> applyNativeDwm(stage, themeSupplier.get()));
            }
        }
    }

    /**
     * Styles a newly opened window with the main scene's stylesheets and native DWM title bar styling.
     */
    public static void styleNewWindow(Window win, Scene mainScene, Supplier<TerminalTheme> themeSupplier) {
        if (win.getScene() != null) {
            applyStylesToScene(win.getScene(), mainScene);
        }
        win.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                applyStylesToScene(newScene, mainScene);
            }
        });
        applyDwmToWindow(win, themeSupplier);
    }

    /**
     * Applies stylesheets from the primary scene to the target scene.
     */
    public static void applyStylesToScene(Scene scene, Scene mainScene) {
        if (scene != null && mainScene != null) {
            scene.getStylesheets().clear();
            scene.getStylesheets().addAll(mainScene.getStylesheets());
        }
    }

    /**
     * Registers a global listener for newly created windows so that they receive theme stylesheets and DWM decorations.
     */
    public static void registerWindowStylingListener(Scene mainScene, Supplier<TerminalTheme> themeSupplier) {
        Window.getWindows().addListener((javafx.collections.ListChangeListener<Window>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    for (Window win : change.getAddedSubList()) {
                        styleNewWindow(win, mainScene, themeSupplier);
                    }
                }
            }
        });
    }
}
