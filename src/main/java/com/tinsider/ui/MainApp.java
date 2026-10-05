package com.tinsider.ui;

import com.tinsider.config.TerminalTheme;
import com.tinsider.config.ThemeManager;
import com.tinsider.model.HighlightPackage;
import com.tinsider.model.HostProfile;
import com.tinsider.model.Snippet;
import com.tinsider.model.SnippetPackage;
import com.tinsider.model.WordHighlight;
import com.tinsider.storage.StorageManager;
import com.tinsider.terminal.TerminalTabFactory;
import com.tinsider.ui.components.navigation.SlimNavRail;
import com.tinsider.ui.components.sidebar.SidebarHighlightsView;
import com.tinsider.ui.components.sidebar.SidebarSnippetsView;
import com.tinsider.ui.components.sidebar.TerminalSidebar;
import com.tinsider.ui.components.terminal.TerminalWorkspace;
import com.tinsider.ui.components.terminal.split.TerminalPane;
import com.tinsider.ui.components.terminal.split.TerminalSplitPane;
import com.tinsider.ui.components.toolbar.TopToolBar;
import com.tinsider.ui.dashboard.ConnectionsDashboardView;
import com.tinsider.ui.dashboard.DashboardContainer;
import com.tinsider.ui.dashboard.DashboardTab;
import com.tinsider.ui.dashboard.HighlightsDashboardView;
import com.tinsider.ui.dashboard.HostsDashboardView;
import com.tinsider.ui.dashboard.SessionsDashboardView;
import com.tinsider.ui.dashboard.SnippetsDashboardView;
import com.tinsider.ui.dialogs.AlertHelper;
import com.tinsider.ui.dialogs.ModalOverlayManager;
import com.tinsider.ui.dialogs.SettingsDialog;
import com.tinsider.ui.session.TerminalSessionManager;
import com.tinsider.ui.theme.DwmUtil;
import com.tinsider.ui.theme.ThemeApplicator;
import com.tinsider.util.ActivityLogger;
import com.tinsider.util.LiveReloader;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Main application entry point and top-level coordinator for T-Insider.
 * Orchestrates application modes, window lifecycle, toolbar, navigation, and live reloaders.
 */
public class MainApp extends Application {

    public enum AppMode { DASHBOARD, TERMINAL }
    public enum DashboardTab { CONNECTIONS, HOSTS, SNIPPETS, TERMINALS, HIGHLIGHTS }

    private static MainApp instance;

    public static MainApp getInstance() {
        return instance;
    }

    public static Map<Tab, TerminalSplitPane> getTabSplitPaneMap() {
        return (instance != null && instance.sessionManager != null)
            ? instance.sessionManager.getTabSplitPaneMap()
            : null;
    }

    public static void onTerminalFocused(TerminalPane pane) {
        if (instance != null && instance.sessionManager != null) {
            instance.sessionManager.onTerminalFocused(pane, instance.topToolBar, instance::rebuildSidebarHighlights);
        }
    }

    private AppMode currentMode = AppMode.DASHBOARD;
    private boolean sidebarVisible = true;
    private boolean autoEnterSnippet = true;

    // Observable data lists
    private final ObservableList<HostProfile> hosts = FXCollections.observableArrayList();
    private final ObservableList<Snippet> snippets = FXCollections.observableArrayList();
    private final ObservableList<SnippetPackage> packages = FXCollections.observableArrayList();
    private final ObservableList<WordHighlight> highlights = FXCollections.observableArrayList();
    private final ObservableList<HighlightPackage> highlightPackages = FXCollections.observableArrayList();

    // Themes
    private final List<TerminalTheme> availableThemes = new ArrayList<>();
    private TerminalTheme currentTheme;

    private static final TerminalTheme DEFAULT_THEME = new TerminalTheme(
        "Default Space Blue",
        new com.jediterm.core.Color(9, 15, 34),     // bg: #090F22
        new com.jediterm.core.Color(226, 232, 240), // text: #e2e8f0
        new com.jediterm.core.Color(0, 173, 181),   // cursor: #00adb5
        new com.jediterm.core.Color(22, 27, 48)      // selection: #161B30
    );

    // Modular subsystems
    private TerminalSessionManager sessionManager;
    private TerminalWorkspace workspace;
    private TopToolBar topToolBar;
    private SlimNavRail slimNavRail;
    private DashboardContainer dashboardContainer;
    private TerminalSidebar terminalSidebar;
    private BorderPane root;
    private Stage primaryStage;
    private boolean initialFullScreen = false;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        ActivityLogger.logSystemEvent("APP_STARTUP", "T-Insider application started successfully.");
        instance = this;
        sessionManager = new TerminalSessionManager();

