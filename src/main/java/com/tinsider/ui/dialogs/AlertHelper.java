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

import java.util.Optional;

public class AlertHelper {

    public static <R> Optional<R> showModal(Dialog<R> dialog) {
        return ModalOverlayManager.showModal(dialog);
    }

    public static void showErrorAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(title);
        alert.setContentText(content);
        showModal(alert);
    }

    public static void themeDialog(Dialog<?> dialog) {
        if (dialog == null || dialog.getDialogPane() == null) {
            return;
        }

        // Prevent duplicate header recreation if themeDialog is called multiple times
        if (dialog.getDialogPane().getHeader() != null &&
            dialog.getDialogPane().getHeader().getStyleClass().contains("header-panel")) {
            return;
        }

        // 1. Strip OS Windows Borders (for fallback Stage display)
        try {
            dialog.initStyle(StageStyle.TRANSPARENT);
        } catch (Exception ignored) {
            // Ignored if style was already initialized
        }

        // 2. Read Existing Properties
        String titleText = dialog.getTitle() != null ? dialog.getTitle() : "";

        // 3. Build Custom Title Bar
        HBox titleBar = new HBox();
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setPadding(new Insets(0, 0, 0, 15));
        titleBar.setStyle("-fx-background-color: transparent; -fx-cursor: move;");

        Label titleLabel = new Label(titleText);
        titleLabel.getStyleClass().add("dialog-header-title");

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

        // 4. Replace Standard Header with just the Title Bar
        VBox customHeader = new VBox();
        customHeader.getStyleClass().add("header-panel");
        customHeader.getChildren().add(titleBar);

        dialog.getDialogPane().setHeaderText(null);
        dialog.getDialogPane().setHeader(customHeader);

        // 5. Inherit application stylesheets (useful for fallback or styling consistency)
        javafx.stage.Window.getWindows().stream()
                .filter(javafx.stage.Window::isShowing)
                .findFirst()
                .ifPresent(window -> {
                    if (window.getScene() != null) {
                        for (String sheet : window.getScene().getStylesheets()) {
                            if (!dialog.getDialogPane().getStylesheets().contains(sheet)) {
                                dialog.getDialogPane().getStylesheets().add(sheet);
                            }
                        }
                    }
                });

        // 6. Fix white edges on transparent stages by setting scene fill to transparent if staged
        if (dialog.getDialogPane().getScene() != null) {
            dialog.getDialogPane().getScene().setFill(javafx.scene.paint.Color.TRANSPARENT);
        } else {
            dialog.getDialogPane().sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) {
                    newScene.setFill(javafx.scene.paint.Color.TRANSPARENT);
                }
            });
        }
    }
}
