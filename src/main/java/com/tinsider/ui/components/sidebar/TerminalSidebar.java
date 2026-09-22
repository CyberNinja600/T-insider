package com.tinsider.ui.components.sidebar;

import com.tinsider.util.ActivityLogger;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Collapsible left execution sidebar (240px) shown during Terminal Mode.
 * Houses the segmented tab navigation for Snippet execution and Word Highlights toggling.
 */
public class TerminalSidebar extends VBox {

    private final SidebarSnippetsView snippetsView;
    private final SidebarHighlightsView highlightsView;

    private final ToggleButton sidebarSnippetsBtn;
    private final ToggleButton sidebarHighlightsBtn;

    public TerminalSidebar(SidebarSnippetsView snippetsView, SidebarHighlightsView highlightsView) {
        super(15);
        this.snippetsView = snippetsView;
        this.highlightsView = highlightsView;

        setPadding(new Insets(20, 10, 20, 10));
        setPrefWidth(240);
        getStyleClass().add("sidebar");

        // Segmented Sidebar Navigation Tabs
        HBox sidebarNav = new HBox(5);
        sidebarNav.setAlignment(Pos.CENTER);

        sidebarSnippetsBtn = new ToggleButton("Snippets");
        sidebarHighlightsBtn = new ToggleButton("Highlights");
        ToggleGroup sidebarGroup = new ToggleGroup();
        sidebarSnippetsBtn.setToggleGroup(sidebarGroup);
        sidebarHighlightsBtn.setToggleGroup(sidebarGroup);
        sidebarSnippetsBtn.setSelected(true);

        sidebarSnippetsBtn.setMaxWidth(Double.MAX_VALUE);
        sidebarHighlightsBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(sidebarSnippetsBtn, Priority.ALWAYS);
        HBox.setHgrow(sidebarHighlightsBtn, Priority.ALWAYS);

        sidebarSnippetsBtn.getStyleClass().add("sidebar-tab-button");
        sidebarHighlightsBtn.getStyleClass().add("sidebar-tab-button");
        sidebarNav.getChildren().addAll(sidebarSnippetsBtn, sidebarHighlightsBtn);

        // Initial view states
        snippetsView.setVisible(true);
        snippetsView.setManaged(true);
        highlightsView.setVisible(false);
        highlightsView.setManaged(false);

        // Tab selection handlers
        sidebarSnippetsBtn.setOnAction(e -> {
            sidebarSnippetsBtn.setSelected(true);
            snippetsView.setVisible(true);
            snippetsView.setManaged(true);
            highlightsView.setVisible(false);
            highlightsView.setManaged(false);
            ActivityLogger.logUserAction("SIDEBAR_SWITCH_TAB", "User switched sidebar tab to Snippets");
        });

        sidebarHighlightsBtn.setOnAction(e -> {
            sidebarHighlightsBtn.setSelected(true);
            snippetsView.setVisible(false);
            snippetsView.setManaged(false);
            highlightsView.setVisible(true);
            highlightsView.setManaged(true);
            ActivityLogger.logUserAction("SIDEBAR_SWITCH_TAB", "User switched sidebar tab to Highlights");
            highlightsView.rebuild();
        });

        getChildren().addAll(sidebarNav, snippetsView, highlightsView);
    }

    public SidebarSnippetsView getSnippetsView() {
        return snippetsView;
    }

    public SidebarHighlightsView getHighlightsView() {
        return highlightsView;
    }

    public void rebuildSnippets() {
        snippetsView.rebuild();
    }

    public void rebuildHighlights() {
        highlightsView.rebuild();
    }
}
