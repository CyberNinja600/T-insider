package com.tinsider.ui.components.terminal;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;

public class TerminalWorkspace extends StackPane {
    private final TabPane tabPane;
    private final Tab addTab;

    public TerminalWorkspace(Runnable onPlusBtnPressed) {
        tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);

        addTab = new Tab("+");
        addTab.setClosable(false);
        addTab.setContent(new Pane());
        addTab.getStyleClass().add("add-tab");
        tabPane.getTabs().add(addTab);

        getChildren().add(tabPane);

        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab == addTab) {
                Platform.runLater(() -> {
                    if (oldTab != null && tabPane.getTabs().contains(oldTab)) {
                        tabPane.getSelectionModel().select(oldTab);
                    }
                    onPlusBtnPressed.run();
                });
            }
        });

        Runnable updateTabWidths = () -> {
            Platform.runLater(() -> {
                int numTabs = tabPane.getTabs().size();
                if (numTabs > 0) {
                    double availableWidth = tabPane.getWidth() - 20;
                    if (availableWidth <= 0) {
                        availableWidth = 740;
                    }
                    double standardTabWidth = 120;
                    if (numTabs > 1) {
                        standardTabWidth = (availableWidth - 20) / (numTabs - 1);
                    }
                    double tabWidth = Math.min(120, Math.max(20, standardTabWidth));

                    // Reset global TabPane min/max tab widths so individual tab styling takes complete precedence
                    tabPane.setTabMinWidth(0);
                    tabPane.setTabMaxWidth(Double.MAX_VALUE);

                    for (Tab tab : tabPane.getTabs()) {
                        if (tab != addTab) {
                            tab.setStyle("-fx-pref-width: " + tabWidth + "px; -fx-min-width: " + tabWidth + "px; -fx-max-width: " + tabWidth + "px;");
                        } else {
                            tab.setStyle("-fx-pref-width: 30px; -fx-min-width: 30px; -fx-max-width: 30px;");
                        }
                    }
                }
            });
        };

        tabPane.widthProperty().addListener((obs, oldVal, newVal) -> updateTabWidths.run());
        tabPane.getTabs().addListener((ListChangeListener<Tab>) change -> updateTabWidths.run());
        Platform.runLater(updateTabWidths);
    }

    public TabPane getTabPane() {
        return tabPane;
    }
    
    public Tab getAddTab() {
        return addTab;
    }
}
