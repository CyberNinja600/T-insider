package com.tinsider.ui.dialogs;

import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;

public class PackageDialog extends TextInputDialog {

    private final TextField editorField;
    private final Button okButton;

    public PackageDialog(String title) {
        super();
        setTitle(title != null ? title : "Add Package");
        setHeaderText(null);
        setContentText("Package Name:");

        editorField = getEditor();
        editorField.setPromptText("Package name");

        okButton = (Button) getDialogPane().lookupButton(ButtonType.OK);
        if (okButton != null) {
            Label saveIcon = new Label("\uEB4B");
            saveIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 14px;");
            saveIcon.getStyleClass().add("button-icon");
            okButton.setGraphic(saveIcon);

            okButton.addEventFilter(ActionEvent.ACTION, event -> {
                if (!validateAndHighlight()) {
                    event.consume();
                }
            });
        }

        editorField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.trim().isEmpty()) {
                editorField.setStyle("");
                editorField.setPromptText("Package name");
            }
        });

        AlertHelper.themeDialog(this);
    }

    public boolean validateAndHighlight() {
        String name = editorField.getText() != null ? editorField.getText().trim() : "";
        if (name.isEmpty()) {
            editorField.setText("");
            editorField.setPromptText("Required");
            editorField.setStyle("-fx-border-color: #ff5555; -fx-prompt-text-fill: #ff5555;");
            editorField.requestFocus();
            return false;
        } else {
            editorField.setStyle("");
            editorField.setPromptText("Package name");
            return true;
        }
    }

    public TextField getEditorField() {
        return editorField;
    }

    public Button getOkButton() {
        return okButton;
    }
}
