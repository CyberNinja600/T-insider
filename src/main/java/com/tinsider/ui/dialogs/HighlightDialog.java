package com.tinsider.ui.dialogs;

import com.tinsider.model.WordHighlight;
import com.tinsider.model.HighlightPackage;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class HighlightDialog extends Dialog<WordHighlight> {

    public HighlightDialog(WordHighlight existing, List<HighlightPackage> packages) {
        setTitle(existing == null ? "Add Highlight" : "Edit Highlight");

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        Button saveBtn = (Button) getDialogPane().lookupButton(saveButtonType);
        if (saveBtn != null) {
            Label saveIcon = new Label("\uEB4B");
            saveIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 12px;");
            saveIcon.getStyleClass().add("button-icon");
            saveBtn.setGraphic(saveIcon);
        }

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField wordField = new TextField();
        wordField.setPromptText("Enter word to highlight...");
        ColorPicker colorPicker = new ColorPicker(javafx.scene.paint.Color.YELLOW);

        if (existing != null) {
            wordField.setText(existing.getWord());
            try {
                colorPicker.setValue(javafx.scene.paint.Color.web(existing.getColorHex()));
            } catch (Exception ex) {
                colorPicker.setValue(javafx.scene.paint.Color.YELLOW);
            }
        }

        grid.add(new Label("Word:"), 0, 0);
        grid.add(wordField, 1, 0);
        grid.add(new Label("Color:"), 0, 1);
        grid.add(colorPicker, 1, 1);

        // Package selection
        HighlightPackage nonePackage = new HighlightPackage("(None)");
        nonePackage.setId(null);

        ComboBox<HighlightPackage> packageCombo = new ComboBox<>();
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
            TextInputDialog inputDlg = new TextInputDialog();
            inputDlg.setTitle("New Highlight Package");
            inputDlg.setContentText("Package Name:");
            AlertHelper.themeDialog(inputDlg);
            Optional<String> result = inputDlg.showAndWait();
            result.ifPresent(name -> {
                if (!name.trim().isEmpty()) {
                    HighlightPackage newPkg = new HighlightPackage(name.trim());
                    if (packages instanceof ObservableList) {
                        ((ObservableList<HighlightPackage>) packages).add(newPkg);
                    } else if (packages != null) {
                        packages.add(newPkg);
                    }
                    com.tinsider.storage.StorageManager.saveHighlightPackages(new java.util.ArrayList<>(packages));
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

        Platform.runLater(wordField::requestFocus);

        setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                String id = existing == null ? UUID.randomUUID().toString() : existing.getId();
                javafx.scene.paint.Color color = colorPicker.getValue();
                String hexColor = String.format("#%02X%02X%02X",
                    (int)(color.getRed() * 255),
                    (int)(color.getGreen() * 255),
                    (int)(color.getBlue() * 255)
                );
                WordHighlight wh = new WordHighlight(wordField.getText().trim(), hexColor);
                wh.setId(id);
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
}