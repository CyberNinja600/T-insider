package com.tinsider.ui.dialogs;

import com.tinsider.model.Snippet;
import com.tinsider.model.SnippetPackage;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SnippetDialog extends Dialog<Snippet> {

    public SnippetDialog(Snippet existing, List<SnippetPackage> packages) {
        setTitle(existing == null ? "Add Snippet" : "Edit Snippet");

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField titleField = new TextField();
        titleField.setPromptText("Update System");
        TextArea codeArea = new TextArea();
        codeArea.setPromptText("sudo apt-get update && sudo apt-get upgrade -y");
        codeArea.setPrefRowCount(5);

        if (existing != null) {
            titleField.setText(existing.getTitle());
            codeArea.setText(existing.getCode());
        }

        grid.add(new Label("Title:"), 0, 0);
        grid.add(titleField, 1, 0);
        grid.add(new Label("Command/Code:"), 0, 1);
        grid.add(codeArea, 1, 1);

        // Package selection
        SnippetPackage nonePackage = new SnippetPackage("(None)");
        nonePackage.setId(null);

        ComboBox<SnippetPackage> packageCombo = new ComboBox<>();
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
            TextInputDialog inputDlg = new TextInputDialog();
            inputDlg.setTitle("New Package");
            inputDlg.setContentText("Package Name:");
            AlertHelper.themeDialog(inputDlg);
            Optional<String> result = inputDlg.showAndWait();
            result.ifPresent(name -> {
                if (!name.trim().isEmpty()) {
                    SnippetPackage newPkg = new SnippetPackage(name.trim());
                    if (packages instanceof ObservableList) {
                        ((ObservableList<SnippetPackage>) packages).add(newPkg);
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
            if (dialogButton == saveButtonType) {
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
}
