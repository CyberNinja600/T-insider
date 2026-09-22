package com.tinsider.ui.components.navigation;

import com.tinsider.ui.dashboard.DashboardTab;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Slim navigation rail (120px) displayed on the left in Dashboard Mode.
 * Offers segmented navigation across Connections, Hosts, Snippets, Terminals, and Highlights.
 */
public class SlimNavRail extends VBox {

    private final ToggleGroup navGroup = new ToggleGroup();
    private final Map<DashboardTab, ToggleButton> buttonMap = new EnumMap<>(DashboardTab.class);
    private Consumer<DashboardTab> tabSelectionHandler;

    public SlimNavRail(Consumer<DashboardTab> onTabSelected) {
        super(20);
        this.tabSelectionHandler = onTabSelected;

        setPadding(new Insets(30, 10, 30, 10));
        setPrefWidth(120);
        setAlignment(Pos.TOP_CENTER);
        getStyleClass().add("slim-nav-rail");

        Label appTitleLabel = new Label("T-Insider");
        appTitleLabel.getStyleClass().add("app-title");
        appTitleLabel.setStyle("-fx-font-size: 16px; -fx-padding: 0 0 20px 0;");

        ToggleButton navConnectionsBtn = createNavButton("Connections", DashboardTab.CONNECTIONS);
        ToggleButton navHostsBtn = createNavButton("Hosts", DashboardTab.HOSTS);
        ToggleButton navSnippetsBtn = createNavButton("Snippets", DashboardTab.SNIPPETS);
        ToggleButton navTerminalsBtn = createNavButton("Terminals", DashboardTab.TERMINALS);
        ToggleButton navHighlightsBtn = createNavButton("Highlighter", DashboardTab.HIGHLIGHTS);

        navConnectionsBtn.setSelected(true);

        getChildren().addAll(appTitleLabel, navConnectionsBtn, navHostsBtn, navSnippetsBtn, navTerminalsBtn, navHighlightsBtn);
    }

    private ToggleButton createNavButton(String text, DashboardTab tab) {
        ToggleButton btn = new ToggleButton(text);
        btn.setToggleGroup(navGroup);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.getStyleClass().add("nav-button");

        btn.setOnAction(e -> {
            // Prevent unselecting the currently selected toggle button by clicking it again
            btn.setSelected(true);
            if (tabSelectionHandler != null) {
                tabSelectionHandler.accept(tab);
            }
        });

        buttonMap.put(tab, btn);
        return btn;
    }

    public void selectTab(DashboardTab tab) {
        ToggleButton btn = buttonMap.get(tab);
        if (btn != null) {
            btn.setSelected(true);
        }
    }

    public void setOnTabSelected(Consumer<DashboardTab> handler) {
        this.tabSelectionHandler = handler;
    }
}