        // 1. Load custom Codicon font & window icon
        loadApplicationResources(primaryStage);

        // 2. Load themes and settings
        loadThemesAndSettings(primaryStage);

        // 3. Load persistent data
        hosts.addAll(StorageManager.loadHosts());
        snippets.addAll(StorageManager.loadSnippets());
        packages.addAll(StorageManager.loadPackages());
        highlights.addAll(StorageManager.loadHighlights());
        highlightPackages.addAll(StorageManager.loadHighlightPackages());

        // 4. Initialize Terminal Workspace
        workspace = new TerminalWorkspace(() -> {
            TopToolBar.DropdownItem selected = topToolBar.getSelectedItem();
            if (selected != null) {
                initiateConnectionFromDropdownItem(selected);
            }
        });

        workspace.getTabPane().getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null) {
                String oldTabTitle = (oldTab != null) ? oldTab.getText() : "None";
                String newTabTitle = newTab.getText();
                if (!newTabTitle.equals("+")) {
                    ActivityLogger.logUserAction("TAB_SWITCH", "User switched active tab from [" + oldTabTitle + "] to [" + newTabTitle + "]");
                }
                Platform.runLater(() -> {
                    TerminalSplitPane splitPane = sessionManager.getSplitPaneForTab(newTab);
                    if (splitPane != null) {
                        splitPane.requestFocusOnActive();
                    }
                    repaintAllTerminals();
                });
            }
        });

        // 5. Initialize Dashboard subsystem
        ConnectionsDashboardView connectionsView = new ConnectionsDashboardView(hosts, this::initiateConnectionFromDropdownItem);
        HostsDashboardView hostsView = new HostsDashboardView(hosts);
        SnippetsDashboardView snippetsView = new SnippetsDashboardView(snippets, packages);
        SessionsDashboardView sessionsView = new SessionsDashboardView(workspace, () -> switchMode(AppMode.TERMINAL));
        HighlightsDashboardView highlightsView = new HighlightsDashboardView(highlights, highlightPackages, this::repaintAllTerminals);

        dashboardContainer = new DashboardContainer(connectionsView, hostsView, snippetsView, sessionsView, highlightsView);

        slimNavRail = new SlimNavRail(tab -> {
            com.tinsider.ui.dashboard.DashboardTab dTab = switch (tab) {
                case CONNECTIONS -> com.tinsider.ui.dashboard.DashboardTab.CONNECTIONS;
                case HOSTS -> com.tinsider.ui.dashboard.DashboardTab.HOSTS;
                case SNIPPETS -> com.tinsider.ui.dashboard.DashboardTab.SNIPPETS;
                case TERMINALS -> com.tinsider.ui.dashboard.DashboardTab.TERMINALS;
                case HIGHLIGHTS -> com.tinsider.ui.dashboard.DashboardTab.HIGHLIGHTS;
            };
            dashboardContainer.switchDashboardTab(dTab);
        });

        // 6. Initialize Terminal Sidebar subsystem
        SidebarSnippetsView sidebarSnippetsView = new SidebarSnippetsView(
            snippets,
            packages,
            (snippet, autoEnter) -> sessionManager.runSnippet(snippet, autoEnter, workspace.getTabPane()),
            snippetsView::showAddSnippetDialog,
            () -> snippetsView.showAddSnippetDialog(null)
        );

        SidebarHighlightsView sidebarHighlightsView = new SidebarHighlightsView(
            highlights,
            highlightPackages,
            () -> {
                Tab selectedTab = workspace.getTabPane().getSelectionModel().getSelectedItem();
                return selectedTab != null ? sessionManager.getSplitPaneForTab(selectedTab) : null;
            }
        );

        terminalSidebar = new TerminalSidebar(sidebarSnippetsView, sidebarHighlightsView);

        // 7. Initialize TopToolBar
        Map<String, String> settings = StorageManager.loadSettings();
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String defaultShell = isWindows ? "CMD" : "Bash";
        String savedShell = settings.getOrDefault("shell", defaultShell);

        topToolBar = new TopToolBar(
            savedShell,
            hosts,
            shell -> {
                Map<String, String> innerSettings = StorageManager.loadSettings();
                innerSettings.put("shell", shell);
                StorageManager.saveSettings(innerSettings);
            },
            () -> {
                TerminalTabFactory.increaseZoom(sessionManager.getAllSplitPanes());
                topToolBar.updateZoomLabel(TerminalTabFactory.getGlobalFontSize());
            },
            () -> {
                TerminalTabFactory.decreaseZoom(sessionManager.getAllSplitPanes());
                topToolBar.updateZoomLabel(TerminalTabFactory.getGlobalFontSize());
            },
            () -> {
                TerminalTabFactory.resetZoom(sessionManager.getAllSplitPanes());
                topToolBar.updateZoomLabel(TerminalTabFactory.getGlobalFontSize());
            },
            this::toggleSidebar,
            () -> switchMode(AppMode.DASHBOARD),
            () -> switchMode(AppMode.TERMINAL),
            this::showSettingsDialog,
            this::toggleFullScreen
        );
        topToolBar.setShellSelectionVisible(false);
        topToolBar.setFullScreenIcon(initialFullScreen);

        // 8. Build Root Layout
        root = new BorderPane();
        VBox topContainer = new VBox(topToolBar);
        root.setTop(topContainer);

        // Initial mode: Dashboard
        switchMode(AppMode.DASHBOARD);
        dashboardContainer.switchDashboardTab(com.tinsider.ui.dashboard.DashboardTab.CONNECTIONS);

        // Synchronize active sessions view on tab modifications
        workspace.getTabPane().getTabs().addListener((ListChangeListener<Tab>) change -> {
            dashboardContainer.getSessionsView().rebuild();
            repaintAllTerminals();
        });

        // 9. Scene & Accelerators
        StackPane appRoot = new StackPane();
        StackPane modalOverlay = new StackPane();
        modalOverlay.setPickOnBounds(true);
        modalOverlay.setVisible(false);
        appRoot.getChildren().addAll(root, modalOverlay);

        ModalOverlayManager.init(modalOverlay, root);

        Scene scene = new Scene(appRoot, 1280, 720);
        String styleCss = getClass().getResource("/style.css") != null ? getClass().getResource("/style.css").toExternalForm() : "";
        if (!styleCss.isEmpty()) {
            scene.getStylesheets().add(styleCss);
        }

        registerKeyboardAccelerators(scene);
        primaryStage.setScene(scene);

        // 10. Listen for newly opened dialogs/windows to apply theme and DWM styling
        DwmUtil.registerWindowStylingListener(scene, () -> currentTheme);

        // 11. Live reloader for theme files
        setupLiveReloader();

        primaryStage.show();
        applyTheme(currentTheme);

        // 12. Safely restore borderless fullscreen after stage is shown and HWND is active
        if (initialFullScreen) {
            Platform.runLater(() -> {
                DwmUtil.setBorderlessFullScreen(primaryStage, true);
                if (topToolBar != null) {
                    topToolBar.setFullScreenIcon(true);
                }
            });
        }

        primaryStage.setOnCloseRequest(e -> {
            ActivityLogger.logSystemEvent("APP_SHUTDOWN", "T-Insider application shutting down.");
            for (Tab tab : new ArrayList<>(workspace.getTabPane().getTabs())) {
                sessionManager.closeTab(tab, workspace.getTabPane(), null);
            }
            Platform.exit();
            System.exit(0);
        });
    }

    private void toggleFullScreen() {
        Stage stage = (primaryStage != null) ? primaryStage : (Stage) topToolBar.getScene().getWindow();
        boolean isNowFullScreen = DwmUtil.toggleBorderlessFullScreen(stage);
        if (topToolBar != null) {
            topToolBar.setFullScreenIcon(isNowFullScreen);
        }
        Map<String, String> innerSettings = StorageManager.loadSettings();
        innerSettings.put("fullscreen", String.valueOf(isNowFullScreen));
        StorageManager.saveSettings(innerSettings);
    }

    private void registerKeyboardAccelerators(Scene scene) {
        registerZoomAccelerators(scene);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F11), this::toggleFullScreen);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.ESCAPE), () -> {
            if (DwmUtil.isBorderlessFullScreen()) {
                toggleFullScreen();
            }
        });
    }

    private void loadApplicationResources(Stage primaryStage) {
        try {
            InputStream fontStream = getClass().getResourceAsStream("/icons/codicon.ttf");
            if (fontStream != null) {
                Font loadedFont = Font.loadFont(fontStream, 13);
                if (loadedFont != null) {
                    System.out.println("[MainApp] Successfully loaded Codicons font: " + loadedFont.getName());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        primaryStage.setTitle("T-Insider");

        try {
            InputStream iconStream = getClass().getResourceAsStream("/icon.png");
            if (iconStream != null) {
                primaryStage.getIcons().add(new Image(iconStream));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadThemesAndSettings(Stage stage) {
        availableThemes.add(DEFAULT_THEME);
        currentTheme = DEFAULT_THEME;

        File themesDir = new File("./themes");
        if (themesDir.exists() && themesDir.isDirectory()) {
            File[] files = themesDir.listFiles((dir, name) -> name.endsWith(".terminal"));
            if (files != null) {
                for (File file : files) {
                    try {
                        availableThemes.add(ThemeManager.loadThemeFromFile(file));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        Map<String, String> settings = StorageManager.loadSettings();
        autoEnterSnippet = Boolean.parseBoolean(settings.getOrDefault("autoEnterSnippet", "true"));

        String savedThemeName = settings.get("theme");
        if (savedThemeName == null || savedThemeName.trim().isEmpty()) {
            savedThemeName = "Default Space Blue";
            for (TerminalTheme theme : availableThemes) {
                if ("Gotham-default".equalsIgnoreCase(theme.getName())) {
                    savedThemeName = theme.getName();
                    break;
                }
            }
        }

        String fullScreen = settings.get("fullscreen");
        initialFullScreen = Boolean.parseBoolean(fullScreen);

        for (TerminalTheme theme : availableThemes) {
            if (theme.getName().equalsIgnoreCase(savedThemeName)) {
                currentTheme = theme;
                break;
            }
        }
    }

    private void toggleSidebar() {
        sidebarVisible = !sidebarVisible;
        if (sidebarVisible) {
            root.setLeft(terminalSidebar);
            if (topToolBar != null) {
                topToolBar.setSidebarButtonText("Hide Sidebar");
            }
        } else {
            root.setLeft(null);
            if (topToolBar != null) {
                topToolBar.setSidebarButtonText("Show Sidebar");
            }
        }
    }

    public void switchMode(AppMode mode) {
        ActivityLogger.logUserAction("SWITCH_APP_MODE", "User switched application mode to: " + mode);
        this.currentMode = mode;
        if (mode == AppMode.DASHBOARD) {
            root.setLeft(slimNavRail);
            root.setCenter(dashboardContainer);
            dashboardContainer.getSessionsView().rebuild();
            if (topToolBar != null) {
                topToolBar.setShellSelectionVisible(false);
                topToolBar.setSidebarToggleVisible(false);
                topToolBar.setZoomVisible(false);
                topToolBar.setTerminalButtonVisible(sessionManager.hasActiveSessions());
            }
        } else {
            if (sidebarVisible) {
                root.setLeft(terminalSidebar);
                if (topToolBar != null) {
                    topToolBar.setSidebarButtonText("Hide Sidebar");
                }
            } else {
                root.setLeft(null);
                if (topToolBar != null) {
                    topToolBar.setSidebarButtonText("Show Sidebar");
                }
            }
            root.setCenter(workspace);
            if (topToolBar != null) {
                topToolBar.setShellSelectionVisible(true);
                topToolBar.setSidebarToggleVisible(true);
                topToolBar.setZoomVisible(true);
                topToolBar.setTerminalButtonVisible(false);
            }
        }
    }

    public void initiateConnectionFromDropdownItem(TopToolBar.DropdownItem item) {
        if (item == null) return;
        if (item.isHost()) {
            sessionManager.connectToHost(
                item.getHostProfile(),
                workspace.getTabPane(),
                () -> currentTheme,
                tab -> sessionManager.closeTab(tab, workspace.getTabPane(), () -> switchMode(AppMode.DASHBOARD))
            );
        } else {
            sessionManager.openLocalTerminal(
                item.getLabel(),
                workspace.getTabPane(),
                () -> currentTheme,
                tab -> sessionManager.closeTab(tab, workspace.getTabPane(), () -> switchMode(AppMode.DASHBOARD))
            );
        }
        switchMode(AppMode.TERMINAL);
    }

    public void applyTheme(TerminalTheme theme) {
        this.currentTheme = theme;
        if (root != null && root.getScene() != null) {
            ThemeApplicator.applyTheme(theme, root.getScene(), sessionManager.getAllSplitPanes());
        }
    }

    public void repaintAllTerminals() {
        if (sessionManager != null) {
            ThemeApplicator.repaintAllTerminals(sessionManager.getAllSplitPanes(), currentTheme);
        }
    }

    public void rebuildSidebarHighlights() {
        if (terminalSidebar != null) {
            terminalSidebar.rebuildHighlights();
        }
    }

    private void showSettingsDialog() {
        Map<String, String> currentSettings = StorageManager.loadSettings();
        boolean initialGradients = Boolean.parseBoolean(currentSettings.getOrDefault("gradientEnabled", "false"));

        SettingsDialog dialog = new SettingsDialog(
            availableThemes,
            currentTheme,
            theme -> {
                applyTheme(theme);
                Map<String, String> innerSettings = StorageManager.loadSettings();
                innerSettings.put("theme", theme.getName());
                StorageManager.saveSettings(innerSettings);
            },
            autoEnterSnippet,
            checked -> {
                autoEnterSnippet = checked;
                Map<String, String> innerSettings = StorageManager.loadSettings();
                innerSettings.put("autoEnterSnippet", String.valueOf(checked));
                StorageManager.saveSettings(innerSettings);
            },
            initialGradients,
            checked -> {
                Map<String, String> innerSettings = StorageManager.loadSettings();
                if (checked) {
                    innerSettings.put("gradientEnabled", "true");
                } else {
                    innerSettings.remove("gradientEnabled");
                }
                StorageManager.saveSettings(innerSettings);
                applyTheme(currentTheme);
            }
        );
        AlertHelper.showModal(dialog);
    }

    private void registerZoomAccelerators(Scene scene) {
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.EQUALS, KeyCombination.SHORTCUT_DOWN), () -> {
            TerminalTabFactory.increaseZoom(sessionManager.getAllSplitPanes());
            topToolBar.updateZoomLabel(TerminalTabFactory.getGlobalFontSize());
        });
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.MINUS, KeyCombination.SHORTCUT_DOWN), () -> {
            TerminalTabFactory.decreaseZoom(sessionManager.getAllSplitPanes());
            topToolBar.updateZoomLabel(TerminalTabFactory.getGlobalFontSize());
        });
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.DIGIT0, KeyCombination.SHORTCUT_DOWN), () -> {
            TerminalTabFactory.resetZoom(sessionManager.getAllSplitPanes());
            topToolBar.updateZoomLabel(TerminalTabFactory.getGlobalFontSize());
        });
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.ADD, KeyCombination.SHORTCUT_DOWN), () -> {
            TerminalTabFactory.increaseZoom(sessionManager.getAllSplitPanes());
            topToolBar.updateZoomLabel(TerminalTabFactory.getGlobalFontSize());
        });
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.SUBTRACT, KeyCombination.SHORTCUT_DOWN), () -> {
            TerminalTabFactory.decreaseZoom(sessionManager.getAllSplitPanes());
            topToolBar.updateZoomLabel(TerminalTabFactory.getGlobalFontSize());
        });
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.NUMPAD0, KeyCombination.SHORTCUT_DOWN), () -> {
            TerminalTabFactory.resetZoom(sessionManager.getAllSplitPanes());
            topToolBar.updateZoomLabel(TerminalTabFactory.getGlobalFontSize());
        });
    }

    private void setupLiveReloader() {
        Runnable themeReloader = () -> {
            Platform.runLater(() -> {
                System.out.println("[LiveReloader] Theme files changed! Reloading UI colors and styles...");
                String currentThemeName = currentTheme != null ? currentTheme.getName() : "";
                TerminalTheme newTheme = null;
                File watchDir = new File("./themes");
                File[] themeFiles = watchDir.listFiles();
                if (themeFiles != null) {
                    for (File file : themeFiles) {
                        if (file.getName().endsWith(".terminal")) {
                            TerminalTheme t = ThemeManager.loadThemeFromFile(file);
                            if (t.getName().equalsIgnoreCase(currentThemeName)) {
                                newTheme = t;
                                break;
                            }
                        }
                    }
                }
                if (newTheme != null) {
                    applyTheme(newTheme);
                }
            });
        };
        LiveReloader.startWatching("./themes", themeReloader);
        LiveReloader.startWatching("./config", themeReloader);
    }

    public static void main(String[] args) {
        // Fix JavaFX High-DPI maximized window shift and button shrinking bug on Windows
        System.setProperty("glass.win.minVisualBounds", "true");
        launch(args);
    }
}
