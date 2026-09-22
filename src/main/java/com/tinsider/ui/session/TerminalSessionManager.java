package com.tinsider.ui.session;

import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.ui.JediTermWidget;
import com.tinsider.config.TerminalTheme;
import com.tinsider.model.HostProfile;
import com.tinsider.model.Snippet;
import com.tinsider.terminal.ConnectionManager;
import com.tinsider.terminal.TerminalTabFactory;
import com.tinsider.ui.components.terminal.split.TerminalPane;
import com.tinsider.ui.components.terminal.split.TerminalSplitPane;
import com.tinsider.ui.components.toolbar.TopToolBar;
import com.tinsider.ui.dialogs.AlertHelper;
import com.tinsider.util.ActivityLogger;
import javafx.application.Platform;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

import javax.swing.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Manages active terminal tabs, SSH connections, local terminal sessions,
 * snippet execution, and terminal split panes.
 */
public class TerminalSessionManager {

    private final Map<Tab, TerminalSplitPane> tabSplitPaneMap = new HashMap<>();

    public Map<Tab, TerminalSplitPane> getTabSplitPaneMap() {
        return tabSplitPaneMap;
    }

    public Collection<TerminalSplitPane> getAllSplitPanes() {
        return tabSplitPaneMap.values();
    }

    public TerminalSplitPane getSplitPaneForTab(Tab tab) {
        return tabSplitPaneMap.get(tab);
    }

    public boolean hasActiveSessions() {
        return !tabSplitPaneMap.isEmpty();
    }

    /**
     * Opens a local terminal (CMD, PowerShell, or Bash).
     */
    public void openLocalTerminal(String type, TabPane tabPane, Supplier<TerminalTheme> themeSupplier, Consumer<Tab> onCloseCallback) {
        try {
            ActivityLogger.logUserAction("OPEN_LOCAL_TERMINAL", "User requested open local terminal: " + type);
            TtyConnector connector = ConnectionManager.openLocalTerminal(type);
            String tabTitle = ConnectionManager.truncate(type, 16);
            TerminalTabFactory.createTerminalTab(
                tabTitle,
                connector,
                tabPane,
                tabSplitPaneMap,
                themeSupplier::get,
                tab -> onCloseCallback.accept(tab),
                "LOCAL",
                type,
                null
            );
        } catch (Exception e) {
            e.printStackTrace();
            AlertHelper.showErrorAlert("Error opening local terminal", e.getMessage());
        }
    }

    /**
     * Initiates an SSH connection to a remote host.
     */
    public void connectToHost(HostProfile host, TabPane tabPane, Supplier<TerminalTheme> themeSupplier, Consumer<Tab> onCloseCallback) {
        ActivityLogger.logUserAction("CONNECT_TO_HOST_ATTEMPT", "User requested connection to host: " + host.getName() + " (" + host.getIp() + ")");
        ConnectionManager.connectToHost(host, tabPane, (title, connector) -> {
            TerminalTabFactory.createTerminalTab(
                title,
                connector,
                tabPane,
                tabSplitPaneMap,
                themeSupplier::get,
                tab -> onCloseCallback.accept(tab),
                "SSH",
                null,
                host
            );
        });
    }

    /**
     * Closes the given terminal tab and cleans up associated resources.
     */
    public void closeTab(Tab tab, TabPane tabPane, Runnable onAllClosedCallback) {
        if (tab == null) return;
        ActivityLogger.logUserAction("CLOSE_TAB", "User manually closed terminal tab [" + tab.getText() + "]");
        TerminalSplitPane splitPane = tabSplitPaneMap.remove(tab);
        if (splitPane != null) {
            splitPane.close();
        }
        Platform.runLater(() -> {
            if (tabPane.getTabs().contains(tab)) {
                tabPane.getTabs().remove(tab);
            }
            if (tabPane.getTabs().size() <= 1 && onAllClosedCallback != null) {
                onAllClosedCallback.run();
            }
        });
    }

    /**
     * Sends a snippet to the currently active terminal tab, optionally sending an enter carriage return.
     */
    public void runSnippet(Snippet snippet, boolean autoEnter, TabPane tabPane) {
        Tab activeTab = tabPane.getSelectionModel().getSelectedItem();
        if (activeTab == null) {
            AlertHelper.showErrorAlert("No active terminal", "Please open a terminal tab before running a snippet.");
            return;
        }

        TerminalSplitPane splitPane = tabSplitPaneMap.get(activeTab);
        JediTermWidget widget = (splitPane != null) ? splitPane.getActiveWidget() : null;
        if (widget == null) {
            AlertHelper.showErrorAlert("No active terminal", "The current tab is not a terminal session.");
            return;
        }

        ActivityLogger.logUserAction("EXECUTE_SNIPPET_DIRECT", "User executed snippet [" + snippet.getTitle() + "] (autoEnter=" + autoEnter + ") on active terminal in tab [" + activeTab.getText() + "]");

        // Bring user focus to the terminal
        Platform.runLater(() -> {
            if (splitPane != null) {
                splitPane.requestFocusOnActive();
            }
        });

        SwingUtilities.invokeLater(() -> {
            try {
                TtyConnector connector = widget.getTtyConnector();
                if (connector != null && connector.isConnected()) {
                    String code = snippet.getCode();
                    if (autoEnter) {
                        code += "\r";
                    }
                    connector.write(code);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Handles terminal focus change to sync toolbar shell selection and refresh highlights.
     */
    public void onTerminalFocused(TerminalPane pane, TopToolBar topToolBar, Runnable refreshHighlightsCallback) {
        if (topToolBar != null && pane != null) {
            topToolBar.selectShellOrHost(pane.getLocalShellType(), pane.getHostProfile());
        }
        if (refreshHighlightsCallback != null) {
            refreshHighlightsCallback.run();
        }
    }
}
