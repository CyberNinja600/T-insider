package com.tinsider.ui.dialogs;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.StageStyle;

public class AlertHelper {
    
    private static double xOffset = 0;
    private static double yOffset = 0;

    public static void showErrorAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(title);
        alert.setContentText(content);
        themeDialog(alert);
        alert.showAndWait();
    }

    public static void themeDialog(Dialog<?> dialog) {
        // 1. Strip OS Windows Borders
        dialog.initStyle(StageStyle.TRANSPARENT);

        // 2. Read Existing Properties
        String titleText = dialog.getTitle() != null ? dialog.getTitle() : "";
        String headerText = dialog.getDialogPane().getHeaderText();

        // 3. Build Custom Title Bar
        HBox titleBar = new HBox();
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setPadding(new Insets(0, 0, 0, 15));
        
        titleBar.setStyle("-fx-background-color: transparent; -fx-cursor: move;");

        Label titleLabel = new Label(titleText);
        titleLabel.getStyleClass().add("dialog-header-title"); // Mapped to CSS inside MainApp

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().addAll("button", "title-btn-close");
        closeBtn.setStyle("-fx-pref-height: 38px; -fx-min-height: 38px; -fx-max-height: 38px; " +
                          "-fx-background-radius: 0 8px 0 0; -fx-border-radius: 0 8px 0 0;");
        closeBtn.setOnAction(e -> {
            dialog.setResult(null);
            dialog.close();
        });

        titleBar.getChildren().addAll(titleLabel, spacer, closeBtn);

        // 4. Implement Dragging on Title Bar
        titleBar.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });
        titleBar.setOnMouseDragged(event -> {
            if (dialog.getDialogPane().getScene().getWindow() != null) {
                dialog.getDialogPane().getScene().getWindow().setX(event.getScreenX() - xOffset);
                dialog.getDialogPane().getScene().getWindow().setY(event.getScreenY() - yOffset);
            }
        });

        // 5. Replace Standard Header with just the Title Bar
        VBox customHeader = new VBox();
        customHeader.getStyleClass().add("header-panel");
        customHeader.getChildren().add(titleBar);
        
        dialog.getDialogPane().setHeaderText(null);
        dialog.getDialogPane().setHeader(customHeader);

        // 7. Apply parent stylesheets
        javafx.stage.Window.getWindows().stream()
                .filter(javafx.stage.Window::isShowing)
                .findFirst()
                .ifPresent(window -> {
                    if (window.getScene() != null) {
                        dialog.getDialogPane().getStylesheets().addAll(window.getScene().getStylesheets());
                    }
                });

        // 8. Fix white edges on transparent stages by setting scene fill to transparent
        if (dialog.getDialogPane().getScene() != null) {
            dialog.getDialogPane().getScene().setFill(javafx.scene.paint.Color.TRANSPARENT);
        } else {
            dialog.getDialogPane().sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) {
                    newScene.setFill(javafx.scene.paint.Color.TRANSPARENT);
                }
            });
        }

        // 9. Dim the parent application window while modal is showing
        dialog.showingProperty().addListener((obs, wasShowing, isShowing) -> {
            javafx.stage.Window.getWindows().stream()
                .filter(w -> w != dialog.getDialogPane().getScene().getWindow() && w.isShowing())
                .findFirst()
                .ifPresent(window -> {
                    if (window.getScene() != null && window.getScene().getRoot() != null) {
                        if (isShowing) {
                            window.getScene().getRoot().setEffect(new javafx.scene.effect.ColorAdjust(0, 0, -0.4, 0));
                        } else {
                            window.getScene().getRoot().setEffect(null);
                        }
                    }
                });
        });
    }
}
