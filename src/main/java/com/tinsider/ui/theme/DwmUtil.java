package com.tinsider.ui.theme;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinUser.HMONITOR;
import com.sun.jna.platform.win32.WinUser.MONITORINFO;
import com.sun.jna.platform.win32.WinUser.WINDOWPLACEMENT;
import com.sun.jna.ptr.IntByReference;
import com.tinsider.config.TerminalTheme;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.function.Supplier;

/**
 * Windows Native Desktop Window Manager (DWM) integration via JNA.
 * Handles caption bar coloring, caption text coloring, theme synchronization,
 * and borderless windowed fullscreen mode across newly opened stages and dialogs.
 */
public final class DwmUtil {

    private DwmUtil() {
        // Utility class
    }

    // Win32 Constants for Borderless Windowed Fullscreen
    private static final int GWL_STYLE = -16;
    private static final int WS_OVERLAPPED_WINDOW = 0x00CF0000;
    private static final int WS_MAXIMIZE = 0x01000000;
    private static final int MONITOR_DEFAULT_TO_NEAREST = 2;
    private static final int SWP_NO_SIZE = 0x0001;
    private static final int SWP_NO_MOVE = 0x0002;
    private static final int SWP_NO_Z_ORDER = 0x0004;
    private static final int SWP_NO_OWNER_Z_ORDER = 0x0200;
    private static final int SWP_FRAME_CHANGED = 0x0020;
    private static final int SWP_SHOW_WINDOW = 0x0040;

    private static final WINDOWPLACEMENT prevPlacement = new WINDOWPLACEMENT();
    private static boolean borderlessFullScreen = false;

    private interface MyDwmAPI extends com.sun.jna.win32.StdCallLibrary {
        MyDwmAPI INSTANCE = com.sun.jna.Native.load("dwmapi", MyDwmAPI.class, com.sun.jna.win32.W32APIOptions.DEFAULT_OPTIONS);

        void DwmSetWindowAttribute(
            HWND hwnd,
            int dwAttribute,
            IntByReference pvAttribute,
            int cbAttribute
        );
    }

