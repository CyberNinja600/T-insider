package com.tinsider.ui.dashboard;

import com.tinsider.util.ActivityLogger;
import javafx.scene.layout.StackPane;

/**
 * StackPane container for all dashboard views (Connections, Hosts, Snippets, Terminals, Highlights).
 * Controls view visibility and tab switching.
 */
public class DashboardContainer extends StackPane {

    private final ConnectionsDashboardView connectionsView;
    private final HostsDashboardView hostsView;
    private final SnippetsDashboardView snippetsView;
    private final SessionsDashboardView sessionsView;
    private final HighlightsDashboardView highlightsView;

    private DashboardTab currentTab = DashboardTab.CONNECTIONS;

    public DashboardContainer(
        ConnectionsDashboardView connectionsView,
        HostsDashboardView hostsView,
        SnippetsDashboardView snippetsView,
        SessionsDashboardView sessionsView,
        HighlightsDashboardView highlightsView
    ) {
        this.connectionsView = connectionsView;
        this.hostsView = hostsView;
        this.snippetsView = snippetsView;
        this.sessionsView = sessionsView;
        this.highlightsView = highlightsView;

        getStyleClass().add("dashboard-center-stack");

        getChildren().addAll(connectionsView, hostsView, snippetsView, sessionsView, highlightsView);

        switchDashboardTab(DashboardTab.CONNECTIONS);
    }

    public void switchDashboardTab(DashboardTab tab) {
        ActivityLogger.logUserAction("SWITCH_DASHBOARD_TAB", "User switched dashboard tab to: " + tab);
        this.currentTab = tab;

        connectionsView.setVisible(false);
        hostsView.setVisible(false);
        snippetsView.setVisible(false);
        sessionsView.setVisible(false);
        highlightsView.setVisible(false);

        switch (tab) {
            case CONNECTIONS -> {
                connectionsView.setVisible(true);
                connectionsView.toFront();
                connectionsView.rebuild();
            }
            case HOSTS -> {
                hostsView.setVisible(true);
                hostsView.toFront();
            }
            case SNIPPETS -> {
                snippetsView.setVisible(true);
                snippetsView.toFront();
            }
            case TERMINALS -> {
                sessionsView.setVisible(true);
                sessionsView.toFront();
                sessionsView.rebuild();
            }
            case HIGHLIGHTS -> {
                highlightsView.setVisible(true);
                highlightsView.toFront();
                highlightsView.rebuild();
            }
        }
    }

    public DashboardTab getCurrentTab() {
        return currentTab;
    }

    public ConnectionsDashboardView getConnectionsView() {
        return connectionsView;
    }

    public HostsDashboardView getHostsView() {
        return hostsView;
    }

    public SnippetsDashboardView getSnippetsView() {
        return snippetsView;
    }

    public SessionsDashboardView getSessionsView() {
        return sessionsView;
    }

    public HighlightsDashboardView getHighlightsView() {
        return highlightsView;
    }
}
