package com.tinsider.ui.dialogs;

import com.tinsider.config.TerminalTheme;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import java.util.List;
import java.util.function.Consumer;

public class SettingsDialog extends Dialog<Void> {
    
    public SettingsDialog(
            List<TerminalTheme> availableThemes, 
            TerminalTheme currentTheme, 
            Consumer<TerminalTheme> onThemeChanged,
            boolean initialAutoEnter,
            Consumer<Boolean> onAutoEnterChanged) {
        
        setTitle("Settings");

        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(15);
        grid.setPadding(new Insets(20, 50, 20, 15));

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

        Label autoEnterLabel = new Label("Auto-enter snippet:");
        CheckBox autoEnterCheckBox = new CheckBox();
        autoEnterCheckBox.getStyleClass().add("settings-checkbox");
        autoEnterCheckBox.setSelected(initialAutoEnter);

        autoEnterCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (onAutoEnterChanged != null) {
                onAutoEnterChanged.accept(newVal);
            }
        });

        grid.add(themeLabel, 0, 0);
        grid.add(themeOptions, 1, 0);
        grid.add(autoEnterLabel, 0, 1);
        grid.add(autoEnterCheckBox, 1, 1);

        getDialogPane().setContent(grid);
        
        AlertHelper.themeDialog(this);
    }
}