    /**
     * Retrieves the native Win32 HWND for a JavaFX Stage.
     */
    public static HWND getHwnd(Stage stage) {
        if (stage == null) return null;
        try {
            String title = stage.getTitle();
            if (title == null || title.isEmpty()) {
                title = "T-Insider";
                stage.setTitle(title);
            }
            return User32.INSTANCE.FindWindow(null, title);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Returns whether the application is currently in borderless fullscreen mode.
     */
    public static boolean isBorderlessFullScreen() {
        return borderlessFullScreen;
    }

    /**
     * Toggles borderless windowed fullscreen mode on Windows.
     * When active, the window covers the full monitor (including the taskbar) without
     * entering exclusive OS fullscreen. Clicking on a secondary monitor keeps the
     * window fully visible without minimizing.
     */
    public static boolean toggleBorderlessFullScreen(Stage stage) {
        return setBorderlessFullScreen(stage, !borderlessFullScreen);
    }

    /**
     * Enables or disables borderless windowed fullscreen mode on the specified stage.
     */
    public static boolean setBorderlessFullScreen(Stage stage, boolean enable) {
        if (stage == null) return false;

        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (!isWindows) {
            stage.setMaximized(enable);
            borderlessFullScreen = enable;
            return borderlessFullScreen;
        }

        try {
            HWND hwnd = getHwnd(stage);
            if (hwnd == null) {
                System.err.println("[DwmUtil] Could not find native HWND for stage: " + stage.getTitle());
                stage.setMaximized(enable);
                borderlessFullScreen = enable;
                return borderlessFullScreen;
            }

            int dwStyle = User32.INSTANCE.GetWindowLong(hwnd, GWL_STYLE);

            if (enable) {
                if (!borderlessFullScreen || (dwStyle & WS_OVERLAPPED_WINDOW) != 0) {
                    MONITORINFO mi = new MONITORINFO();
                    mi.cbSize = mi.size();
                    prevPlacement.length = prevPlacement.size();

                    HMONITOR hMonitor = User32.INSTANCE.MonitorFromWindow(hwnd, MONITOR_DEFAULT_TO_NEAREST);
                    com.sun.jna.platform.win32.WinDef.BOOL placementOk = User32.INSTANCE.GetWindowPlacement(hwnd, prevPlacement);
                    com.sun.jna.platform.win32.WinDef.BOOL monitorOk = (hMonitor != null) ? User32.INSTANCE.GetMonitorInfo(hMonitor, mi) : null;

                    if (placementOk != null && placementOk.booleanValue() && monitorOk != null && monitorOk.booleanValue()) {

                        int newStyle = (dwStyle & ~WS_OVERLAPPED_WINDOW) & ~WS_MAXIMIZE;
                        User32.INSTANCE.SetWindowLong(hwnd, GWL_STYLE, newStyle);

                        int x = mi.rcMonitor.left;
                        int y = mi.rcMonitor.top;
                        int width = mi.rcMonitor.right - mi.rcMonitor.left;
                        int height = mi.rcMonitor.bottom - mi.rcMonitor.top;

                        HWND hwndTop = new HWND(Pointer.createConstant(0)); // HWND_TOP
                        User32.INSTANCE.SetWindowPos(
                            hwnd,
                            hwndTop,
                            x, y, width, height,
                            SWP_NO_OWNER_Z_ORDER | SWP_FRAME_CHANGED | SWP_SHOW_WINDOW
                        );

                        borderlessFullScreen = true;
                    }
                }
            } else {
                if (borderlessFullScreen || (dwStyle & WS_OVERLAPPED_WINDOW) == 0) {
                    int newStyle = dwStyle | WS_OVERLAPPED_WINDOW;
                    User32.INSTANCE.SetWindowLong(hwnd, GWL_STYLE, newStyle);
                    User32.INSTANCE.SetWindowPlacement(hwnd, prevPlacement);
                    User32.INSTANCE.SetWindowPos(
                        hwnd,
                        null,
                        0, 0, 0, 0,
                        SWP_NO_MOVE | SWP_NO_SIZE | SWP_NO_Z_ORDER | SWP_NO_OWNER_Z_ORDER | SWP_FRAME_CHANGED
                    );

                    borderlessFullScreen = false;
                }
            }

            Platform.runLater(() -> {
                if (stage.getScene() != null && stage.getScene().getRoot() != null) {
                    stage.getScene().getRoot().requestLayout();
                }
            });

        } catch (Throwable t) {
            System.err.println("[DwmUtil] Failed to set borderless fullscreen: " + t.getMessage());
            t.printStackTrace();
            stage.setMaximized(enable);
            borderlessFullScreen = enable;
        }

        return borderlessFullScreen;
    }

    /**
     * Applies native DWM title bar caption and text coloring matching the current theme.
     */
    public static void applyNativeDwm(Stage stage, TerminalTheme theme) {
        if (stage == null || theme == null) return;
        try {
            HWND hwnd = getHwnd(stage);
            if (hwnd != null) {
                com.jediterm.core.Color titleBg = theme.getColor("titlebar.background", theme.getBackgroundColor());
                com.jediterm.core.Color labelFg = theme.getColor("global.label.foreground", theme.getTextColor());
                int colorRef = ((titleBg.getBlue() & 0xFF) << 16) | ((titleBg.getGreen() & 0xFF) << 8) | (titleBg.getRed() & 0xFF);
                int textColor = ((labelFg.getBlue() & 0xFF) << 16) | ((labelFg.getGreen() & 0xFF) << 8) | (labelFg.getRed() & 0xFF);
                MyDwmAPI.INSTANCE.DwmSetWindowAttribute(hwnd, 35, new com.sun.jna.ptr.IntByReference(colorRef), 4); // DWMWA_CAPTION_COLOR
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
