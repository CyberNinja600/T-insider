package com.tinsider.ui.components.toolbar;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.stage.Stage;

public class CustomTitleBar extends HBox {
    private double xOffset = 0;
    private double yOffset = 0;
    private double restoredWidth = 800;
    private double restoredHeight = 500;
    private boolean isSnapped = false;

    // Pixel-perfect unscaled 12x12 vector designs matching Heroicons style
    private static final String SVG_MINIMIZE = "M 1 6 L 11 6";
    private static final String SVG_MAXIMIZE = "M 1 1 H 11 V 11 H 1 Z";
    private static final String SVG_RESTORE = "M 1 4 H 8 V 11 H 1 Z M 4 1 H 11 V 8 H 8";
    private static final String SVG_CLOSE = "M 2 2 L 10 10 M 2 10 L 10 2";

    public CustomTitleBar(Stage stage) {
        super(10);
        setPadding(new Insets(0, 0, 0, 10));
        setPrefHeight(36);
        setMinHeight(36);
        setAlignment(Pos.CENTER_LEFT);
        getStyleClass().add("custom-title-bar");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button minBtn = new Button();
        minBtn.setGraphic(createHeroIcon(SVG_MINIMIZE, minBtn));
        minBtn.getStyleClass().add("title-btn");
        minBtn.setFocusTraversable(false);
        minBtn.setOnAction(e -> stage.setIconified(true));

        Button maxBtn = new Button();
        SVGPath maxIcon = createHeroIcon(SVG_MAXIMIZE, maxBtn);
        maxBtn.setGraphic(maxIcon);
        maxBtn.getStyleClass().add("title-btn");
        maxBtn.setFocusTraversable(false);
        maxBtn.setOnAction(e -> {
            if (stage.isMaximized()) {
                stage.setMaximized(false);
            } else {
                stage.setMaximized(true);
            }
        });

        // Dynamically toggle the SVG path data based on maximized state
        stage.maximizedProperty().addListener((obs, wasMax, isMax) -> {
            if (isMax) {
                maxIcon.setContent(SVG_RESTORE);
            } else {
                maxIcon.setContent(SVG_MAXIMIZE);
            }
        });

        Button closeBtn = new Button();
        closeBtn.setGraphic(createHeroIcon(SVG_CLOSE, closeBtn));
        closeBtn.getStyleClass().add("title-btn-close");
        closeBtn.setFocusTraversable(false);
        closeBtn.setOnAction(e -> {
            stage.close();
            Platform.exit();
            System.exit(0);
        });

        HBox controls = new HBox(0, minBtn, maxBtn, closeBtn);
        controls.setAlignment(Pos.TOP_RIGHT);
        controls.setTranslateX(4.0); // Shift 4px right to compensate for WS_THICKFRAME native resize borders

        getChildren().addAll(spacer, controls);

        // Native Win32 window dragging listener
        setOnMousePressed(event -> {
            if (event.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                startNativeDrag(stage);
            }
        });
    }

    private interface MyUser32 extends com.sun.jna.win32.StdCallLibrary {
        MyUser32 INSTANCE = com.sun.jna.Native.load("user32", MyUser32.class, com.sun.jna.win32.W32APIOptions.DEFAULT_OPTIONS);
        
        boolean ReleaseCapture();
        
        int SendMessage(
            com.sun.jna.platform.win32.WinDef.HWND hWnd, 
            int msg, 
            com.sun.jna.platform.win32.WinDef.WPARAM wParam, 
            com.sun.jna.platform.win32.WinDef.LPARAM lParam
        );
        
        com.sun.jna.platform.win32.WinDef.HWND FindWindow(String lpClassName, String lpWindowName);
    }

    private void startNativeDrag(Stage stage) {
        try {
            String title = stage.getTitle();
            if (title != null && !title.isEmpty()) {
                com.sun.jna.platform.win32.WinDef.HWND hwnd = MyUser32.INSTANCE.FindWindow(null, title);
                if (hwnd != null) {
                    MyUser32.INSTANCE.ReleaseCapture();
                    MyUser32.INSTANCE.SendMessage(
                        hwnd, 
                        0x00A1, // WM_NCLBUTTONDOWN
                        new com.sun.jna.platform.win32.WinDef.WPARAM(2), // HTCAPTION
                        new com.sun.jna.platform.win32.WinDef.LPARAM(0)
                    );
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private SVGPath createHeroIcon(String pathData, Button parentBtn) {
        SVGPath icon = new SVGPath();
        icon.setContent(pathData);
        icon.setFill(Color.TRANSPARENT);
        icon.setStrokeWidth(1.2); // Uniform crisp outline
        icon.setStrokeLineCap(StrokeLineCap.ROUND);
        icon.setStrokeLineJoin(StrokeLineJoin.ROUND);
        // Bind the stroke color of the SVG to the text-fill color of the Button so CSS hover states work seamlessly!
        icon.strokeProperty().bind(parentBtn.textFillProperty());
        return icon;
    }
}
