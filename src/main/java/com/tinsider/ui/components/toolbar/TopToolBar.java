package com.tinsider.ui.components.toolbar;

import com.tinsider.config.TerminalTheme;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import java.util.List;
import java.util.function.Consumer;

public class TopToolBar extends HBox {
    private final ComboBox<String> shellOptions;
    private final ComboBox<String> themeOptions;

    public TopToolBar(
            List<TerminalTheme> availableThemes, 
            String initialThemeName, 
            String initialShell,
            Consumer<TerminalTheme> onThemeChanged,
            Consumer<String> onShellChanged) {
        
        super(10);
        setPadding(new Insets(10));
        setAlignment(Pos.CENTER_LEFT);
        getStyleClass().add("top-bar");

        Label selectLabel = new Label("Local Shell Type:");
        shellOptions = new ComboBox<>();
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        if (isWindows) {
            shellOptions.getItems().addAll("CMD", "PowerShell", "Bash");
            shellOptions.setValue(initialShell != null ? initialShell : "CMD");
        } else {
            shellOptions.getItems().addAll("Bash");
            shellOptions.setValue("Bash");
        }

        shellOptions.setOnAction(e -> {
            if (onShellChanged != null) {
                onShellChanged.accept(shellOptions.getValue());
            }
        });

        Label themeLabel = new Label("Theme:");
        themeOptions = new ComboBox<>();
        for (TerminalTheme theme : availableThemes) {
            themeOptions.getItems().add(theme.getName());
        }
        themeOptions.setValue(initialThemeName != null ? initialThemeName : "Default Space Blue");

        themeOptions.setOnAction(e -> {
            String selectedName = themeOptions.getValue();
            for (TerminalTheme theme : availableThemes) {
                if (theme.getName().equalsIgnoreCase(selectedName)) {
                    onThemeChanged.accept(theme);
                    break;
                }
            }
        });

        getChildren().addAll(selectLabel, shellOptions, themeLabel, themeOptions);
    }

    public String getSelectedShell() {
        return shellOptions.getValue();
    }

    public String getSelectedTheme() {
        return themeOptions.getValue();
    }
}
