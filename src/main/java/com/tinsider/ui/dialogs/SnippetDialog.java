package com.tinsider.ui.dialogs;

import com.tinsider.model.Snippet;
import com.tinsider.model.SnippetPackage;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SnippetDialog extends Dialog<Snippet> {

    private final TextField titleField;
    private final TextArea codeArea;
    private final ComboBox<SnippetPackage> packageCombo;
    private final Button saveBtn;

    public SnippetDialog(Snippet existing, List<SnippetPackage> packages) {
        setTitle(existing == null ? "Add Snippet" : "Edit Snippet");

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        saveBtn = (Button) getDialogPane().lookupButton(saveButtonType);
        if (saveBtn != null) {
            Label saveIcon = new Label("\uEB4B");
            saveIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 12px;");
            saveIcon.getStyleClass().add("button-icon");
            saveBtn.setGraphic(saveIcon);

            saveBtn.addEventFilter(ActionEvent.ACTION, event -> {
                if (!validateAndHighlight()) {
                    event.consume();
                }
            });
        }

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        titleField = new TextField();
        titleField.setPromptText("Update System");
        codeArea = new TextArea();
        codeArea.setPromptText("sudo apt-get update && sudo apt-get upgrade -y");
        codeArea.setPrefRowCount(5);

        if (existing != null) {
            titleField.setText(existing.getTitle());
            codeArea.setText(existing.getCode());
        }

        // Clear error styling when the user types in either field
        titleField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.trim().isEmpty()) {
                titleField.setStyle("");
                titleField.setPromptText("Update System");
            }
        });
        codeArea.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.trim().isEmpty()) {
                codeArea.setStyle("");
                codeArea.setPromptText("sudo apt-get update && sudo apt-get upgrade -y");
            }
        });

        grid.add(new Label("Title:"), 0, 0);
        grid.add(titleField, 1, 0);
        grid.add(new Label("Command/Code:"), 0, 1);
        grid.add(codeArea, 1, 1);

        // Package selection
        SnippetPackage nonePackage = new SnippetPackage("(None)");
        nonePackage.setId(null);

        packageCombo = new ComboBox<>();
        packageCombo.getItems().add(nonePackage);
        if (packages != null) {
            packageCombo.getItems().addAll(packages);
        }
        packageCombo.setValue(nonePackage);

        if (existing != null && existing.getPackageId() != null && packages != null) {
            for (SnippetPackage pkg : packages) {
                if (existing.getPackageId().equals(pkg.getId())) {
                    packageCombo.setValue(pkg);
                    break;
                }
            }
        }

        Button newPackageBtn = new Button("+ New");
        newPackageBtn.setOnAction(e -> {
            PackageDialog inputDlg = new PackageDialog("New Package");
            Optional<String> result = AlertHelper.showModal(inputDlg);
            result.ifPresent(name -> {
                if (!name.trim().isEmpty()) {
                    SnippetPackage newPkg = new SnippetPackage(name.trim());
                    if (packages instanceof ObservableList) {
                        packages.add(newPkg);
                    } else if (packages != null) {
                        packages.add(newPkg);
                    }
                    com.tinsider.storage.StorageManager.savePackages(new java.util.ArrayList<>(packages));
                    packageCombo.getItems().add(newPkg);
                    packageCombo.setValue(newPkg);
                }
            });
        });

        HBox packageBox = new HBox(5);
        packageBox.getChildren().addAll(packageCombo, newPackageBtn);

        grid.add(new Label("Package:"), 0, 2);
        grid.add(packageBox, 1, 2);

        getDialogPane().setContent(grid);
        AlertHelper.themeDialog(this);

        Platform.runLater(titleField::requestFocus);

        setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType && isInputValid()) {
                String id = existing == null ? UUID.randomUUID().toString() : existing.getId();
                Snippet snippet = new Snippet(
                        titleField.getText().trim(),
                        codeArea.getText()
                );
                snippet.setId(id);
                
                SnippetPackage selectedPkg = packageCombo.getValue();
                if (selectedPkg != null && selectedPkg.getId() != null) {
                    snippet.setPackageId(selectedPkg.getId());
                } else {
                    snippet.setPackageId(null);
                }
                
                return snippet;
            }
            return null;
        });
    }

    public boolean isInputValid() {
        String title = titleField.getText() != null ? titleField.getText().trim() : "";
        String code = codeArea.getText() != null ? codeArea.getText().trim() : "";
        return !title.isEmpty() && !code.isEmpty();
    }

    public boolean validateAndHighlight() {
        boolean valid = true;
        Control firstInvalid = null;

        String title = titleField.getText() != null ? titleField.getText().trim() : "";
        if (title.isEmpty()) {
            titleField.setText("");
            titleField.setPromptText("Required");
            titleField.setStyle("-fx-border-color: #ff5555; -fx-prompt-text-fill: #ff5555;");
            valid = false;
            if (firstInvalid == null) firstInvalid = titleField;
        } else {
            titleField.setStyle("");
            titleField.setPromptText("Update System");
        }

        String code = codeArea.getText() != null ? codeArea.getText().trim() : "";
        if (code.isEmpty()) {
            codeArea.setText("");
            codeArea.setPromptText("Required");
            codeArea.setStyle("-fx-border-color: #ff5555; -fx-prompt-text-fill: #ff5555;");
            valid = false;
            if (firstInvalid == null) firstInvalid = codeArea;
        } else {
            codeArea.setStyle("");
            codeArea.setPromptText("sudo apt-get update && sudo apt-get upgrade -y");
        }

        if (firstInvalid != null) {
            firstInvalid.requestFocus();
        }

        return valid;
    }

    public TextField getTitleField() {
        return titleField;
    }

    public TextArea getCodeArea() {
        return codeArea;
    }

    public ComboBox<SnippetPackage> getPackageCombo() {
        return packageCombo;
    }

    public Button getSaveButton() {
        return saveBtn;
    }
}
