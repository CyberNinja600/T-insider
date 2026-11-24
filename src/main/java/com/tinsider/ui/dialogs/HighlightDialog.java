package com.tinsider.ui.dialogs;

import com.tinsider.model.WordHighlight;
import com.tinsider.model.HighlightPackage;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class HighlightDialog extends Dialog<WordHighlight> {

    private final TextField wordField;
    private final TextField nameField;
    private final ColorPicker colorPicker;
    private final ComboBox<HighlightPackage> packageCombo;
    private final CheckBox regexCheckBox;
    private final Button saveBtn;

    public HighlightDialog(WordHighlight existing, List<HighlightPackage> packages) {
        setTitle(existing == null ? "Add Highlight" : "Edit Highlight");

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        wordField = new TextField();
        wordField.setPromptText("Enter word to highlight...");
        
        nameField = new TextField();
        nameField.setPromptText("Friendly name (optional)...");
        
        colorPicker = new ColorPicker(javafx.scene.paint.Color.YELLOW);

        if (existing != null) {
            wordField.setText(existing.getWord());
            nameField.setText(existing.getName());
            try {
                colorPicker.setValue(javafx.scene.paint.Color.web(existing.getColorHex()));
            } catch (Exception ex) {
                colorPicker.setValue(javafx.scene.paint.Color.YELLOW);
            }
        }

        // Clear error styling when the user types in wordField
        wordField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.trim().isEmpty()) {
                wordField.setStyle("");
                wordField.setPromptText("Enter word to highlight...");
            }
        });

        grid.add(new Label("Word/Regex:"), 0, 0);
        grid.add(wordField, 1, 0);
        
        grid.add(new Label("Friendly Name:"), 0, 1);
        grid.add(nameField, 1, 1);
        
        grid.add(new Label("Color:"), 0, 2);
        grid.add(colorPicker, 1, 2);

        // Package selection
        HighlightPackage nonePackage = new HighlightPackage("(None)");
        nonePackage.setId(null);

        packageCombo = new ComboBox<>();
        packageCombo.getItems().add(nonePackage);
        if (packages != null) {
            packageCombo.getItems().addAll(packages);
        }
        packageCombo.setValue(nonePackage);

        if (existing != null && existing.getPackageId() != null && packages != null) {
            for (HighlightPackage pkg : packages) {
                if (existing.getPackageId().equals(pkg.getId())) {
                    packageCombo.setValue(pkg);
                    break;
                }
            }
        }

        Button newPackageBtn = new Button("+ New");
        newPackageBtn.setOnAction(e -> {
            PackageDialog inputDlg = new PackageDialog("New Highlight Package");
            Optional<String> result = AlertHelper.showModal(inputDlg);
            result.ifPresent(name -> {
                if (name.length() > 12) {
                    AlertHelper.showErrorAlert("Package name too long", "Package name cannot be more than 12 letters.");
                    return;
                }
                String trimmedName = name.trim();
                if (!trimmedName.isEmpty()) {
                    boolean duplicate = false;
                    if (packages != null) {
                        for (HighlightPackage pkg : packages) {
                            if (pkg.getName() != null && pkg.getName().trim().equalsIgnoreCase(trimmedName)) {
                                duplicate = true;
                                break;
                            }
                        }
                    }
                    if (duplicate) {
                        AlertHelper.showErrorAlert("Duplicate Package", "A highlight package with this name already exists.");
                        return;
                    }

                    HighlightPackage newPkg = new HighlightPackage(trimmedName);
                    if (packages != null) {
                        packages.add(newPkg);
                        com.tinsider.storage.StorageManager.saveHighlightPackages(new java.util.ArrayList<>(packages));
                    }
                    packageCombo.getItems().add(newPkg);
                    packageCombo.setValue(newPkg);
                }
            });
        });

        HBox packageBox = new HBox(5);
        packageBox.getChildren().addAll(packageCombo, newPackageBtn);

        grid.add(new Label("Package:"), 0, 3);
        grid.add(packageBox, 1, 3);

        regexCheckBox = new CheckBox("Regex Highlight");
        if (existing != null) {
            regexCheckBox.setSelected(existing.isRegex());
        }
        grid.add(regexCheckBox, 1, 4);

        saveBtn = (Button) getDialogPane().lookupButton(saveButtonType);
        if (saveBtn != null) {
            Label saveIcon = new Label("\uEB4B");
            saveIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 12px;");
            saveIcon.getStyleClass().add("button-icon");
            saveBtn.setGraphic(saveIcon);
            if (existing != null && com.tinsider.storage.StorageManager.isDefaultHighlight(existing.getId())) {
                saveBtn.setDisable(true);
            }

            saveBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
                if (existing != null && com.tinsider.storage.StorageManager.isDefaultHighlight(existing.getId())) {
                    AlertHelper.showErrorAlert("Built-in Highlight", "Cannot edit built-in highlights.");
                    event.consume();
                    return;
                }
                if (!validateAndHighlight()) {
                    event.consume();
                }
            });
        }

        getDialogPane().setContent(grid);
        AlertHelper.themeDialog(this);

        Platform.runLater(wordField::requestFocus);

        setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType && isInputValid()) {
                if (existing != null && com.tinsider.storage.StorageManager.isDefaultHighlight(existing.getId())) {
                    return null;
                }
                String id = existing == null ? UUID.randomUUID().toString() : existing.getId();
                javafx.scene.paint.Color color = colorPicker.getValue();
                String hexColor = String.format("#%02X%02X%02X",
                    (int)(color.getRed() * 255),
                    (int)(color.getGreen() * 255),
                    (int)(color.getBlue() * 255)
                );
                String wordStr = wordField.getText() == null ? "" : wordField.getText().trim();
                String nameStr = nameField.getText() == null ? "" : nameField.getText().trim();
                WordHighlight wh = new WordHighlight(wordStr, hexColor);
                wh.setId(id);
                wh.setName(nameStr);
                wh.setRegex(regexCheckBox.isSelected());
                if (existing != null) {
                    wh.setCaseInsensitive(existing.isCaseInsensitive());
                }
                
                HighlightPackage selectedPkg = packageCombo.getValue();
                if (selectedPkg != null && selectedPkg.getId() != null) {
                    wh.setPackageId(selectedPkg.getId());
                } else {
                    wh.setPackageId(null);
                }
                
                return wh;
            }
            return null;
        });
    }

    public boolean isInputValid() {
        String word = wordField.getText() != null ? wordField.getText().trim() : "";
        return !word.isEmpty();
    }

    public boolean validateAndHighlight() {
        String word = wordField.getText() != null ? wordField.getText().trim() : "";
        if (word.isEmpty()) {
            wordField.setText("");
            wordField.setPromptText("Required");
            wordField.setStyle("-fx-border-color: #ff5555; -fx-prompt-text-fill: #ff5555;");
            wordField.requestFocus();
            return false;
        } else {
            wordField.setStyle("");
            wordField.setPromptText("Enter word to highlight...");
            return true;
        }
    }

    public TextField getWordField() {
        return wordField;
    }

    public TextField getNameField() {
        return nameField;
    }

    public Button getSaveButton() {
        return saveBtn;
    }
}