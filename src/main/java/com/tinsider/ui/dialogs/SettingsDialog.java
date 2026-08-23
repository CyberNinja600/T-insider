package com.tinsider.ui.dialogs;

import com.tinsider.config.TerminalTheme;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import java.util.List;
import java.util.function.Consumer;

public class SettingsDialog extends Dialog<Void> {
    
    public SettingsDialog(
            List<TerminalTheme> availableThemes, 
            TerminalTheme currentTheme, 
            Consumer<TerminalTheme> onThemeChanged,
            boolean initialAutoEnter,
            Consumer<Boolean> onAutoEnterChanged,
            boolean initialGradientsEnabled,
            Consumer<Boolean> onGradientsChanged) {
        
        setTitle("Settings");

        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        // Group 1: Appearance Group
        VBox appearanceGroup = new VBox(10);
        Label appearanceHeader = new Label("Appearance");
        appearanceHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        
        GridPane appearanceGrid = new GridPane();
        appearanceGrid.setHgap(15);
        appearanceGrid.setVgap(15);
        appearanceGrid.setPadding(new Insets(5, 0, 5, 10));

        Label themeLabel = new Label("Theme:");
        ComboBox<String> themeOptions = new ComboBox<>();
        themeOptions.getStyleClass().add("theme-dropdown");
        for (TerminalTheme theme : availableThemes) {
            themeOptions.getItems().add(theme.getName());
        }
        themeOptions.setValue(currentTheme.getName());

        themeOptions.setOnAction(e -> {
            String selectedName = themeOptions.getValue();
            for (TerminalTheme theme : availableThemes) {
                if (theme.getName().equalsIgnoreCase(selectedName)) {
                    onThemeChanged.accept(theme);
                    break;
                }
            }
        });

        Label gradientLabel = new Label("Theme Gradient:");
        CheckBox gradientCheckBox = new CheckBox();
        gradientCheckBox.getStyleClass().add("settings-checkbox");
        gradientCheckBox.setSelected(initialGradientsEnabled);
        gradientCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (onGradientsChanged != null) {
                onGradientsChanged.accept(newVal);
            }
        });

        appearanceGrid.add(themeLabel, 0, 0);
        appearanceGrid.add(themeOptions, 1, 0);
        appearanceGrid.add(gradientLabel, 0, 1);
        appearanceGrid.add(gradientCheckBox, 1, 1);
        appearanceGroup.getChildren().addAll(appearanceHeader, appearanceGrid);

        // Group 2: Utilities Group
        VBox utilityGroup = new VBox(10);
        Label utilityHeader = new Label("Utility");
        utilityHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        GridPane utilityGrid = new GridPane();
        utilityGrid.setHgap(15);
        utilityGrid.setVgap(15);
        utilityGrid.setPadding(new Insets(5, 0, 5, 10));

        Label autoEnterLabel = new Label("Auto-enter snippet:");
        CheckBox autoEnterCheckBox = new CheckBox();
        autoEnterCheckBox.getStyleClass().add("settings-checkbox");
        autoEnterCheckBox.setSelected(initialAutoEnter);

        autoEnterCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (onAutoEnterChanged != null) {
                onAutoEnterChanged.accept(newVal);
            }
        });

        utilityGrid.add(autoEnterLabel, 0, 0);
        utilityGrid.add(autoEnterCheckBox, 1, 0);
        utilityGroup.getChildren().addAll(utilityHeader, utilityGrid);

        // Vertical Layout Container
        VBox mainLayout = new VBox(20);
        mainLayout.setPadding(new Insets(20, 40, 20, 20));
        mainLayout.getChildren().addAll(appearanceGroup, utilityGroup);

        getDialogPane().setContent(mainLayout);
        
        AlertHelper.themeDialog(this);
    }
}