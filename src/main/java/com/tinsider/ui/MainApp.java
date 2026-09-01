package com.tinsider.ui;

import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.ui.JediTermWidget;
import com.tinsider.config.TerminalTheme;
import com.tinsider.config.ThemeManager;
import com.tinsider.model.HostProfile;
import com.tinsider.model.Snippet;
import com.tinsider.model.SnippetPackage;
import com.tinsider.model.WordHighlight;
import com.tinsider.model.HighlightPackage;
import com.tinsider.storage.StorageManager;
import com.tinsider.terminal.ConnectionManager;
import com.tinsider.terminal.TerminalTabFactory;
import com.tinsider.ui.components.terminal.TerminalWorkspace;
import com.tinsider.ui.components.toolbar.TopToolBar;
import com.tinsider.ui.dialogs.AlertHelper;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingNode;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javax.swing.*;
import java.io.File;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import com.tinsider.ui.dialogs.HostDialog;
import com.tinsider.ui.dialogs.SnippetDialog;
import com.tinsider.ui.dialogs.HighlightDialog;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public class MainApp extends Application {
    public enum AppMode { DASHBOARD, TERMINAL }
    public enum DashboardTab { CONNECTIONS, HOSTS, SNIPPETS, TERMINALS, HIGHLIGHTS }

    private static MainApp instance;

    public static void onTerminalFocused(com.tinsider.ui.components.terminal.split.TerminalPane pane) {
        if (instance != null && instance.topToolBar != null && pane != null) {
            instance.topToolBar.selectShellOrHost(pane.getLocalShellType(), pane.getHostProfile());
        }
        // Force refresh the sidebar highlights list when active terminal focus changes
        if (instance != null) {
            instance.rebuildSidebarHighlights();
        }
    }

    private AppMode currentMode = AppMode.DASHBOARD;
    private DashboardTab currentDashboardTab = DashboardTab.CONNECTIONS;
    private boolean sidebarVisible = true;
    private boolean autoEnterSnippet = true;

    private final ObservableList<HostProfile> hosts = FXCollections.observableArrayList();
    private final ObservableList<Snippet> snippets = FXCollections.observableArrayList();
    private final ObservableList<SnippetPackage> packages = FXCollections.observableArrayList();
    private final ObservableList<WordHighlight> highlights = FXCollections.observableArrayList();
    private final ObservableList<HighlightPackage> highlightPackages = FXCollections.observableArrayList();
    private final Set<String> expandedPackageIds = new java.util.HashSet<>();

    private final Map<Tab, com.tinsider.ui.components.terminal.split.TerminalSplitPane> tabSplitPaneMap = new HashMap<>();
    
    private static Map<Tab, com.tinsider.ui.components.terminal.split.TerminalSplitPane> tabSplitPaneMapStaticInstance;
    
    public static Map<Tab, com.tinsider.ui.components.terminal.split.TerminalSplitPane> getTabSplitPaneMap() {
        return tabSplitPaneMapStaticInstance;
    }
    private final List<TerminalTheme> availableThemes = new ArrayList<>();
    private TerminalTheme currentTheme;

    private static final TerminalTheme DEFAULT_THEME = new TerminalTheme(
        "Default Space Blue",
        new com.jediterm.core.Color(9, 15, 34),     // bg: #090F22
        new com.jediterm.core.Color(226, 232, 240), // text: #e2e8f0
        new com.jediterm.core.Color(0, 173, 181),   // cursor: #00adb5
        new com.jediterm.core.Color(22, 27, 48)     // selection: #161B30
    );

    private TerminalWorkspace workspace;
    private TopToolBar topToolBar;

    // Layout Containers for Termius Redesign
    private BorderPane root;
    private VBox slimNavRail;
    private VBox snippetExecutionSidebar;
    private StackPane dashboardCenterStack;
    private VBox terminalSnippetsContainer;
    private javafx.scene.Node currentlySelectedCardNode;

    // Dashboard Tabs
    private VBox connectionsDashboard;
    private VBox hostsDashboard;
    private VBox snippetsDashboard;
    private VBox terminalsDashboard;
    private VBox highlightsDashboard;

    // Grids
    private FlowPane localConnectionsGrid;
    private FlowPane remoteConnectionsGrid;
    private FlowPane hostsGrid;
    private VBox snippetsDashboardContainer;
    private VBox highlightsDashboardContainer;
    private FlowPane terminalsGrid;
    private FlowPane highlightsGrid;
    private ScrollPane connectionsGridScrollPane;
    private ScrollPane hostsGridScrollPane;
    private ScrollPane snippetsGridScrollPane;
    private ScrollPane terminalsGridScrollPane;
    private ScrollPane highlightsGridScrollPane;
    private VBox terminalHighlightsContainer;

    // Selection Trackers
    private final ObjectProperty<WordHighlight> selectedHighlight = new SimpleObjectProperty<>(null);
    private final ObjectProperty<HostProfile> selectedHost = new SimpleObjectProperty<>(null);
    private final ObjectProperty<Snippet> selectedSnippet = new SimpleObjectProperty<>(null);
    private final ObjectProperty<Tab> selectedSession = new SimpleObjectProperty<>(null);
    private final ObjectProperty<TopToolBar.DropdownItem> selectedConnectionItem = new SimpleObjectProperty<>(null);
    private Label noActiveSessionsLabel;

    // Search Fields
    private TextField connectionsSearchField;
    private TextField hostsSearchField;
    private TextField snippetsSearchField;
    private TextField highlightsSearchField;
    private TextField sidebarSearchField;
    private TextField sidebarHighlightsSearchField;


    @Override
    public void start(Stage primaryStage) {
        com.tinsider.util.ActivityLogger.logSystemEvent("APP_STARTUP", "T-Insider application started successfully.");
        tabSplitPaneMapStaticInstance = tabSplitPaneMap;
        instance = this;

        // Load custom Codicon font
        try {
            java.io.InputStream fontStream = getClass().getResourceAsStream("/icons/codicon.ttf");
            if (fontStream != null) {
                javafx.scene.text.Font loadedFont = javafx.scene.text.Font.loadFont(fontStream, 13);
                if (loadedFont != null) {
                    System.out.println("[MainApp] Successfully loaded Codicons font: " + loadedFont.getName());
                } else {
                    System.err.println("[MainApp] Failed to parse Codicons font.");
                }
            } else {
                System.err.println("[MainApp] Codicons font file not found in resources/icons.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        primaryStage.setTitle("T-Insider");

        // Set the program icon
        try {
            java.io.InputStream iconStream = getClass().getResourceAsStream("/icon.png");
            if (iconStream != null) {
                primaryStage.getIcons().add(new javafx.scene.image.Image(iconStream));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Load custom themes
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

        // Load data
        hosts.addAll(StorageManager.loadHosts());
        snippets.addAll(StorageManager.loadSnippets());
        packages.addAll(StorageManager.loadPackages());
        highlights.addAll(StorageManager.loadHighlights());
        highlightPackages.addAll(StorageManager.loadHighlightPackages());

        // Load settings and restore shell type + theme selection
        Map<String, String> settings = StorageManager.loadSettings();
        autoEnterSnippet = Boolean.parseBoolean(settings.getOrDefault("autoEnterSnippet", "true"));
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String defaultShell = isWindows ? "CMD" : "Bash";
        String savedShell = settings.getOrDefault("shell", defaultShell);
        
        String savedThemeName = settings.get("theme");
        if (savedThemeName == null || savedThemeName.trim().isEmpty()) {
            savedThemeName = "Default Space Blue"; // Default fallback
            for (TerminalTheme theme : availableThemes) {
                if ("Gotham-default".equalsIgnoreCase(theme.getName())) {
                    savedThemeName = theme.getName();
                    break;
                }
            }
        }

        for (TerminalTheme theme : availableThemes) {
            if (theme.getName().equalsIgnoreCase(savedThemeName)) {
                currentTheme = theme;
                break;
            }
        }

        // 1. Instantiate Terminal Workspace (Center in Terminal Mode)
        workspace = new TerminalWorkspace(() -> {
            TopToolBar.DropdownItem selected = topToolBar.getSelectedItem();
            if (selected != null) {
                if (selected.isHost()) {
                    connectToHost(selected.getHostProfile());
                } else {
                    openLocalTerminal(selected.getLabel());
                }
            }
        });
        workspace.getTabPane().getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null) {
                String oldTabTitle = (oldTab != null) ? oldTab.getText() : "None";
                String newTabTitle = newTab.getText();
                if (!newTabTitle.equals("+")) {
                    com.tinsider.util.ActivityLogger.logUserAction("TAB_SWITCH", "User switched active tab from [" + oldTabTitle + "] to [" + newTabTitle + "]");
                }
                Platform.runLater(() -> {
                    com.tinsider.ui.components.terminal.split.TerminalSplitPane splitPane = tabSplitPaneMap.get(newTab);
                    if (splitPane != null) {
                        splitPane.requestFocusOnActive();
                    }
                    repaintAllTerminals();
                });
            }
        });

        // 2. Build Dashboard Mode - Left Slim Nav Rail
        slimNavRail = new VBox(20);
        slimNavRail.setPadding(new Insets(30, 10, 30, 10));
        slimNavRail.setPrefWidth(120);
        slimNavRail.setAlignment(Pos.TOP_CENTER);
        slimNavRail.getStyleClass().add("slim-nav-rail");

        Label appTitleLabel = new Label("T-Insider");
        appTitleLabel.getStyleClass().add("app-title");
        appTitleLabel.setStyle("-fx-font-size: 16px; -fx-padding: 0 0 20px 0;");

        ToggleButton navConnectionsBtn = new ToggleButton("Connections");
        ToggleButton navHostsBtn = new ToggleButton("Hosts");
        ToggleButton navSnippetsBtn = new ToggleButton("Snippets");
        ToggleButton navTerminalsBtn = new ToggleButton("Terminals");
        ToggleButton navHighlightsBtn = new ToggleButton("Highlighter");

        ToggleGroup navGroup = new ToggleGroup();
        navConnectionsBtn.setToggleGroup(navGroup);
        navHostsBtn.setToggleGroup(navGroup);
        navSnippetsBtn.setToggleGroup(navGroup);
        navTerminalsBtn.setToggleGroup(navGroup);
        navHighlightsBtn.setToggleGroup(navGroup);
        navConnectionsBtn.setSelected(true);

        navConnectionsBtn.setMaxWidth(Double.MAX_VALUE);
        navHostsBtn.setMaxWidth(Double.MAX_VALUE);
        navSnippetsBtn.setMaxWidth(Double.MAX_VALUE);
        navTerminalsBtn.setMaxWidth(Double.MAX_VALUE);
        navHighlightsBtn.setMaxWidth(Double.MAX_VALUE);

        navConnectionsBtn.getStyleClass().add("nav-button");
        navHostsBtn.getStyleClass().add("nav-button");
        navSnippetsBtn.getStyleClass().add("nav-button");
        navTerminalsBtn.getStyleClass().add("nav-button");
        navHighlightsBtn.getStyleClass().add("nav-button");

        slimNavRail.getChildren().addAll(appTitleLabel, navConnectionsBtn, navHostsBtn, navSnippetsBtn, navTerminalsBtn, navHighlightsBtn);

        // Navigation Actions
        navConnectionsBtn.setOnAction(e -> switchDashboardTab(DashboardTab.CONNECTIONS));
        navHostsBtn.setOnAction(e -> switchDashboardTab(DashboardTab.HOSTS));
        navSnippetsBtn.setOnAction(e -> switchDashboardTab(DashboardTab.SNIPPETS));
        navTerminalsBtn.setOnAction(e -> switchDashboardTab(DashboardTab.TERMINALS));
        navHighlightsBtn.setOnAction(e -> switchDashboardTab(DashboardTab.HIGHLIGHTS));

        // 3. Build Dashboard Center Panes (Connections, Hosts, Snippets, Terminals)
        dashboardCenterStack = new StackPane();
        dashboardCenterStack.getStyleClass().add("dashboard-center-stack");

        // --- Connections Dashboard ---
        connectionsDashboard = new VBox(15);
        connectionsDashboard.setPadding(new Insets(25));
        connectionsDashboard.getStyleClass().add("dashboard-view");

        Label connectionsTitle = new Label("Quick Connections");
        connectionsTitle.getStyleClass().add("dashboard-title");
        connectionsTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        connectionsSearchField = new TextField();
        connectionsSearchField.setPromptText("🔍 Search hosts or local terminals...");
        connectionsSearchField.getStyleClass().add("text-field");
        connectionsSearchField.textProperty().addListener((obs, oldVal, newVal) -> rebuildConnectionsGrid());

        VBox connectionsContainer = new VBox(20);
        connectionsContainer.setStyle("-fx-background-color: transparent;");

        // Local terminals section
        VBox localSection = new VBox(10);
        HBox localHeader = new HBox(8);
        localHeader.setAlignment(Pos.CENTER_LEFT);
        Label localIcon = new Label("\uEA85");
        localIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 15px; -fx-text-fill: #94a3b8;");
        Label localText = new Label("Local Terminals");
        localText.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #94a3b8;");
        localHeader.getChildren().addAll(localIcon, localText);

        localConnectionsGrid = new FlowPane(15, 15);
        localConnectionsGrid.setPadding(new Insets(10, 0, 10, 0));
        localSection.getChildren().addAll(localHeader, localConnectionsGrid);

        // Remote connections section
        VBox remoteSection = new VBox(10);
        HBox remoteHeader = new HBox(8);
        remoteHeader.setAlignment(Pos.CENTER_LEFT);
        Label remoteIcon = new Label("\uEB01");
        remoteIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 15px; -fx-text-fill: #94a3b8;");
        Label remoteText = new Label("Remote Connections");
        remoteText.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #94a3b8;");
        remoteHeader.getChildren().addAll(remoteIcon, remoteText);

        remoteConnectionsGrid = new FlowPane(15, 15);
        remoteConnectionsGrid.setPadding(new Insets(10, 0, 10, 0));
        remoteSection.getChildren().addAll(remoteHeader, remoteConnectionsGrid);

        connectionsContainer.getChildren().addAll(localSection, remoteSection);

        connectionsGridScrollPane = new ScrollPane(connectionsContainer);
        connectionsGridScrollPane.setFitToWidth(true);
        connectionsGridScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        connectionsGridScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        connectionsGridScrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(connectionsGridScrollPane, Priority.ALWAYS);

        HBox connectionsButtons = new HBox(10);
        Button connectBtn = new Button("Connect");
        Label connectIcon = new Label("\uEB39");
        connectIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 12px;");
        connectIcon.getStyleClass().add("button-icon");
        connectBtn.setGraphic(connectIcon);
        connectBtn.getStyleClass().add("button");
        connectBtn.setPrefWidth(100);
        connectionsButtons.getChildren().addAll(connectBtn);
        connectionsButtons.setAlignment(Pos.CENTER_LEFT);

        connectBtn.setOnAction(e -> {
            initiateConnectionFromDropdownItem(selectedConnectionItem.get());
        });
        connectBtn.disableProperty().bind(selectedConnectionItem.isNull());

        connectionsDashboard.getChildren().addAll(connectionsTitle, connectionsSearchField, connectionsGridScrollPane, connectionsButtons);

        // --- Hosts Dashboard ---
        hostsDashboard = new VBox(15);
        hostsDashboard.setPadding(new Insets(25));
        hostsDashboard.getStyleClass().add("dashboard-view");

        Label hostsTitle = new Label("Hosts Management");
        hostsTitle.getStyleClass().add("dashboard-title");
        hostsTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        hostsSearchField = new TextField();
        hostsSearchField.setPromptText("🔍 Search hosts by name, IP, or username...");
        hostsSearchField.getStyleClass().add("text-field");
        hostsSearchField.textProperty().addListener((obs, oldVal, newVal) -> rebuildHostsGrid());

        hostsGrid = new FlowPane(15, 15);
        hostsGrid.setPadding(new Insets(10));
        hostsGridScrollPane = new ScrollPane(hostsGrid);
        hostsGridScrollPane.setFitToWidth(true);
        hostsGridScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        hostsGridScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        hostsGridScrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(hostsGridScrollPane, Priority.ALWAYS);

        HBox hostButtons = new HBox(10);
        Button addHostBtn = new Button("Add");
        Label addHostIcon = new Label("\uEA60");
        addHostIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 12px;");
        addHostIcon.getStyleClass().add("button-icon");
        addHostBtn.setGraphic(addHostIcon);

        Button editHostBtn = new Button("Edit");
        Label editHostIcon = new Label("\uEA73");
        editHostIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 12px;");
        editHostIcon.getStyleClass().add("button-icon");
        editHostBtn.setGraphic(editHostIcon);

        Button deleteHostBtn = new Button("Delete");
        Label deleteHostIcon = new Label("\uEA81");
        deleteHostIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 12px;");
        deleteHostIcon.getStyleClass().add("button-icon");
        deleteHostBtn.setGraphic(deleteHostIcon);

        hostButtons.getChildren().addAll(addHostBtn, editHostBtn, deleteHostBtn);
        hostButtons.setAlignment(Pos.CENTER_LEFT);

        editHostBtn.disableProperty().bind(selectedHost.isNull());
        deleteHostBtn.disableProperty().bind(selectedHost.isNull());

        addHostBtn.setOnAction(e -> showAddHostDialog(hosts, null));
        editHostBtn.setOnAction(e -> {
            HostProfile selected = selectedHost.get();
            if (selected != null) showAddHostDialog(hosts, selected);
        });
        deleteHostBtn.setOnAction(e -> {
            HostProfile selected = selectedHost.get();
            if (selected != null) {
                hosts.remove(selected);
                StorageManager.saveHosts(new ArrayList<>(hosts));
                selectedHost.set(null);
            }
        });

        hosts.addListener(new javafx.collections.ListChangeListener<HostProfile>() {
            @Override
            public void onChanged(javafx.collections.ListChangeListener.Change<? extends HostProfile> c) {
                rebuildHostsGrid();
            }
        });

        hostsDashboard.getChildren().addAll(hostsTitle, hostsSearchField, hostsGridScrollPane, hostButtons);

        // --- Snippets Dashboard ---
        snippetsDashboard = new VBox(15);
        snippetsDashboard.setPadding(new Insets(25));
        snippetsDashboard.getStyleClass().add("dashboard-view");

        Label snippetsTitle = new Label("Code Snippets");
        snippetsTitle.getStyleClass().add("dashboard-title");
        snippetsTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        snippetsSearchField = new TextField();
        snippetsSearchField.setPromptText("🔍 Search snippets by title/code or packages by name...");
        snippetsSearchField.getStyleClass().add("text-field");
        snippetsSearchField.textProperty().addListener((obs, oldVal, newVal) -> rebuildSnippetsGrid());

        snippetsDashboardContainer = new VBox(20);
        snippetsDashboardContainer.setPadding(new Insets(10));
        snippetsGridScrollPane = new ScrollPane(snippetsDashboardContainer);
        snippetsGridScrollPane.setFitToWidth(true);
        snippetsGridScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        snippetsGridScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        snippetsGridScrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(snippetsGridScrollPane, Priority.ALWAYS);

        HBox snippetButtons = new HBox(10);
        Button addSnippetBtn = new Button("Add Snippet");
        Label addSnippetIcon = new Label("\uEA60");
        addSnippetIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 12px;");
        addSnippetIcon.getStyleClass().add("button-icon");
        addSnippetBtn.setGraphic(addSnippetIcon);

        Button editSnippetBtn = new Button("Edit");
        Label editSnippetIcon = new Label("\uEA73");
        editSnippetIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 12px;");
        editSnippetIcon.getStyleClass().add("button-icon");
        editSnippetBtn.setGraphic(editSnippetIcon);

        Button deleteSnippetBtn = new Button("Delete");
        Label deleteSnippetIcon = new Label("\uEA81");
        deleteSnippetIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 12px;");
        deleteSnippetIcon.getStyleClass().add("button-icon");
        deleteSnippetBtn.setGraphic(deleteSnippetIcon);

        Button addPackageBtn = new Button("Add Package");
        Label addPackageIcon = new Label("\uEA60");
        addPackageIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 12px;");
        addPackageIcon.getStyleClass().add("button-icon");
        addPackageBtn.setGraphic(addPackageIcon);

        Button deletePackageBtn = new Button("Delete Package");
        Label deletePackageIcon = new Label("\uEA81");
        deletePackageIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 12px;");
        deletePackageIcon.getStyleClass().add("button-icon");
        deletePackageBtn.setGraphic(deletePackageIcon);

        snippetButtons.getChildren().addAll(addSnippetBtn, editSnippetBtn, deleteSnippetBtn, addPackageBtn, deletePackageBtn);
        snippetButtons.setAlignment(Pos.CENTER_LEFT);

        editSnippetBtn.disableProperty().bind(selectedSnippet.isNull());
        deleteSnippetBtn.disableProperty().bind(selectedSnippet.isNull());

        addSnippetBtn.setOnAction(e -> showAddSnippetDialog(snippets, null));
        editSnippetBtn.setOnAction(e -> {
            Snippet selected = selectedSnippet.get();
            if (selected != null) showAddSnippetDialog(snippets, selected);
        });
        deleteSnippetBtn.setOnAction(e -> {
            Snippet selected = selectedSnippet.get();
            if (selected != null) {
                snippets.remove(selected);
                StorageManager.saveSnippets(new ArrayList<>(snippets));
                selectedSnippet.set(null);
            }
        });

        addPackageBtn.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Add Package");
            dialog.setContentText("Package Name:");
            com.tinsider.ui.dialogs.AlertHelper.themeDialog(dialog);
            Optional<String> result = dialog.showAndWait();
            result.ifPresent(name -> {
                if (!name.trim().isEmpty()) {
                    SnippetPackage pkg = new SnippetPackage(name.trim());
                    packages.add(pkg);
                    StorageManager.savePackages(new ArrayList<>(packages));
                    rebuildSnippetsGrid();
                }
            });
        });

        deletePackageBtn.setOnAction(e -> {
            if (packages.isEmpty()) {
                com.tinsider.ui.dialogs.AlertHelper.showErrorAlert("No Packages", "There are no packages to delete.");
                return;
            }
            ChoiceDialog<SnippetPackage> dialog = new ChoiceDialog<>(packages.get(0), packages);
            dialog.setTitle("Delete Package");
            dialog.setContentText("Package:");
            com.tinsider.ui.dialogs.AlertHelper.themeDialog(dialog);
            Optional<SnippetPackage> result = dialog.showAndWait();
            result.ifPresent(pkg -> {
                if (pkg != null) {
                    packages.remove(pkg);
                    // orphan snippets in this package
                    for (Snippet s : snippets) {
                        if (pkg.getId().equals(s.getPackageId())) {
                            s.setPackageId(null);
                        }
                    }
                    StorageManager.saveSnippets(new ArrayList<>(snippets));
                    StorageManager.savePackages(new ArrayList<>(packages));
                    rebuildSnippetsGrid();
                }
            });
        });

        snippets.addListener(new javafx.collections.ListChangeListener<Snippet>() {
            @Override
            public void onChanged(javafx.collections.ListChangeListener.Change<? extends Snippet> c) {
                rebuildSnippetsGrid();
            }
        });

        packages.addListener(new javafx.collections.ListChangeListener<SnippetPackage>() {
            @Override
            public void onChanged(javafx.collections.ListChangeListener.Change<? extends SnippetPackage> c) {
                rebuildSnippetsGrid();
            }
        });

        snippetsDashboard.getChildren().addAll(snippetsTitle, snippetsSearchField, snippetsGridScrollPane, snippetButtons);

        // --- Terminals Dashboard ---
        terminalsDashboard = new VBox(15);
        terminalsDashboard.setPadding(new Insets(25));
        terminalsDashboard.getStyleClass().add("dashboard-view");

        Label terminalsTitle = new Label("Active Sessions");
        terminalsTitle.getStyleClass().add("dashboard-title");
        terminalsTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        noActiveSessionsLabel = new Label("No active terminals. Click a host to connect.");
        noActiveSessionsLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 14px;");

        terminalsGrid = new FlowPane(15, 15);
        terminalsGrid.setPadding(new Insets(10));
        terminalsGridScrollPane = new ScrollPane(terminalsGrid);
        terminalsGridScrollPane.setFitToWidth(true);
        terminalsGridScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        terminalsGridScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        terminalsGridScrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(terminalsGridScrollPane, Priority.ALWAYS);

        terminalsDashboard.getChildren().addAll(terminalsTitle, noActiveSessionsLabel, terminalsGridScrollPane);

        // --- Highlights Dashboard ---
        highlightsDashboard = new VBox(15);
        highlightsDashboard.setPadding(new Insets(25));
        highlightsDashboard.getStyleClass().add("dashboard-view");

        Label highlightsTitle = new Label("Word Highlights Management");
        highlightsTitle.getStyleClass().add("dashboard-title");
        highlightsTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        highlightsSearchField = new TextField();
        highlightsSearchField.setPromptText("🔍 Search highlights by word or packages by name...");
        highlightsSearchField.getStyleClass().add("text-field");
        highlightsSearchField.textProperty().addListener((obs, oldVal, newVal) -> rebuildHighlightsGrid());

        highlightsDashboardContainer = new VBox(20);
        highlightsDashboardContainer.setPadding(new Insets(10));
        highlightsGridScrollPane = new ScrollPane(highlightsDashboardContainer);
        highlightsGridScrollPane.setFitToWidth(true);
        highlightsGridScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        highlightsGridScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        highlightsGridScrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(highlightsGridScrollPane, Priority.ALWAYS);

        HBox highlightButtons = new HBox(10);
        Button addHighlightBtn = new Button("Add Highlight");
        Label addHighlightIcon = new Label("\uEA60");
        addHighlightIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 12px;");
        addHighlightIcon.getStyleClass().add("button-icon");
        addHighlightBtn.setGraphic(addHighlightIcon);

        Button editHighlightBtn = new Button("Edit");
        Label editHighlightIcon = new Label("\uEA73");
        editHighlightIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 12px;");
        editHighlightIcon.getStyleClass().add("button-icon");
        editHighlightBtn.setGraphic(editHighlightIcon);

        Button deleteHighlightBtn = new Button("Delete");
        Label deleteHighlightIcon = new Label("\uEA81");
        deleteHighlightIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 12px;");
        deleteHighlightIcon.getStyleClass().add("button-icon");
        deleteHighlightBtn.setGraphic(deleteHighlightIcon);

        Button addHighlightPackageBtn = new Button("Add Package");
        Label addHighlightPackageIcon = new Label("\uEA60");
        addHighlightPackageIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 12px;");
        addHighlightPackageIcon.getStyleClass().add("button-icon");
        addHighlightPackageBtn.setGraphic(addHighlightPackageIcon);

        Button deleteHighlightPackageBtn = new Button("Delete Package");
        Label deleteHighlightPackageIcon = new Label("\uEA81");
        deleteHighlightPackageIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 12px;");
        deleteHighlightPackageIcon.getStyleClass().add("button-icon");
        deleteHighlightPackageBtn.setGraphic(deleteHighlightPackageIcon);

        highlightButtons.getChildren().addAll(addHighlightBtn, editHighlightBtn, deleteHighlightBtn, addHighlightPackageBtn, deleteHighlightPackageBtn);
        highlightButtons.setAlignment(Pos.CENTER_LEFT);

        editHighlightBtn.disableProperty().bind(selectedHighlight.isNull());
        deleteHighlightBtn.disableProperty().bind(selectedHighlight.isNull());

        addHighlightBtn.setOnAction(e -> showAddHighlightDialog(highlights, null));
        editHighlightBtn.setOnAction(e -> {
            WordHighlight selected = selectedHighlight.get();
            if (selected != null) showAddHighlightDialog(highlights, selected);
        });
        deleteHighlightBtn.setOnAction(e -> {
            WordHighlight selected = selectedHighlight.get();
            if (selected != null) {
                highlights.remove(selected);
                StorageManager.saveHighlights(new ArrayList<>(highlights));
                selectedHighlight.set(null);
            }
        });

        addHighlightPackageBtn.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Add Highlight Package");
            dialog.setContentText("Package Name:");
            com.tinsider.ui.dialogs.AlertHelper.themeDialog(dialog);
            Optional<String> result = dialog.showAndWait();
            result.ifPresent(name -> {
                String trimmedName = name.trim();
                if (!trimmedName.isEmpty()) {
                    boolean duplicate = false;
                    for (HighlightPackage pkg : highlightPackages) {
                        if (pkg.getName() != null && pkg.getName().trim().equalsIgnoreCase(trimmedName)) {
                            duplicate = true;
                            break;
                        }
                    }
                    if (duplicate) {
                        com.tinsider.ui.dialogs.AlertHelper.showErrorAlert("Duplicate Package", "A highlight package with this name already exists.");
                        return;
                    }
                    HighlightPackage pkg = new HighlightPackage(trimmedName);
                    highlightPackages.add(pkg);
                    StorageManager.saveHighlightPackages(new ArrayList<>(highlightPackages));
                }
            });
        });

        deleteHighlightPackageBtn.setOnAction(e -> {
            if (highlightPackages.isEmpty()) {
                com.tinsider.ui.dialogs.AlertHelper.showErrorAlert("No Packages", "There are no packages to delete.");
                return;
            }
            ChoiceDialog<HighlightPackage> dialog = new ChoiceDialog<>(highlightPackages.get(0), highlightPackages);
            dialog.setTitle("Delete Highlight Package");
            dialog.setContentText("Package:");
            com.tinsider.ui.dialogs.AlertHelper.themeDialog(dialog);
            Optional<HighlightPackage> result = dialog.showAndWait();
            result.ifPresent(pkg -> {
                if (pkg != null) {
                    highlightPackages.remove(pkg);
                    // orphan highlights in this package
                    for (WordHighlight wh : highlights) {
                        if (pkg.getId().equals(wh.getPackageId())) {
                            wh.setPackageId(null);
                        }
                    }
                    StorageManager.saveHighlights(new ArrayList<>(highlights));
                    StorageManager.saveHighlightPackages(new ArrayList<>(highlightPackages));
                }
            });
        });

        highlightsDashboard.getChildren().addAll(highlightsTitle, highlightsSearchField, highlightsGridScrollPane, highlightButtons);

        highlights.addListener(new javafx.collections.ListChangeListener<WordHighlight>() {
            @Override
            public void onChanged(javafx.collections.ListChangeListener.Change<? extends WordHighlight> c) {
                rebuildHighlightsGrid();
            }
        });

        highlightPackages.addListener(new javafx.collections.ListChangeListener<HighlightPackage>() {
            @Override
            public void onChanged(javafx.collections.ListChangeListener.Change<? extends HighlightPackage> c) {
                rebuildHighlightsGrid();
            }
        });

        // Rebuild sidebar highlights automatically when data changes
        highlights.addListener((javafx.collections.ListChangeListener<WordHighlight>) change -> {
            rebuildSidebarHighlights();
        });
        highlightPackages.addListener((javafx.collections.ListChangeListener<HighlightPackage>) change -> {
            rebuildSidebarHighlights();
        });

        // Add to stack
        dashboardCenterStack.getChildren().addAll(connectionsDashboard, hostsDashboard, snippetsDashboard, terminalsDashboard, highlightsDashboard);

        // Initial Grid Renderings
        rebuildHostsGrid();
        rebuildSnippetsGrid();
        rebuildSessionsGrid();
        rebuildHighlightsGrid();

        // 4. Build Terminal Mode - Snippet Execution Sidebar (Left)
        snippetExecutionSidebar = new VBox(15);
        snippetExecutionSidebar.setPadding(new Insets(20, 10, 20, 10));
        snippetExecutionSidebar.setPrefWidth(240);
        snippetExecutionSidebar.getStyleClass().add("sidebar");

        // Segmented Sidebar Navigation Tabs
        HBox sidebarNav = new HBox(5);
        sidebarNav.setAlignment(Pos.CENTER);
        
        ToggleButton sidebarSnippetsBtn = new ToggleButton("Snippets");
        ToggleButton sidebarHighlightsBtn = new ToggleButton("Highlights");
        ToggleGroup sidebarGroup = new ToggleGroup();
        sidebarSnippetsBtn.setToggleGroup(sidebarGroup);
        sidebarHighlightsBtn.setToggleGroup(sidebarGroup);
        sidebarSnippetsBtn.setSelected(true); // default selected

        sidebarSnippetsBtn.setMaxWidth(Double.MAX_VALUE);
        sidebarHighlightsBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(sidebarSnippetsBtn, Priority.ALWAYS);
        HBox.setHgrow(sidebarHighlightsBtn, Priority.ALWAYS);

        sidebarSnippetsBtn.getStyleClass().add("sidebar-tab-button");
        sidebarHighlightsBtn.getStyleClass().add("sidebar-tab-button");
        sidebarNav.getChildren().addAll(sidebarSnippetsBtn, sidebarHighlightsBtn);

        // -- Snippets Content Container --
        VBox snippetsSidebarContent = new VBox(10);
        VBox.setVgrow(snippetsSidebarContent, Priority.ALWAYS);

        sidebarSearchField = new TextField();
        sidebarSearchField.setPromptText("🔍 Search snippets or packages...");
        sidebarSearchField.getStyleClass().add("text-field");
        sidebarSearchField.textProperty().addListener((obs, oldVal, newVal) -> rebuildTerminalSnippetsSidebar());

        terminalSnippetsContainer = new VBox(10);
        terminalSnippetsContainer.setStyle("-fx-background-color: transparent;");
        terminalSnippetsContainer.setPadding(new Insets(0, 5, 0, 0));

        ScrollPane sidebarScrollPane = new ScrollPane(terminalSnippetsContainer);
        sidebarScrollPane.setFitToWidth(true);
        sidebarScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sidebarScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sidebarScrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(sidebarScrollPane, Priority.ALWAYS);

        snippets.addListener((javafx.collections.ListChangeListener<Snippet>) change -> {
            rebuildTerminalSnippetsSidebar();
        });
        packages.addListener((javafx.collections.ListChangeListener<SnippetPackage>) change -> {
            rebuildTerminalSnippetsSidebar();
        });

        rebuildTerminalSnippetsSidebar();

        Button addSnippetSidebarBtn = new Button("Add Snippet");
        Label addIcon = new Label("\uEA60");
        addIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 12px;");
        addIcon.getStyleClass().add("button-icon");
        addSnippetSidebarBtn.setGraphic(addIcon);
        addSnippetSidebarBtn.getStyleClass().add("button");
        addSnippetSidebarBtn.setMaxWidth(Double.MAX_VALUE); // Full-width
        addSnippetSidebarBtn.setOnAction(e -> showAddSnippetDialog(snippets, null));

        snippetsSidebarContent.getChildren().addAll(sidebarSearchField, addSnippetSidebarBtn, sidebarScrollPane);

        // -- Highlights Content Container --
        VBox highlightsSidebarContent = new VBox(10);
        VBox.setVgrow(highlightsSidebarContent, Priority.ALWAYS);

        sidebarHighlightsSearchField = new TextField();
        sidebarHighlightsSearchField.setPromptText("🔍 Search highlights or packages...");
        sidebarHighlightsSearchField.getStyleClass().add("text-field");
        sidebarHighlightsSearchField.textProperty().addListener((obs, oldVal, newVal) -> rebuildSidebarHighlights());

        terminalHighlightsContainer = new VBox(8);
        terminalHighlightsContainer.setStyle("-fx-background-color: transparent;");
        terminalHighlightsContainer.setPadding(new Insets(5, 5, 5, 0));

        ScrollPane highlightsScrollPane = new ScrollPane(terminalHighlightsContainer);
        highlightsScrollPane.setFitToWidth(true);
        highlightsScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        highlightsScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        highlightsScrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(highlightsScrollPane, Priority.ALWAYS);

        highlightsSidebarContent.getChildren().addAll(sidebarHighlightsSearchField, highlightsScrollPane);

        // Initial Visibility
        snippetsSidebarContent.setVisible(true);
        snippetsSidebarContent.setManaged(true);
        highlightsSidebarContent.setVisible(false);
        highlightsSidebarContent.setManaged(false);

        // Nav Handlers
        sidebarSnippetsBtn.setOnAction(e -> {
            sidebarSnippetsBtn.setSelected(true); // keep selected
            snippetsSidebarContent.setVisible(true);
            snippetsSidebarContent.setManaged(true);
            highlightsSidebarContent.setVisible(false);
            highlightsSidebarContent.setManaged(false);
            com.tinsider.util.ActivityLogger.logUserAction("SIDEBAR_SWITCH_TAB", "User switched sidebar tab to Snippets");
        });

        sidebarHighlightsBtn.setOnAction(e -> {
            sidebarHighlightsBtn.setSelected(true); // keep selected
            snippetsSidebarContent.setVisible(false);
            snippetsSidebarContent.setManaged(false);
            highlightsSidebarContent.setVisible(true);
            highlightsSidebarContent.setManaged(true);
            com.tinsider.util.ActivityLogger.logUserAction("SIDEBAR_SWITCH_TAB", "User switched sidebar tab to Highlights");
            rebuildSidebarHighlights(); // force refresh
        });

        snippetExecutionSidebar.getChildren().addAll(sidebarNav, snippetsSidebarContent, highlightsSidebarContent);

        // 5. Root Layout & Theme Control
        topToolBar = new TopToolBar(
            savedShell,
            hosts,
            shell -> {
                Map<String, String> innerSettings = StorageManager.loadSettings();
                innerSettings.put("shell", shell);
                StorageManager.saveSettings(innerSettings);
            },
            () -> {
                com.tinsider.terminal.TerminalTabFactory.increaseZoom(tabSplitPaneMap.values());
                topToolBar.updateZoomLabel(com.tinsider.terminal.TerminalTabFactory.getGlobalFontSize());
            },
            () -> {
                com.tinsider.terminal.TerminalTabFactory.decreaseZoom(tabSplitPaneMap.values());
                topToolBar.updateZoomLabel(com.tinsider.terminal.TerminalTabFactory.getGlobalFontSize());
            },
            () -> {
                com.tinsider.terminal.TerminalTabFactory.resetZoom(tabSplitPaneMap.values());
                topToolBar.updateZoomLabel(com.tinsider.terminal.TerminalTabFactory.getGlobalFontSize());
            },
            () -> {
                toggleSidebar();
            },
            () -> {
                switchMode(AppMode.DASHBOARD);
            },
            () -> {
                switchMode(AppMode.TERMINAL);
            },
            () -> {
                Map<String, String> currentSettings = StorageManager.loadSettings();
                boolean initialGradients = Boolean.parseBoolean(currentSettings.getOrDefault("gradientEnabled", "false"));
                com.tinsider.ui.dialogs.SettingsDialog dialog = new com.tinsider.ui.dialogs.SettingsDialog(
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
                dialog.showAndWait();
            }
        );
        topToolBar.setShellSelectionVisible(false);

        root = new BorderPane();
        
        // Initial state
        switchMode(AppMode.DASHBOARD);
        switchDashboardTab(DashboardTab.CONNECTIONS);

        // Pack top panel controls
        VBox topContainer = new VBox();
        topContainer.getChildren().addAll(topToolBar);
        root.setTop(topContainer);

        // Listener to keep Active Sessions view synchronized
        workspace.getTabPane().getTabs().addListener((javafx.collections.ListChangeListener<Tab>) change -> {
            updateActiveSessionsList();
            repaintAllTerminals();
        });

        Scene scene = new Scene(root, 1000, 700);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());

        // Add zoom accelerators (shortcuts)
        scene.getAccelerators().put(new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.EQUALS, javafx.scene.input.KeyCombination.SHORTCUT_DOWN), () -> {
            com.tinsider.terminal.TerminalTabFactory.increaseZoom(tabSplitPaneMap.values());
            topToolBar.updateZoomLabel(com.tinsider.terminal.TerminalTabFactory.getGlobalFontSize());
        });
        scene.getAccelerators().put(new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.MINUS, javafx.scene.input.KeyCombination.SHORTCUT_DOWN), () -> {
            com.tinsider.terminal.TerminalTabFactory.decreaseZoom(tabSplitPaneMap.values());
            topToolBar.updateZoomLabel(com.tinsider.terminal.TerminalTabFactory.getGlobalFontSize());
        });
        scene.getAccelerators().put(new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.DIGIT0, javafx.scene.input.KeyCombination.SHORTCUT_DOWN), () -> {
            com.tinsider.terminal.TerminalTabFactory.resetZoom(tabSplitPaneMap.values());
            topToolBar.updateZoomLabel(com.tinsider.terminal.TerminalTabFactory.getGlobalFontSize());
        });
        scene.getAccelerators().put(new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.ADD, javafx.scene.input.KeyCombination.SHORTCUT_DOWN), () -> {
            com.tinsider.terminal.TerminalTabFactory.increaseZoom(tabSplitPaneMap.values());
            topToolBar.updateZoomLabel(com.tinsider.terminal.TerminalTabFactory.getGlobalFontSize());
        });
        scene.getAccelerators().put(new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.SUBTRACT, javafx.scene.input.KeyCombination.SHORTCUT_DOWN), () -> {
            com.tinsider.terminal.TerminalTabFactory.decreaseZoom(tabSplitPaneMap.values());
            topToolBar.updateZoomLabel(com.tinsider.terminal.TerminalTabFactory.getGlobalFontSize());
        });
        scene.getAccelerators().put(new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.NUMPAD0, javafx.scene.input.KeyCombination.SHORTCUT_DOWN), () -> {
            com.tinsider.terminal.TerminalTabFactory.resetZoom(tabSplitPaneMap.values());
            topToolBar.updateZoomLabel(com.tinsider.terminal.TerminalTabFactory.getGlobalFontSize());
        });

        primaryStage.setScene(scene);

        // Listen for new windows/dialogs (like custom color dialog) and auto-apply the theme and JNA titlebar styling
        javafx.stage.Window.getWindows().addListener((javafx.collections.ListChangeListener<javafx.stage.Window>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    for (javafx.stage.Window win : change.getAddedSubList()) {
                        styleNewWindow(win);
                    }
                }
            }
        });

        // Start Live Reloader for themes and properties files
        Runnable themeReloader = () -> {
            Platform.runLater(() -> {
                System.out.println("[LiveReloader] Theme or config files changed! Reloading UI colors and styles...");
                com.tinsider.config.ThemeManager.reloadConfig();
                String currentThemeName = currentTheme != null ? currentTheme.getName() : "";
                com.tinsider.config.TerminalTheme newTheme = null;
                File watchDir = new File("./themes");
                File[] themeFiles = watchDir.listFiles();
                if (themeFiles != null) {
                    for (File file : themeFiles) {
                        if (file.getName().endsWith(".terminal")) {
                            com.tinsider.config.TerminalTheme t = com.tinsider.config.ThemeManager.loadThemeFromFile(file);
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
        com.tinsider.util.LiveReloader.startWatching("./themes", themeReloader);
        com.tinsider.util.LiveReloader.startWatching("./config", themeReloader);

        primaryStage.show();
        applyTheme(currentTheme);

        primaryStage.setOnCloseRequest(e -> {
            com.tinsider.util.ActivityLogger.logSystemEvent("APP_SHUTDOWN", "T-Insider application shutting down.");
            for (Tab tab : new ArrayList<>(workspace.getTabPane().getTabs())) {
                closeTab(tab);
            }
            Platform.exit();
            System.exit(0);
        });
    }

    private void toggleSidebar() {
        sidebarVisible = !sidebarVisible;
        if (sidebarVisible) {
            root.setLeft(snippetExecutionSidebar);
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

    private void switchMode(AppMode mode) {
        com.tinsider.util.ActivityLogger.logUserAction("SWITCH_APP_MODE", "User switched application mode to: " + mode);
        this.currentMode = mode;
        if (mode == AppMode.DASHBOARD) {
            root.setLeft(slimNavRail);
            root.setCenter(dashboardCenterStack);
            updateActiveSessionsList();
            if (topToolBar != null) {
                topToolBar.setShellSelectionVisible(false);
                topToolBar.setSidebarToggleVisible(false);
                topToolBar.setZoomVisible(false);
                topToolBar.setTerminalButtonVisible(!tabSplitPaneMap.isEmpty());
            }
        } else {
            if (sidebarVisible) {
                root.setLeft(snippetExecutionSidebar);
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

    private void switchDashboardTab(DashboardTab tab) {
        com.tinsider.util.ActivityLogger.logUserAction("SWITCH_DASHBOARD_TAB", "User switched dashboard tab to: " + tab);
        this.currentDashboardTab = tab;
        connectionsDashboard.setVisible(false);
        hostsDashboard.setVisible(false);
        snippetsDashboard.setVisible(false);
        terminalsDashboard.setVisible(false);
        highlightsDashboard.setVisible(false);

        if (tab == DashboardTab.CONNECTIONS) {
            connectionsDashboard.setVisible(true);
            connectionsDashboard.toFront();
            rebuildConnectionsGrid();
        } else if (tab == DashboardTab.HOSTS) {
            hostsDashboard.setVisible(true);
            hostsDashboard.toFront();
        } else if (tab == DashboardTab.SNIPPETS) {
            snippetsDashboard.setVisible(true);
            snippetsDashboard.toFront();
        } else if (tab == DashboardTab.TERMINALS) {
            terminalsDashboard.setVisible(true);
            terminalsDashboard.toFront();
            updateActiveSessionsList();
        } else if (tab == DashboardTab.HIGHLIGHTS) {
            highlightsDashboard.setVisible(true);
            highlightsDashboard.toFront();
            rebuildHighlightsGrid();
        }
    }

    private void updateActiveSessionsList() {
        Platform.runLater(() -> {
            rebuildSessionsGrid();
        });
    }

    private void showAddHostDialog(ObservableList<HostProfile> hosts, HostProfile existing) {
        HostDialog dialog = new HostDialog(existing);
        Optional<HostProfile> result = dialog.showAndWait();
        result.ifPresent(profile -> {
            if (existing != null) {
                com.tinsider.util.ActivityLogger.logUserAction("EDIT_HOST", "User edited host profile: " + profile.getName() + " (" + profile.getIp() + ")");
                hosts.remove(existing);
            } else {
                com.tinsider.util.ActivityLogger.logUserAction("ADD_HOST", "User added host profile: " + profile.getName() + " (" + profile.getIp() + ")");
            }
            hosts.add(profile);
            StorageManager.saveHosts(new ArrayList<>(hosts));
        });
    }

    private void showAddSnippetDialog(ObservableList<Snippet> snippets, Snippet existing) {
        SnippetDialog dialog = new SnippetDialog(existing, packages);
        Optional<Snippet> result = dialog.showAndWait();
        result.ifPresent(snippet -> {
            if (existing != null) {
                com.tinsider.util.ActivityLogger.logUserAction("EDIT_SNIPPET", "User edited snippet: " + snippet.getTitle());
                snippets.remove(existing);
            } else {
                com.tinsider.util.ActivityLogger.logUserAction("ADD_SNIPPET", "User added snippet: " + snippet.getTitle());
            }
            snippets.add(snippet);
            StorageManager.saveSnippets(new ArrayList<>(snippets));
        });
    }

    private void openLocalTerminal(String type) {
        try {
            com.tinsider.util.ActivityLogger.logUserAction("OPEN_LOCAL_TERMINAL", "User requested open local terminal: " + type);
            TtyConnector connector = ConnectionManager.openLocalTerminal(type);
            String tabTitle = ConnectionManager.truncate(type, 16);
            TerminalTabFactory.createTerminalTab(
                    tabTitle, 
                    connector, 
                    workspace.getTabPane(), 
                    tabSplitPaneMap, 
                    () -> currentTheme, 
                    this::closeTab,
                    "LOCAL",
                    type,
                    null
            );
        } catch (Exception e) {
            e.printStackTrace();
            AlertHelper.showErrorAlert("Error opening local terminal", e.getMessage());
        }
    }

    private void connectToHost(HostProfile host) {
        com.tinsider.util.ActivityLogger.logUserAction("CONNECT_TO_HOST_ATTEMPT", "User requested connection to host: " + host.getName() + " (" + host.getIp() + ")");
        ConnectionManager.connectToHost(host, workspace.getTabPane(), (title, connector) -> {
            TerminalTabFactory.createTerminalTab(
                    title, 
                    connector, 
                    workspace.getTabPane(), 
                    tabSplitPaneMap, 
                    () -> currentTheme, 
                    this::closeTab,
                    "SSH",
                    null,
                    host
            );
        });
    }

    private void closeTab(Tab tab) {
        com.tinsider.util.ActivityLogger.logUserAction("CLOSE_TAB", "User manually closed terminal tab [" + tab.getText() + "]");
        com.tinsider.ui.components.terminal.split.TerminalSplitPane splitPane = tabSplitPaneMap.remove(tab);
        if (splitPane != null) {
            splitPane.close();
        }
        Platform.runLater(() -> {
            if (workspace.getTabPane().getTabs().contains(tab)) {
                workspace.getTabPane().getTabs().remove(tab);
            }
            if (workspace.getTabPane().getTabs().size() <= 1) {
                switchMode(AppMode.DASHBOARD);
            }
        });
    }

    private void executeSnippet(Snippet snippet) {
        Tab activeTab = workspace.getTabPane().getSelectionModel().getSelectedItem();
        if (activeTab == null) {
            AlertHelper.showErrorAlert("No active terminal", "Please open a terminal tab before running a snippet.");
            return;
        }

        com.tinsider.ui.components.terminal.split.TerminalSplitPane splitPane = tabSplitPaneMap.get(activeTab);
        JediTermWidget widget = (splitPane != null) ? splitPane.getActiveWidget() : null;
        if (widget == null) {
            AlertHelper.showErrorAlert("No active terminal", "The current tab is not a terminal session.");
            return;
        }

        com.tinsider.util.ActivityLogger.logUserAction("EXECUTE_SNIPPET", "User executed snippet [" + snippet.getTitle() + "] on active terminal in tab [" + activeTab.getText() + "]");

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
                    if (autoEnterSnippet) {
                        code += "\r";
                    }
                    connector.write(code);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void runSnippet(Snippet snippet, boolean autoEnter) {
        Tab activeTab = workspace.getTabPane().getSelectionModel().getSelectedItem();
        if (activeTab == null) {
            AlertHelper.showErrorAlert("No active terminal", "Please open a terminal tab before running a snippet.");
            return;
        }

        com.tinsider.ui.components.terminal.split.TerminalSplitPane splitPane = tabSplitPaneMap.get(activeTab);
        JediTermWidget widget = (splitPane != null) ? splitPane.getActiveWidget() : null;
        if (widget == null) {
            AlertHelper.showErrorAlert("No active terminal", "The current tab is not a terminal session.");
            return;
        }

        com.tinsider.util.ActivityLogger.logUserAction("EXECUTE_SNIPPET_DIRECT", "User executed snippet [" + snippet.getTitle() + "] (autoEnter=" + autoEnter + ") on active terminal in tab [" + activeTab.getText() + "]");

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

    public void repaintAllTerminals() {
        if (currentTheme == null) return;
        
        com.jediterm.core.Color termBg = currentTheme.getBackgroundColor();
        com.jediterm.core.Color termFg = currentTheme.getTextColor();
        
        // Repaint all active terminal panels using the specific resolved terminal colors!
        for (com.tinsider.ui.components.terminal.split.TerminalSplitPane splitPane : tabSplitPaneMap.values()) {
            Platform.runLater(() -> splitPane.updateActiveTheme(currentTheme));
            for (JediTermWidget widget : splitPane.getAllWidgets()) {
                SwingUtilities.invokeLater(() -> {
                    // Add 1ms delay to fix scrollbar race condition when adding new tabs
                    javax.swing.Timer timer = new javax.swing.Timer(1, e -> {
                        try {
                            java.awt.Color awtBg = new java.awt.Color(termBg.getRed(), termBg.getGreen(), termBg.getBlue());
                            java.awt.Color awtFg = new java.awt.Color(termFg.getRed(), termFg.getGreen(), termFg.getBlue());

                            widget.setBackground(awtBg);
                            widget.setForeground(awtFg);
                            if (widget.getTerminalPanel() != null) {
                                widget.getTerminalPanel().setBackground(awtBg);
                                widget.getTerminalPanel().setForeground(awtFg);
                            }

                            if (widget.getTerminal() != null && widget.getTerminal().getStyleState() != null) {
                                widget.getTerminal().getStyleState().setDefaultStyle(com.jediterm.terminal.TextStyle.EMPTY);
                                widget.getTerminal().getStyleState().reset();
                            }

                            if (widget.getTerminalPanel() != null) {
                                widget.getTerminalPanel().repaint();
                            }
                            widget.invalidate();
                            widget.repaint();
                            widget.revalidate();
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    });
                    timer.setRepeats(false);
                    timer.start();
                });
            }
        }
    }

    private void applyTheme(TerminalTheme theme) {
        this.currentTheme = theme;
        boolean gradientsEnabled = Boolean.parseBoolean(StorageManager.loadSettings().getOrDefault("gradientEnabled", "false"));

        double appWinBgOpacity = 1.0;
        double appWinBorderOpacity = 1.0;
        double titleBgOpacity = 1.0;
        double titleBorderOpacity = 1.0;
        double toolbarBgOpacity = 1.0;
        double toolbarBorderOpacity = 1.0;
        double sidebarBgOpacity = 1.0;
        double sidebarBorderOpacity = 1.0;

        // Resolve each granular color from the theme map with standard robust fallbacks
        com.jediterm.core.Color winBg = theme.getColor("app.window.background", theme.getBackgroundColor());
        com.jediterm.core.Color winBorder = theme.getColor("app.window.border", theme.getSelectionColor());
        com.jediterm.core.Color titleBg = theme.getColor("titlebar.background", theme.getBackgroundColor());
        com.jediterm.core.Color titleBorder = theme.getColor("titlebar.border", theme.getSelectionColor());
        com.jediterm.core.Color toolBg = theme.getColor("toolbar.background", theme.getBackgroundColor());
        com.jediterm.core.Color toolBorder = theme.getColor("toolbar.border", theme.getSelectionColor());
        com.jediterm.core.Color sideBg = theme.getColor("sidebar.background", theme.getBackgroundColor());
        com.jediterm.core.Color sideBorder = theme.getColor("sidebar.border", theme.getSelectionColor());
        com.jediterm.core.Color labelFg = theme.getColor("global.label.foreground", theme.getTextColor());

        com.jediterm.core.Color termBg = theme.getColor("terminal.background", theme.getBackgroundColor());
        com.jediterm.core.Color termFg = theme.getColor("terminal.foreground", theme.getTextColor());
        com.jediterm.core.Color termCursor = theme.getColor("terminal.cursor", theme.getCursorColor());
        com.jediterm.core.Color termSelection = theme.getColor("terminal.selection", theme.getSelectionColor());

        com.jediterm.core.Color headerBg = theme.getColor("tabs.header.background", theme.getBackgroundColor());
        com.jediterm.core.Color tabInactiveBg = theme.getColor("tabs.tab.inactive.background", theme.getBackgroundColor());
        com.jediterm.core.Color tabInactiveFg = theme.getColor("tabs.tab.inactive.foreground", theme.getTextColor());
        com.jediterm.core.Color tabActiveBg = theme.getColor("tabs.tab.active.background", theme.getBackgroundColor());
        com.jediterm.core.Color tabActiveFg = theme.getColor("tabs.tab.active.foreground", theme.getCursorColor());
        com.jediterm.core.Color tabActiveBorder = theme.getColor("tabs.tab.active.border", theme.getCursorColor());
        com.jediterm.core.Color tabHoverBg = theme.getColor("tabs.tab.hover.background", theme.getSelectionColor());
        com.jediterm.core.Color plusFg = theme.getColor("tabs.plus.foreground", theme.getTextColor());
        com.jediterm.core.Color plusHoverBg = theme.getColor("tabs.plus.hover.background", theme.getSelectionColor());
        com.jediterm.core.Color plusHoverFg = theme.getColor("tabs.plus.hover.foreground", theme.getCursorColor());

        com.jediterm.core.Color cardsListBg = theme.getColor("cards.list.background", theme.getBackgroundColor());
        com.jediterm.core.Color cardsListBorder = theme.getColor("cards.list.border", theme.getSelectionColor());
        com.jediterm.core.Color cardsItemBg = theme.getColor("cards.item.background", theme.getSelectionColor());
        com.jediterm.core.Color cardsItemBorder = theme.getColor("cards.item.border", theme.getBackgroundColor());
        com.jediterm.core.Color cardsItemTitle = theme.getColor("cards.item.title", theme.getTextColor());
        com.jediterm.core.Color cardsItemSub = theme.getColor("cards.item.subtext", theme.getTextColor());
        com.jediterm.core.Color cardsBadgeBg = theme.getColor("cards.item.badge.background", theme.getCursorColor());
        com.jediterm.core.Color cardsBadgeFg = theme.getColor("cards.item.badge.foreground", theme.getTextColor());
        com.jediterm.core.Color cardsBadgeHoverBg = theme.getColor("cards.item.badge.hover.background", theme.getSelectionColor());
        com.jediterm.core.Color cardsBadgeHoverFg = theme.getColor("cards.item.badge.hover.foreground", theme.getCursorColor());
        com.jediterm.core.Color cardsBadgeSelectedBg = theme.getColor("cards.item.badge.selected.background", theme.getBackgroundColor());
        com.jediterm.core.Color cardsBadgeSelectedFg = theme.getColor("cards.item.badge.selected.foreground", theme.getCursorColor());
        com.jediterm.core.Color cardsHoverBg = theme.getColor("cards.item.hover.background", theme.getSelectionColor());
        com.jediterm.core.Color cardsHoverBorder = theme.getColor("cards.item.hover.border", theme.getSelectionColor());
        com.jediterm.core.Color cardsSelectedBg = theme.getColor("cards.item.selected.background", theme.getSelectionColor());
        com.jediterm.core.Color cardsSelectedBorder = theme.getColor("cards.item.selected.border", theme.getCursorColor());
        com.jediterm.core.Color cardsSelectedGlow = theme.getColor("cards.item.selected.glow", theme.getCursorColor());

        com.jediterm.core.Color btnBg = theme.getColor("controls.button.background", theme.getSelectionColor());
        com.jediterm.core.Color btnBorder = theme.getColor("controls.button.border", theme.getBackgroundColor());
        com.jediterm.core.Color btnFg = theme.getColor("controls.button.foreground", theme.getTextColor());
        com.jediterm.core.Color btnHoverBg = theme.getColor("controls.button.hover.background", theme.getSelectionColor());
        com.jediterm.core.Color btnHoverBorder = theme.getColor("controls.button.hover.border", theme.getCursorColor());
        com.jediterm.core.Color btnHoverFg = theme.getColor("controls.button.hover.foreground", theme.getBackgroundColor());
        com.jediterm.core.Color btnIcon = theme.getColor("controls.button.icon", theme.getTextColor());
        com.jediterm.core.Color btnHoverIcon = theme.getColor("controls.button.hover.icon", theme.getBackgroundColor());
        com.jediterm.core.Color comboBg = theme.getColor("controls.combo.background", theme.getSelectionColor());
        com.jediterm.core.Color comboBorder = theme.getColor("controls.combo.border", theme.getBackgroundColor());
        com.jediterm.core.Color comboFg = theme.getColor("controls.combo.foreground", theme.getTextColor());
        com.jediterm.core.Color colorPickerBg = theme.getColor("controls.colorpicker.background", theme.getBackgroundColor());
        com.jediterm.core.Color colorPickerBorder = theme.getColor("controls.colorpicker.border", theme.getSelectionColor());
        com.jediterm.core.Color colorPickerFg = theme.getColor("controls.colorpicker.foreground", theme.getTextColor());
        com.jediterm.core.Color colorPickerPopupBg = theme.getColor("controls.colorpicker.popup.background", theme.getBackgroundColor());
        com.jediterm.core.Color colorPickerPopupBorder = theme.getColor("controls.colorpicker.popup.border", theme.getSelectionColor());
        com.jediterm.core.Color colorPickerCustomBg = theme.getColor("controls.colorpicker.custom.background", theme.getBackgroundColor());
        com.jediterm.core.Color colorPickerCustomControlsBg = theme.getColor("controls.colorpicker.custom.controls.background", theme.getSelectionColor());
        com.jediterm.core.Color colorPickerCustomBtnBg = theme.getColor("controls.colorpicker.custom.button.background", theme.getSelectionColor());
        com.jediterm.core.Color colorPickerCustomBtnBorder = theme.getColor("controls.colorpicker.custom.button.border", theme.getBackgroundColor());
        com.jediterm.core.Color colorPickerCustomBtnFg = theme.getColor("controls.colorpicker.custom.button.foreground", theme.getTextColor());
        com.jediterm.core.Color colorPickerCustomBtnHoverBg = theme.getColor("controls.colorpicker.custom.button.hover.background", theme.getSelectionColor());
        com.jediterm.core.Color colorPickerCustomBtnHoverBorder = theme.getColor("controls.colorpicker.custom.button.hover.border", theme.getCursorColor());
        com.jediterm.core.Color colorPickerCustomBtnHoverFg = theme.getColor("controls.colorpicker.custom.button.hover.foreground", theme.getCursorColor());
        com.jediterm.core.Color colorPickerCustomToggleBg = theme.getColor("controls.colorpicker.custom.toggle.background", theme.getBackgroundColor());
        com.jediterm.core.Color colorPickerCustomToggleBorder = theme.getColor("controls.colorpicker.custom.toggle.border", theme.getSelectionColor());
        com.jediterm.core.Color colorPickerCustomToggleFg = theme.getColor("controls.colorpicker.custom.toggle.foreground", theme.getTextColor());
        com.jediterm.core.Color colorPickerCustomToggleSelectedBg = theme.getColor("controls.colorpicker.custom.toggle.selected.background", theme.getCursorColor());
        com.jediterm.core.Color colorPickerCustomToggleSelectedBorder = theme.getColor("controls.colorpicker.custom.toggle.selected.border", theme.getCursorColor());
        com.jediterm.core.Color colorPickerCustomToggleSelectedFg = theme.getColor("controls.colorpicker.custom.toggle.selected.foreground", theme.getBackgroundColor());
        com.jediterm.core.Color inputBg = theme.getColor("controls.input.background", theme.getSelectionColor());
        com.jediterm.core.Color inputBorder = theme.getColor("controls.input.border", theme.getBackgroundColor());
        com.jediterm.core.Color inputFg = theme.getColor("controls.input.foreground", theme.getTextColor());

        com.jediterm.core.Color zoomBg = theme.getColor("controls.zoom.background", theme.getBackgroundColor());
        com.jediterm.core.Color zoomBorder = theme.getColor("controls.zoom.border", theme.getSelectionColor());
        com.jediterm.core.Color zoomFg = theme.getColor("controls.zoom.foreground", theme.getTextColor());
        com.jediterm.core.Color zoomHoverBg = theme.getColor("controls.zoom.hover.background", theme.getSelectionColor());
        com.jediterm.core.Color zoomHoverBorder = theme.getColor("controls.zoom.hover.border", theme.getCursorColor());
        com.jediterm.core.Color zoomHoverFg = theme.getColor("controls.zoom.hover.foreground", theme.getTextColor());
        com.jediterm.core.Color zoomActiveBg = theme.getColor("controls.zoom.active.background", theme.getCursorColor());
        com.jediterm.core.Color zoomActiveBorder = theme.getColor("controls.zoom.active.border", theme.getCursorColor());
        com.jediterm.core.Color zoomActiveFg = theme.getColor("controls.zoom.active.foreground", theme.getBackgroundColor());

        com.jediterm.core.Color toggleBg = theme.getColor("controls.sidebar.toggle.background", theme.getBackgroundColor());
        com.jediterm.core.Color toggleBorder = theme.getColor("controls.sidebar.toggle.border", theme.getSelectionColor());
        com.jediterm.core.Color toggleFg = theme.getColor("controls.sidebar.toggle.foreground", theme.getTextColor());
        com.jediterm.core.Color toggleHoverBg = theme.getColor("controls.sidebar.toggle.hover.background", theme.getSelectionColor());
        com.jediterm.core.Color toggleHoverBorder = theme.getColor("controls.sidebar.toggle.hover.border", theme.getCursorColor());
        com.jediterm.core.Color toggleHoverFg = theme.getColor("controls.sidebar.toggle.hover.foreground", theme.getTextColor());
        com.jediterm.core.Color toggleActiveBg = theme.getColor("controls.sidebar.toggle.active.background", theme.getCursorColor());
        com.jediterm.core.Color toggleActiveBorder = theme.getColor("controls.sidebar.toggle.active.border", theme.getCursorColor());
        com.jediterm.core.Color toggleActiveFg = theme.getColor("controls.sidebar.toggle.active.foreground", theme.getBackgroundColor());

        com.jediterm.core.Color homeBg = theme.getColor("controls.home.background", theme.getBackgroundColor());
        com.jediterm.core.Color homeBorder = theme.getColor("controls.home.border", theme.getSelectionColor());
        com.jediterm.core.Color homeFg = theme.getColor("controls.home.foreground", theme.getTextColor());
        com.jediterm.core.Color homeHoverBg = theme.getColor("controls.home.hover.background", theme.getSelectionColor());
        com.jediterm.core.Color homeHoverBorder = theme.getColor("controls.home.hover.border", theme.getCursorColor());
        com.jediterm.core.Color homeHoverFg = theme.getColor("controls.home.hover.foreground", theme.getTextColor());
        com.jediterm.core.Color homeActiveBg = theme.getColor("controls.home.active.background", theme.getCursorColor());
        com.jediterm.core.Color homeActiveBorder = theme.getColor("controls.home.active.border", theme.getCursorColor());
        com.jediterm.core.Color homeActiveFg = theme.getColor("controls.home.active.foreground", theme.getBackgroundColor());

        com.jediterm.core.Color settingsBg = theme.getColor("controls.settings.background", theme.getBackgroundColor());
        com.jediterm.core.Color settingsBorder = theme.getColor("controls.settings.border", theme.getSelectionColor());
        com.jediterm.core.Color settingsFg = theme.getColor("controls.settings.foreground", theme.getTextColor());
        com.jediterm.core.Color settingsHoverBg = theme.getColor("controls.settings.hover.background", theme.getSelectionColor());
        com.jediterm.core.Color settingsHoverBorder = theme.getColor("controls.settings.hover.border", theme.getCursorColor());
        com.jediterm.core.Color settingsHoverFg = theme.getColor("controls.settings.hover.foreground", theme.getTextColor());
        com.jediterm.core.Color settingsActiveBg = theme.getColor("controls.settings.active.background", theme.getCursorColor());
        com.jediterm.core.Color settingsActiveBorder = theme.getColor("controls.settings.active.border", theme.getCursorColor());
        com.jediterm.core.Color settingsActiveFg = theme.getColor("controls.settings.active.foreground", theme.getBackgroundColor());

        com.jediterm.core.Color snippetHoverBtnBg = theme.getColor("controls.snippet.hover.btn.background", theme.getBackgroundColor());
        com.jediterm.core.Color snippetHoverBtnBorder = theme.getColor("controls.snippet.hover.btn.border", theme.getSelectionColor());
        com.jediterm.core.Color snippetHoverBtnFg = theme.getColor("controls.snippet.hover.btn.foreground", theme.getTextColor());
        com.jediterm.core.Color snippetHoverBtnHoverBg = theme.getColor("controls.snippet.hover.btn.hover.background", theme.getSelectionColor());
        com.jediterm.core.Color snippetHoverBtnHoverBorder = theme.getColor("controls.snippet.hover.btn.hover.border", theme.getCursorColor());
        com.jediterm.core.Color snippetHoverBtnHoverFg = theme.getColor("controls.snippet.hover.btn.hover.foreground", theme.getCursorColor());

        com.jediterm.core.Color connBg = theme.getColor("connecting.background", theme.getBackgroundColor());
        com.jediterm.core.Color connIcon = theme.getColor("connecting.icon", theme.getCursorColor());
        com.jediterm.core.Color connFg = theme.getColor("connecting.foreground", theme.getTextColor());
        com.jediterm.core.Color splitPaneBg = theme.getColor("terminal.splitpane.background", theme.getBackgroundColor());
        com.jediterm.core.Color splitDividerBg = theme.getColor("terminal.splitpane.divider.background", theme.getSelectionColor());
        com.jediterm.core.Color splitDividerHover = theme.getColor("terminal.splitpane.divider.hover", theme.getCursorColor());

        com.jediterm.core.Color searchBg = theme.getColor("search.bar.background", theme.getSelectionColor());
        com.jediterm.core.Color searchBorder = theme.getColor("search.bar.border", theme.getBackgroundColor());
        com.jediterm.core.Color searchFg = theme.getColor("search.bar.foreground", theme.getTextColor());
        com.jediterm.core.Color searchHoverBg = theme.getColor("search.bar.hover.background", theme.getSelectionColor());
        com.jediterm.core.Color searchActiveBorder = theme.getColor("search.bar.active.border", theme.getCursorColor());
        com.jediterm.core.Color searchActiveBg = theme.getColor("search.bar.active.background", theme.getSelectionColor());

        com.jediterm.core.Color dropListBg = theme.getColor("dropdown.list.background", theme.getBackgroundColor());
        com.jediterm.core.Color dropListBorder = theme.getColor("dropdown.list.border", theme.getSelectionColor());
        com.jediterm.core.Color dropItemBg = theme.getColor("dropdown.item.background", theme.getBackgroundColor());
        com.jediterm.core.Color dropItemFg = theme.getColor("dropdown.item.foreground", theme.getTextColor());
        com.jediterm.core.Color dropHoverBg = theme.getColor("dropdown.item.hover.background", theme.getSelectionColor());
        com.jediterm.core.Color dropHoverFg = theme.getColor("dropdown.item.hover.foreground", theme.getCursorColor());

        com.jediterm.core.Color titleBtnFg = theme.getColor("titlebar.button.foreground", theme.getBackgroundColor());
        com.jediterm.core.Color titleBtnHoverBg = theme.getColor("titlebar.button.hover.background", theme.getSelectionColor());
        com.jediterm.core.Color titleBtnHoverFg = theme.getColor("titlebar.button.hover.foreground", theme.getCursorColor());
        com.jediterm.core.Color titleCloseHoverBg = theme.getColor("titlebar.close.hover.background", new com.jediterm.core.Color(255, 85, 85));
        com.jediterm.core.Color titleCloseHoverFg = theme.getColor("titlebar.close.hover.foreground", new com.jediterm.core.Color(255, 255, 255));

        com.jediterm.core.Color sideNavFg = theme.getColor("sidebar.nav.foreground", theme.getTextColor());
        com.jediterm.core.Color sideNavHoverBg = theme.getColor("sidebar.nav.hover.background", theme.getSelectionColor());
        com.jediterm.core.Color sideNavHoverFg = theme.getColor("sidebar.nav.hover.foreground", theme.getCursorColor());
        com.jediterm.core.Color sideNavSelectedBg = theme.getColor("sidebar.nav.selected.background", theme.getSelectionColor());
        com.jediterm.core.Color sideNavSelectedBorder = theme.getColor("sidebar.nav.selected.border", theme.getCursorColor());

        com.jediterm.core.Color snippetLauncherLabelColor = theme.getColor("snippetLauncher.label", theme.getCursorColor());

        com.jediterm.core.Color pkgBg = theme.getColor("dashboard.package.background", theme.getSelectionColor());
        com.jediterm.core.Color pkgBorder = theme.getColor("dashboard.package.border", theme.getSelectionColor());
        com.jediterm.core.Color sidebarPkgContentBg = theme.getColor("sidebar.package.content.background", theme.getBackgroundColor());
        com.jediterm.core.Color scrollThumb = theme.getColor("scrollbar.thumb", theme.getCursorColor());
        com.jediterm.core.Color scrollThumbHover = theme.getColor("scrollbar.thumb.hover", theme.getCursorColor());
        com.jediterm.core.Color dropScrollThumb = theme.getColor("dropdown.scrollbar.thumb", theme.getSelectionColor());
        com.jediterm.core.Color dropScrollThumbHover = theme.getColor("dropdown.scrollbar.thumb.hover", theme.getCursorColor());
        com.jediterm.core.Color dialogBg = theme.getColor("dialog.background", theme.getSelectionColor());
        com.jediterm.core.Color dialogBorder = theme.getColor("dialog.border", theme.getSelectionColor());
        com.jediterm.core.Color dialogHeaderBg = theme.getColor("dialog.header.background", theme.getBackgroundColor());
        com.jediterm.core.Color dialogHeaderFg = theme.getColor("dialog.header.foreground", theme.getCursorColor());

        // Highlights Theming Keys
        com.jediterm.core.Color hlSideBg = theme.getColor("highlights.sidebar.toggle.background", theme.getBackgroundColor());
        com.jediterm.core.Color hlSideActiveBg = theme.getColor("highlights.sidebar.toggle.active.background", theme.getSelectionColor());
        com.jediterm.core.Color hlSideActiveBorder = theme.getColor("highlights.sidebar.toggle.active.border", theme.getSelectionColor());
        com.jediterm.core.Color hlSideFg = theme.getColor("highlights.sidebar.toggle.foreground", theme.getTextColor());
        com.jediterm.core.Color hlSideActiveFg = theme.getColor("highlights.sidebar.toggle.active.foreground", theme.getCursorColor());
        com.jediterm.core.Color hlSideEyeInactive = theme.getColor("highlights.sidebar.toggle.eye.inactive", theme.getTextColor());
        com.jediterm.core.Color hlSideEyeActive = theme.getColor("highlights.sidebar.toggle.eye.active", theme.getCursorColor());
        com.jediterm.core.Color hlSideHoverBg = theme.getColor("highlights.sidebar.toggle.hover.background", theme.getSelectionColor());
        com.jediterm.core.Color hlSideHoverBorder = theme.getColor("highlights.sidebar.toggle.hover.border", theme.getCursorColor());

        // Highlight Package Sidebar configurations
        com.jediterm.core.Color pkgBgActive = theme.getColor("sidebar.package.background.active", theme.getSelectionColor());
        com.jediterm.core.Color pkgBgInactive = theme.getColor("sidebar.package.background.inactive", theme.getBackgroundColor());
        com.jediterm.core.Color pkgIconColor = theme.getColor("highlights.sidebar.package.icon", theme.getCursorColor());
        com.jediterm.core.Color pkgIconHoverColor = theme.getColor("highlights.sidebar.package.icon.hover", theme.getCursorColor());
        com.jediterm.core.Color pkgIconActiveColor = theme.getColor("highlights.sidebar.package.icon.active", theme.getCursorColor());
        com.jediterm.core.Color pkgTitleColor = theme.getColor("highlights.sidebar.package.title", theme.getTextColor());
        com.jediterm.core.Color pkgTitleHoverColor = theme.getColor("highlights.sidebar.package.title.hover", theme.getTextColor());
        com.jediterm.core.Color pkgTitleActiveColor = theme.getColor("highlights.sidebar.package.title.active", theme.getTextColor());
        com.jediterm.core.Color pkgEyeActiveColor = theme.getColor("highlights.sidebar.package.eye.active", theme.getCursorColor());
        com.jediterm.core.Color pkgEyeInactiveColor = theme.getColor("highlights.sidebar.package.eye.inactive", theme.getSelectionColor());

        String hexPkgIcon = toHex(pkgIconColor);
        String hexPkgIconHover = toHex(pkgIconHoverColor);
        String hexPkgIconActive = toHex(pkgIconActiveColor);
        String hexPkgTitle = toHex(pkgTitleColor);
        String hexPkgTitleHover = toHex(pkgTitleHoverColor);
        String hexPkgTitleActive = toHex(pkgTitleActiveColor);
        String hexPkgEyeActive = toHex(pkgEyeActiveColor);
        String hexPkgEyeInactive = toHex(pkgEyeInactiveColor);

        String hexHlSideFg = toHex(hlSideFg);
        String hexHlSideActiveFg = toHex(hlSideActiveFg);
        String hexHlSideEyeInactive = toHex(hlSideEyeInactive);
        String hexHlSideEyeActive = toHex(hlSideEyeActive);

        // Format to hex/rgba/gradient strings for CSS
        String hexWinBg;
        if (gradientsEnabled) {
            com.jediterm.core.Color winBgStart = theme.getColor("app.window.background.gradient.start", winBg);
            com.jediterm.core.Color winBgEnd = theme.getColor("app.window.background.gradient.end", theme.getBackgroundColor());
            String winBgRatio = ThemeManager.getProperty("app.window.background.gradient.ratio", "0% 100%");
            hexWinBg = getGradientStyle(winBgStart, appWinBgOpacity, winBgEnd, appWinBgOpacity, "to bottom", winBgRatio);
        } else {
            hexWinBg = toRgba(winBg, appWinBgOpacity);
        }

        String hexWinBorder = toRgba(winBorder, appWinBorderOpacity);

        String hexTitleBg = toRgba(titleBg, titleBgOpacity);
        String hexTitleBorder = toRgba(titleBorder, titleBorderOpacity);

        String hexToolBg = toRgba(toolBg, toolbarBgOpacity);
        String hexToolBorder = toRgba(toolBorder, toolbarBorderOpacity);

        String hexSideBg;
        if (gradientsEnabled) {
            com.jediterm.core.Color sideBgStart = theme.getColor("sidebar.background.gradient.start", sideBg);
            com.jediterm.core.Color sideBgEnd = theme.getColor("sidebar.background.gradient.end", theme.getBackgroundColor());
            String sideBgRatio = ThemeManager.getProperty("sidebar.background.gradient.ratio", "0% 100%");
            hexSideBg = getGradientStyle(sideBgStart, sidebarBgOpacity, sideBgEnd, sidebarBgOpacity, "to bottom", sideBgRatio);
        } else {
            hexSideBg = toRgba(sideBg, sidebarBgOpacity);
        }

        String hexSideBorder = toRgba(sideBorder, sidebarBorderOpacity);
        String hexLabelFg = toHex(labelFg);

        String hexTermBg = toHex(termBg);
        String hexTermFg = toHex(termFg);
        String hexTermCursor = toHex(termCursor);
        String hexTermSelection = toHex(termSelection);

        String hexHeaderBg = toHex(headerBg);
        String hexTabInactiveBg = toHex(tabInactiveBg);
        String hexTabInactiveFg = toHex(tabInactiveFg);
        String hexTabActiveBg = toHex(tabActiveBg);
        String hexTabActiveFg = toHex(tabActiveFg);
        String hexTabActiveBorder = toHex(tabActiveBorder);
        String hexTabHoverBg = toHex(tabHoverBg);
        String hexPlusFg = toHex(plusFg);
        String hexPlusHoverBg = toHex(plusHoverBg);
        String hexPlusHoverFg = toHex(plusHoverFg);

        String hexCardsListBg = toHex(cardsListBg);
        String hexCardsListBorder = toHex(cardsListBorder);
        String hexCardsItemTitle = toHex(cardsItemTitle);
        String hexCardsItemSub = toHex(cardsItemSub);

        String hexSnippetLauncherLabelColor = toHex(snippetLauncherLabelColor);

        // Parse customizable card opacities
        double itemBgOpacity = 1.0;
        double itemHoverBgOpacity = 1.0;
        double itemSelectedBgOpacity = 1.0;
        double itemBorderOpacity = 1.0;
        double itemHoverBorderOpacity = 1.0;
        double itemSelectedBorderOpacity = 1.0;
        double cardsBadgeOpacity = 0.15;
        double cardsBadgeHoverOpacity = 0.15;
        double cardsBadgeSelectedOpacity = 0.15;
        double sideNavSelectedBgOpacity = 1.0;
        double pkgBgOpacity = 0.3;
        double pkgBorderOpacity = 1.0;
        double sidebarPkgContentBgOpacity = 1.0;
        double scrollThumbOpacity = 0.3;
        double scrollThumbHoverOpacity = 0.6;
        double dropScrollThumbOpacity = 0.4;
        double dropScrollThumbHoverOpacity = 0.7;
        double dialogBgOpacity = 1.0;
        double dialogBorderOpacity = 1.0;
        double dialogHeaderBgOpacity = 0.5;
        double searchBarBgOpacity = 1.0;
        double zoomBgOpacity = 1.0;
        double zoomBorderOpacity = 1.0;
        double zoomHoverBgOpacity = 1.0;
        double zoomHoverBorderOpacity = 1.0;
        double zoomActiveBgOpacity = 1.0;
        double zoomActiveBorderOpacity = 1.0;

        double toggleBgOpacity = 1.0;
        double toggleBorderOpacity = 1.0;
        double toggleHoverBgOpacity = 1.0;
        double toggleHoverBorderOpacity = 1.0;
        double toggleActiveBgOpacity = 1.0;
        double toggleActiveBorderOpacity = 1.0;

        double homeBgOpacity = 1.0;
        double homeBorderOpacity = 1.0;
        double homeHoverBgOpacity = 1.0;
        double homeHoverBorderOpacity = 1.0;
        double homeActiveBgOpacity = 1.0;
        double homeActiveBorderOpacity = 1.0;

        double settingsBgOpacity = 1.0;
        double settingsBorderOpacity = 1.0;
        double settingsHoverBgOpacity = 1.0;
        double settingsHoverBorderOpacity = 1.0;
        double settingsActiveBgOpacity = 1.0;
        double settingsActiveBorderOpacity = 1.0;

        double snippetHoverBtnBgOpacity = 1.0;
        double snippetHoverBtnBorderOpacity = 1.0;
        double snippetHoverBtnHoverBgOpacity = 1.0;
        double snippetHoverBtnHoverBorderOpacity = 1.0;

        double connItemBgOpacity = 1.0;
        double connItemBorderOpacity = 1.0;
        double connBadgeBgOpacity = 0.15;
        double connHoverBgOpacity = 1.0;
        double connHoverBorderOpacity = 1.0;
        double connSelectedBgOpacity = 1.0;
        double connSelectedBorderOpacity = 1.0;

        double btnBgOpacity = 1.0;
        double btnBorderOpacity = 1.0;
        double btnHoverBgOpacity = 1.0;
        double btnHoverBorderOpacity = 1.0;

        // Highlights custom opacity variables
        double hlSideBgOpacity = 1.0;
        double hlSideBorderOpacity = 1.0;
        double hlSideHoverBgOpacity = 1.0;
        double hlSideHoverBorderOpacity = 1.0;
        double hlSideActiveBgOpacity = 1.0;
        double hlSideActiveBorderOpacity = 1.0;
        double pkgBgActiveOpacity = 0.15;
        double pkgBgInactiveOpacity = 0.30;


        try {
            itemBgOpacity = Double.parseDouble(ThemeManager.getProperty("cards.item.background.opacity", "1.0"));
            itemHoverBgOpacity = Double.parseDouble(ThemeManager.getProperty("cards.item.hover.background.opacity", "1.0"));
            itemSelectedBgOpacity = Double.parseDouble(ThemeManager.getProperty("cards.item.selected.background.opacity", "1.0"));
            itemBorderOpacity = Double.parseDouble(ThemeManager.getProperty("cards.item.border.opacity", "1.0"));
            itemHoverBorderOpacity = Double.parseDouble(ThemeManager.getProperty("cards.item.hover.border.opacity", "1.0"));
            itemSelectedBorderOpacity = Double.parseDouble(ThemeManager.getProperty("cards.item.selected.border.opacity", "1.0"));
            cardsBadgeOpacity = Double.parseDouble(ThemeManager.getProperty("cards.item.badge.opacity", "0.15"));
            cardsBadgeHoverOpacity = Double.parseDouble(ThemeManager.getProperty("cards.item.badge.hover.opacity", "0.15"));
            cardsBadgeSelectedOpacity = Double.parseDouble(ThemeManager.getProperty("cards.item.badge.selected.opacity", "0.15"));
            sideNavSelectedBgOpacity = Double.parseDouble(ThemeManager.getProperty("sidebar.nav.selected.background.opacity", "1.0"));
            pkgBgOpacity = Double.parseDouble(ThemeManager.getProperty("dashboard.package.background.opacity", "0.3"));
            pkgBorderOpacity = Double.parseDouble(ThemeManager.getProperty("dashboard.package.border.opacity", "1.0"));
            sidebarPkgContentBgOpacity = Double.parseDouble(ThemeManager.getProperty("sidebar.package.content.background.opacity", "1.0"));
            pkgBgActiveOpacity = Double.parseDouble(ThemeManager.getProperty("sidebar.package.background.active.opacity", "0.15"));
            pkgBgInactiveOpacity = Double.parseDouble(ThemeManager.getProperty("sidebar.package.background.inactive.opacity", "0.30"));
            scrollThumbOpacity = Double.parseDouble(ThemeManager.getProperty("scrollbar.thumb.opacity", "0.3"));
            scrollThumbHoverOpacity = Double.parseDouble(ThemeManager.getProperty("scrollbar.thumb.hover.opacity", "0.6"));
            dropScrollThumbOpacity = Double.parseDouble(ThemeManager.getProperty("dropdown.scrollbar.thumb.opacity", "0.4"));
            dropScrollThumbHoverOpacity = Double.parseDouble(ThemeManager.getProperty("dropdown.scrollbar.thumb.hover.opacity", "0.7"));
            dialogBgOpacity = Double.parseDouble(ThemeManager.getProperty("dialog.background.opacity", "1.0"));
            dialogBorderOpacity = Double.parseDouble(ThemeManager.getProperty("dialog.border.opacity", "1.0"));
            dialogHeaderBgOpacity = Double.parseDouble(ThemeManager.getProperty("dialog.header.background.opacity", "0.5"));
            searchBarBgOpacity = Double.parseDouble(ThemeManager.getProperty("search.bar.background.opacity", "1.0"));
            zoomBgOpacity = Double.parseDouble(ThemeManager.getProperty("controls.zoom.background.opacity", "1.0"));
            zoomBorderOpacity = Double.parseDouble(ThemeManager.getProperty("controls.zoom.border.opacity", "1.0"));
            zoomHoverBgOpacity = Double.parseDouble(ThemeManager.getProperty("controls.zoom.hover.background.opacity", "1.0"));
            zoomHoverBorderOpacity = Double.parseDouble(ThemeManager.getProperty("controls.zoom.hover.border.opacity", "1.0"));
            zoomActiveBgOpacity = Double.parseDouble(ThemeManager.getProperty("controls.zoom.active.background.opacity", "1.0"));
            zoomActiveBorderOpacity = Double.parseDouble(ThemeManager.getProperty("controls.zoom.active.border.opacity", "1.0"));

            toggleBgOpacity = Double.parseDouble(ThemeManager.getProperty("controls.sidebar.toggle.background.opacity", "1.0"));
            toggleBorderOpacity = Double.parseDouble(ThemeManager.getProperty("controls.sidebar.toggle.border.opacity", "1.0"));
            toggleHoverBgOpacity = Double.parseDouble(ThemeManager.getProperty("controls.sidebar.toggle.hover.background.opacity", "1.0"));
            toggleHoverBorderOpacity = Double.parseDouble(ThemeManager.getProperty("controls.sidebar.toggle.hover.border.opacity", "1.0"));
            toggleActiveBgOpacity = Double.parseDouble(ThemeManager.getProperty("controls.sidebar.toggle.active.background.opacity", "1.0"));
            toggleActiveBorderOpacity = Double.parseDouble(ThemeManager.getProperty("controls.sidebar.toggle.active.border.opacity", "1.0"));

            homeBgOpacity = Double.parseDouble(ThemeManager.getProperty("controls.home.background.opacity", "1.0"));
            homeBorderOpacity = Double.parseDouble(ThemeManager.getProperty("controls.home.border.opacity", "1.0"));
            homeHoverBgOpacity = Double.parseDouble(ThemeManager.getProperty("controls.home.hover.background.opacity", "1.0"));
            homeHoverBorderOpacity = Double.parseDouble(ThemeManager.getProperty("controls.home.hover.border.opacity", "1.0"));
            homeActiveBgOpacity = Double.parseDouble(ThemeManager.getProperty("controls.home.active.background.opacity", "1.0"));
            homeActiveBorderOpacity = Double.parseDouble(ThemeManager.getProperty("controls.home.active.border.opacity", "1.0"));

            settingsBgOpacity = Double.parseDouble(ThemeManager.getProperty("controls.settings.background.opacity", "1.0"));
            settingsBorderOpacity = Double.parseDouble(ThemeManager.getProperty("controls.settings.border.opacity", "1.0"));
            settingsHoverBgOpacity = Double.parseDouble(ThemeManager.getProperty("controls.settings.hover.background.opacity", "1.0"));
            settingsHoverBorderOpacity = Double.parseDouble(ThemeManager.getProperty("controls.settings.hover.border.opacity", "1.0"));
            settingsActiveBgOpacity = Double.parseDouble(ThemeManager.getProperty("controls.settings.active.background.opacity", "1.0"));
            settingsActiveBorderOpacity = Double.parseDouble(ThemeManager.getProperty("controls.settings.active.border.opacity", "1.0"));

            snippetHoverBtnBgOpacity = Double.parseDouble(ThemeManager.getProperty("controls.snippet.hover.btn.background.opacity", "1.0"));
            snippetHoverBtnBorderOpacity = Double.parseDouble(ThemeManager.getProperty("controls.snippet.hover.btn.border.opacity", "0.3"));
            snippetHoverBtnHoverBgOpacity = Double.parseDouble(ThemeManager.getProperty("controls.snippet.hover.btn.hover.background.opacity", "1.0"));
            snippetHoverBtnHoverBorderOpacity = Double.parseDouble(ThemeManager.getProperty("controls.snippet.hover.btn.hover.border.opacity", "1.0"));

            btnBgOpacity = Double.parseDouble(ThemeManager.getProperty("controls.button.background.opacity", "1.0"));
            btnBorderOpacity = Double.parseDouble(ThemeManager.getProperty("controls.button.border.opacity", "1.0"));
            btnHoverBgOpacity = Double.parseDouble(ThemeManager.getProperty("controls.button.hover.background.opacity", "1.0"));
            btnHoverBorderOpacity = Double.parseDouble(ThemeManager.getProperty("controls.button.hover.border.opacity", "1.0"));

            appWinBgOpacity = Double.parseDouble(ThemeManager.getProperty("app.window.background.opacity", "1.0"));
            appWinBorderOpacity = Double.parseDouble(ThemeManager.getProperty("app.window.border.opacity", "1.0"));
            titleBgOpacity = Double.parseDouble(ThemeManager.getProperty("titlebar.background.opacity", "1.0"));
            titleBorderOpacity = Double.parseDouble(ThemeManager.getProperty("titlebar.border.opacity", "1.0"));
            toolbarBgOpacity = Double.parseDouble(ThemeManager.getProperty("toolbar.background.opacity", "1.0"));
            toolbarBorderOpacity = Double.parseDouble(ThemeManager.getProperty("toolbar.border.opacity", "1.0"));
            sidebarBgOpacity = Double.parseDouble(ThemeManager.getProperty("sidebar.background.opacity", "1.0"));
            sidebarBorderOpacity = Double.parseDouble(ThemeManager.getProperty("sidebar.border.opacity", "1.0"));

            hlSideBgOpacity = Double.parseDouble(ThemeManager.getProperty("highlights.sidebar.toggle.background.opacity", "1.0"));
            hlSideBorderOpacity = Double.parseDouble(ThemeManager.getProperty("highlights.sidebar.toggle.border.opacity", "1.0"));
            hlSideHoverBgOpacity = Double.parseDouble(ThemeManager.getProperty("highlights.sidebar.toggle.hover.background.opacity", "1.0"));
            hlSideHoverBorderOpacity = Double.parseDouble(ThemeManager.getProperty("highlights.sidebar.toggle.hover.border.opacity", "1.0"));
            hlSideActiveBgOpacity = Double.parseDouble(ThemeManager.getProperty("highlights.sidebar.toggle.active.background.opacity", "1.0"));
            hlSideActiveBorderOpacity = Double.parseDouble(ThemeManager.getProperty("highlights.sidebar.toggle.active.border.opacity", "1.0"));
        } catch (Exception ignored) {}

        // Format raw colors to RGBA with parsed opacities
        String rgbaZoomBg = toRgba(zoomBg, zoomBgOpacity);
        String rgbaZoomBorder = toRgba(zoomBorder, zoomBorderOpacity);
        String hexZoomFg = toHex(zoomFg);
        String rgbaZoomHoverBg = toRgba(zoomHoverBg, zoomHoverBgOpacity);
        String rgbaZoomHoverBorder = toRgba(zoomHoverBorder, zoomHoverBorderOpacity);
        String hexZoomHoverFg = toHex(zoomHoverFg);
        String rgbaZoomActiveBg = toRgba(zoomActiveBg, zoomActiveBgOpacity);
        String rgbaZoomActiveBorder = toRgba(zoomActiveBorder, zoomActiveBorderOpacity);
        String hexZoomActiveFg = toHex(zoomActiveFg);

        String rgbaToggleBg = toRgba(toggleBg, toggleBgOpacity);
        String rgbaToggleBorder = toRgba(toggleBorder, toggleBorderOpacity);
        String hexToggleFg = toHex(toggleFg);
        String rgbaToggleHoverBg = toRgba(toggleHoverBg, toggleHoverBgOpacity);
        String rgbaToggleHoverBorder = toRgba(toggleHoverBorder, toggleHoverBorderOpacity);
        String hexToggleHoverFg = toHex(toggleHoverFg);
        String rgbaToggleActiveBg = toRgba(toggleActiveBg, toggleActiveBgOpacity);
        String rgbaToggleActiveBorder = toRgba(toggleActiveBorder, toggleActiveBorderOpacity);
        String hexToggleActiveFg = toHex(toggleActiveFg);

        String rgbaHomeBg = toRgba(homeBg, homeBgOpacity);
        String rgbaHomeBorder = toRgba(homeBorder, homeBorderOpacity);
        String hexHomeFg = toHex(homeFg);
        String rgbaHomeHoverBg = toRgba(homeHoverBg, homeHoverBgOpacity);
        String rgbaHomeHoverBorder = toRgba(homeHoverBorder, homeHoverBorderOpacity);
        String hexHomeHoverFg = toHex(homeHoverFg);
        String rgbaHomeActiveBg = toRgba(homeActiveBg, homeActiveBgOpacity);
        String rgbaHomeActiveBorder = toRgba(homeActiveBorder, homeActiveBorderOpacity);
        String hexHomeActiveFg = toHex(homeActiveFg);

        String rgbaSettingsBg = toRgba(settingsBg, settingsBgOpacity);
        String rgbaSettingsBorder = toRgba(settingsBorder, settingsBorderOpacity);
        String hexSettingsFg = toHex(settingsFg);
        String rgbaSettingsHoverBg = toRgba(settingsHoverBg, settingsHoverBgOpacity);
        String rgbaSettingsHoverBorder = toRgba(settingsHoverBorder, settingsHoverBorderOpacity);
        String hexSettingsHoverFg = toHex(settingsHoverFg);
        String rgbaSettingsActiveBg = toRgba(settingsActiveBg, settingsActiveBgOpacity);
        String rgbaSettingsActiveBorder = toRgba(settingsActiveBorder, settingsActiveBorderOpacity);
        String hexSettingsActiveFg = toHex(settingsActiveFg);

        String rgbaSnippetHoverBtnBg = toRgba(snippetHoverBtnBg, snippetHoverBtnBgOpacity);
        String rgbaSnippetHoverBtnBorder = toRgba(snippetHoverBtnBorder, snippetHoverBtnBorderOpacity);
        String hexSnippetHoverBtnFg = toHex(snippetHoverBtnFg);
        String rgbaSnippetHoverBtnHoverBg = toRgba(snippetHoverBtnHoverBg, snippetHoverBtnHoverBgOpacity);
        String rgbaSnippetHoverBtnHoverBorder = toRgba(snippetHoverBtnHoverBorder, snippetHoverBtnHoverBorderOpacity);
        String hexSnippetHoverBtnHoverFg = toHex(snippetHoverBtnHoverFg);

        String rgbaCardsItemBg = toRgba(cardsItemBg, itemBgOpacity);
        String rgbaCardsItemBorder = toRgba(cardsItemBorder, itemBorderOpacity);
        String rgbaCardsHoverBg = toRgba(cardsHoverBg, itemHoverBgOpacity);
        String rgbaCardsHoverBorder = toRgba(cardsHoverBorder, itemHoverBorderOpacity);
        String rgbaCardsSelectedBg = toRgba(cardsSelectedBg, itemSelectedBgOpacity);
        String rgbaCardsSelectedBorder = toRgba(cardsSelectedBorder, itemSelectedBorderOpacity);
        String rgbaCardsBadgeBg = toRgba(cardsBadgeBg, cardsBadgeOpacity);
        String rgbaCardsBadgeHoverBg = toRgba(cardsBadgeHoverBg, cardsBadgeHoverOpacity);
        String rgbaCardsBadgeSelectedBg = toRgba(cardsBadgeSelectedBg, cardsBadgeSelectedOpacity);
        String rgbaSideNavSelectedBg = toRgba(sideNavSelectedBg, sideNavSelectedBgOpacity);
        String rgbaPkgBg = toRgba(pkgBg, pkgBgOpacity);
        String rgbaPkgBorder = toRgba(pkgBorder, pkgBorderOpacity);
        String rgbaSidebarPkgContentBg = toRgba(sidebarPkgContentBg, sidebarPkgContentBgOpacity);
        String rgbaPkgBgActive = toRgba(pkgBgActive, pkgBgActiveOpacity);
        String rgbaPkgBgInactive = toRgba(pkgBgInactive, pkgBgInactiveOpacity);
        String rgbaScrollThumb = toRgba(scrollThumb, scrollThumbOpacity);
        String rgbaScrollThumbHover = toRgba(scrollThumbHover, scrollThumbHoverOpacity);
        String rgbaDropScrollThumb = toRgba(dropScrollThumb, dropScrollThumbOpacity);
        String rgbaDropScrollThumbHover = toRgba(dropScrollThumbHover, dropScrollThumbHoverOpacity);

        // Word Highlight opacity configurations
        String rgbaHlSideBg = toRgba(hlSideBg, hlSideBgOpacity);
        String rgbaHlSideBorder = toRgba(sideBorder, hlSideBorderOpacity);
        String rgbaHlSideHoverBg = toRgba(hlSideHoverBg, hlSideHoverBgOpacity);
        String rgbaHlSideHoverBorder = toRgba(hlSideHoverBorder, hlSideHoverBorderOpacity);
        String rgbaHlSideActiveBg = toRgba(hlSideActiveBg, hlSideActiveBgOpacity);
        String rgbaHlSideActiveBorder = toRgba(hlSideActiveBorder, hlSideActiveBorderOpacity);
        
        String rgbaDialogBg;
        if (gradientsEnabled) {
            com.jediterm.core.Color dialogBgStart = theme.getColor("dialog.background.gradient.start", dialogBg);
            com.jediterm.core.Color dialogBgEnd = theme.getColor("dialog.background.gradient.end", theme.getBackgroundColor());
            String dialogBgRatio = ThemeManager.getProperty("dialog.background.gradient.ratio", "0% 100%");
            rgbaDialogBg = getGradientStyle(dialogBgStart, dialogBgOpacity, dialogBgEnd, dialogBgOpacity, "to bottom", dialogBgRatio);
        } else {
            rgbaDialogBg = toRgba(dialogBg, dialogBgOpacity);
        }
        
        String rgbaDialogBorder = toRgba(dialogBorder, dialogBorderOpacity);
        String rgbaDialogHeaderBg = toRgba(dialogHeaderBg, dialogHeaderBgOpacity);
        String hexDialogHeaderFg = toHex(dialogHeaderFg);

        String hexCardsBadgeFg = toHex(cardsBadgeFg);
        String hexCardsBadgeHoverFg = toHex(cardsBadgeHoverFg);
        String hexCardsBadgeSelectedFg = toHex(cardsBadgeSelectedFg);
        String rgbaCardsSelectedGlow = toRgba(cardsSelectedGlow, 0.4);

        String rgbaBtnBg = toRgba(btnBg, btnBgOpacity);
        String rgbaBtnBorder = toRgba(btnBorder, btnBorderOpacity);
        String hexBtnFg = toHex(btnFg);
        String rgbaBtnHoverBg = toRgba(btnHoverBg, btnHoverBgOpacity);
        String rgbaBtnHoverBorder = toRgba(btnHoverBorder, btnHoverBorderOpacity);
        String hexBtnHoverFg = toHex(btnHoverFg);
        String hexBtnHoverBg = toHex(btnHoverBg);
        String hexBtnIcon = toHex(btnIcon);
        String hexBtnHoverIcon = toHex(btnHoverIcon);
        String hexComboBg = toHex(comboBg);
        String hexComboBorder = toHex(comboBorder);
        String hexComboFg = toHex(comboFg);
        String hexColorPickerBg = toHex(colorPickerBg);
        String hexColorPickerBorder = toHex(colorPickerBorder);
        String hexColorPickerFg = toHex(colorPickerFg);
        String hexColorPickerPopupBg = toHex(colorPickerPopupBg);
        String hexColorPickerPopupBorder = toHex(colorPickerPopupBorder);
        String hexColorPickerCustomBg = toHex(colorPickerCustomBg);
        String hexColorPickerCustomControlsBg = toHex(colorPickerCustomControlsBg);
        String hexColorPickerCustomBtnBg = toHex(colorPickerCustomBtnBg);
        String hexColorPickerCustomBtnBorder = toHex(colorPickerCustomBtnBorder);
        String hexColorPickerCustomBtnFg = toHex(colorPickerCustomBtnFg);
        String hexColorPickerCustomBtnHoverBg = toHex(colorPickerCustomBtnHoverBg);
        String hexColorPickerCustomBtnHoverBorder = toHex(colorPickerCustomBtnHoverBorder);
        String hexColorPickerCustomBtnHoverFg = toHex(colorPickerCustomBtnHoverFg);
        String hexColorPickerCustomToggleBg = toHex(colorPickerCustomToggleBg);
        String hexColorPickerCustomToggleBorder = toHex(colorPickerCustomToggleBorder);
        String hexColorPickerCustomToggleFg = toHex(colorPickerCustomToggleFg);
        String hexColorPickerCustomToggleSelectedBg = toHex(colorPickerCustomToggleSelectedBg);
        String hexColorPickerCustomToggleSelectedBorder = toHex(colorPickerCustomToggleSelectedBorder);
        String hexColorPickerCustomToggleSelectedFg = toHex(colorPickerCustomToggleSelectedFg);
        String hexInputBg = toHex(inputBg);
        String hexInputBorder = toHex(inputBorder);
        String hexInputFg = toHex(inputFg);

        String hexConnBg = toHex(connBg);
        String hexConnIcon = toHex(connIcon);
        String hexConnFg = toHex(connFg);
        String hexSplitPaneBg = toHex(splitPaneBg);
        String hexSplitDividerBg = toHex(splitDividerBg);
        String hexSplitDividerHover = toHex(splitDividerHover);

        String rgbaSearchBg = toRgba(searchBg, searchBarBgOpacity);
        String hexSearchBorder = toHex(searchBorder);
        String hexSearchFg = toHex(searchFg);
        String hexSearchHoverBg = toHex(searchHoverBg);
        String hexSearchActiveBorder = toHex(searchActiveBorder);
        String hexSearchActiveBg = toHex(searchActiveBg);

        String hexDropListBg = toHex(dropListBg);
        String hexDropListBorder = toHex(dropListBorder);
        String hexDropItemBg = toHex(dropItemBg);
        String hexDropItemFg = toHex(dropItemFg);
        String hexDropHoverBg = toHex(dropHoverBg);
        String hexDropHoverFg = toHex(dropHoverFg);

        String hexTitleBtnFg = toHex(titleBtnFg);
        String hexTitleBtnHoverBg = toHex(titleBtnHoverBg);
        String hexTitleBtnHoverFg = toHex(titleBtnHoverFg);
        String hexTitleCloseHoverBg = toHex(titleCloseHoverBg);
        String hexTitleCloseHoverFg = toHex(titleCloseHoverFg);

        String hexSideNavFg = toHex(sideNavFg);
        String hexSideNavHoverBg = toHex(sideNavHoverBg);
        String hexSideNavHoverFg = toHex(sideNavHoverFg);
        String hexSideNavSelectedBorder = toHex(sideNavSelectedBorder);

        String dynamicCss = ".root { -fx-background-color: " + hexWinBg + "; -fx-border-color: " + hexWinBorder + "; -fx-border-width: 1px; -fx-background-radius: 0px; -fx-border-radius: 0px; }\n" +
                            ".custom-title-bar { -fx-background-color: " + hexTitleBg + "; -fx-border-color: " + hexTitleBorder + "; -fx-border-width: 0 0 1 0; -fx-background-radius: 0px; }\n" +
                            ".button.title-btn { -fx-background-color: transparent; -fx-border-color: transparent; -fx-border-width: 0px; -fx-pref-width: 46px; -fx-pref-height: 36px; -fx-min-width: 46px; -fx-min-height: 36px; -fx-max-width: 46px; -fx-max-height: 36px; -fx-padding: 0; -fx-alignment: center; -fx-text-fill: " + hexTitleBtnFg + "; -fx-focus-color: transparent; -fx-faint-focus-color: transparent; }\n" +
                            ".button.title-btn:hover { -fx-background-color: " + hexTitleBtnHoverBg + "; -fx-text-fill: " + hexTitleBtnHoverFg + "; }\n" +
                            ".button.title-btn-close { -fx-background-color: transparent; -fx-border-color: transparent; -fx-border-width: 0px; -fx-pref-width: 46px; -fx-pref-height: 36px; -fx-min-width: 46px; -fx-min-height: 36px; -fx-max-width: 46px; -fx-max-height: 36px; -fx-padding: 0; -fx-alignment: center; -fx-text-fill: " + hexTitleBtnFg + "; -fx-focus-color: transparent; -fx-faint-focus-color: transparent; }\n" +
                            ".button.title-btn-close:hover { -fx-background-color: " + hexTitleCloseHoverBg + "; -fx-text-fill: " + hexTitleCloseHoverFg + "; }\n" +
                            ".sidebar { -fx-background-color: " + hexSideBg + "; -fx-border-color: " + hexSideBorder + "; }\n" +
                            ".top-bar { -fx-background-color: " + hexToolBg + "; -fx-border-color: " + hexToolBorder + "; -fx-pref-height: 46px; -fx-min-height: 46px; -fx-max-height: 46px; }\n" +
                            ".button { -fx-background-color: " + rgbaBtnBg + "; -fx-border-color: " + rgbaBtnBorder + "; -fx-text-fill: " + hexBtnFg + "; -fx-background-insets: 0; -fx-border-width: 1px; -fx-background-radius: 4px; -fx-border-radius: 4px; }\n" +
                            ".button:hover { -fx-background-color: " + rgbaBtnHoverBg + "; -fx-border-color: " + rgbaBtnHoverBorder + "; -fx-text-fill: " + hexBtnHoverFg + "; }\n" +
                            ".button .button-icon { -fx-text-fill: " + hexBtnIcon + "; }\n" +
                            ".button:hover .button-icon { -fx-text-fill: " + hexBtnHoverIcon + "; }\n" +
                            ".button:disabled { -fx-opacity: 0.4; -fx-cursor: default; }\n" +
                            ".button.zoom-btn { -fx-background-color: " + rgbaZoomBg + "; -fx-border-color: " + rgbaZoomBorder + "; -fx-text-fill: " + hexZoomFg + "; -fx-padding: 3px 8px; -fx-font-size: 11px; -fx-min-width: 24px; -fx-min-height: 24px; -fx-alignment: center; -fx-background-insets: 0; -fx-border-width: 1px; -fx-background-radius: 4px; -fx-border-radius: 4px; }\n" +
                            ".button.zoom-btn:hover { -fx-background-color: " + rgbaZoomHoverBg + "; -fx-border-color: " + rgbaZoomHoverBorder + "; -fx-text-fill: " + hexZoomHoverFg + "; }\n" +
                            ".button.zoom-btn:pressed { -fx-background-color: " + rgbaZoomActiveBg + "; -fx-border-color: " + rgbaZoomActiveBorder + "; -fx-text-fill: " + hexZoomActiveFg + "; }\n" +
                            ".button.zoom-reset-btn { -fx-min-width: 56px; -fx-pref-width: 56px; -fx-max-width: 56px; }\n" +
                            ".button.zoom-icon-btn { -fx-font-family: \"codicon\"; -fx-font-size: 11px; }\n" +
                            ".button.sidebar-toggle-btn { -fx-background-color: " + rgbaToggleBg + "; -fx-border-color: " + rgbaToggleBorder + "; -fx-text-fill: " + hexToggleFg + "; -fx-padding: 3px 8px; -fx-font-family: \"codicon\"; -fx-font-size: 14px; -fx-min-width: 24px; -fx-min-height: 24px; -fx-alignment: center; -fx-background-insets: 0; -fx-border-width: 1px; -fx-background-radius: 4px; -fx-border-radius: 4px; }\n" +
                            ".button.sidebar-toggle-btn:hover { -fx-background-color: " + rgbaToggleHoverBg + "; -fx-border-color: " + rgbaToggleHoverBorder + "; -fx-text-fill: " + hexToggleHoverFg + "; }\n" +
                            ".button.sidebar-toggle-btn:pressed { -fx-background-color: " + rgbaToggleActiveBg + "; -fx-border-color: " + rgbaToggleActiveBorder + "; -fx-text-fill: " + hexToggleActiveFg + "; }\n" +
                            ".button.home-btn { -fx-background-color: " + rgbaHomeBg + "; -fx-border-color: " + rgbaHomeBorder + "; -fx-text-fill: " + hexHomeFg + "; -fx-padding: 3px 8px; -fx-font-family: \"codicon\"; -fx-font-size: 14px; -fx-min-width: 24px; -fx-min-height: 24px; -fx-alignment: center; -fx-background-insets: 0; -fx-border-width: 1px; -fx-background-radius: 4px; -fx-border-radius: 4px; }\n" +
                            ".button.home-btn:hover { -fx-background-color: " + rgbaHomeHoverBg + "; -fx-border-color: " + rgbaHomeHoverBorder + "; -fx-text-fill: " + hexHomeHoverFg + "; }\n" +
                            ".button.home-btn:pressed { -fx-background-color: " + rgbaHomeActiveBg + "; -fx-border-color: " + rgbaHomeActiveBorder + "; -fx-text-fill: " + hexHomeActiveFg + "; }\n" +
                            ".button.settings-btn { -fx-background-color: " + rgbaSettingsBg + "; -fx-border-color: " + rgbaSettingsBorder + "; -fx-text-fill: " + hexSettingsFg + "; -fx-padding: 3px 8px; -fx-font-family: \"codicon\"; -fx-font-size: 14px; -fx-min-width: 24px; -fx-min-height: 24px; -fx-alignment: center; -fx-background-insets: 0; -fx-border-width: 1px; -fx-background-radius: 4px; -fx-border-radius: 4px; }\n" +
                            ".button.settings-btn:hover { -fx-background-color: " + rgbaSettingsHoverBg + "; -fx-border-color: " + rgbaSettingsHoverBorder + "; -fx-text-fill: " + hexSettingsHoverFg + "; }\n" +
                            ".button.settings-btn:pressed { -fx-background-color: " + rgbaSettingsActiveBg + "; -fx-border-color: " + rgbaSettingsActiveBorder + "; -fx-text-fill: " + hexSettingsActiveFg + "; }\n" +
                            ".button.snippet-hover-btn { -fx-background-color: " + rgbaSnippetHoverBtnBg + "; -fx-border-color: " + rgbaSnippetHoverBtnBorder + "; -fx-text-fill: " + hexSnippetHoverBtnFg + "; -fx-padding: 0; -fx-font-family: \"codicon\"; -fx-font-size: 11px; -fx-alignment: center; -fx-background-insets: 0; -fx-border-width: 1px; -fx-background-radius: 3px; -fx-border-radius: 3px; }\n" +
                            ".button.snippet-hover-btn:hover { -fx-background-color: " + rgbaSnippetHoverBtnHoverBg + "; -fx-border-color: " + rgbaSnippetHoverBtnHoverBorder + "; -fx-text-fill: " + hexSnippetHoverBtnHoverFg + "; }\n" +
                            ".button.terminal-btn { -fx-background-color: " + rgbaHomeBg + "; -fx-border-color: " + rgbaHomeBorder + "; -fx-text-fill: " + hexHomeFg + "; -fx-padding: 3px 8px; -fx-font-family: \"codicon\"; -fx-font-size: 14px; -fx-min-width: 24px; -fx-min-height: 24px; -fx-alignment: center; -fx-background-insets: 0; -fx-border-width: 1px; -fx-background-radius: 4px; -fx-border-radius: 4px; }\n" +
                            ".button.terminal-btn:hover { -fx-background-color: " + rgbaHomeHoverBg + "; -fx-border-color: " + rgbaHomeHoverBorder + "; -fx-text-fill: " + hexHomeHoverFg + "; }\n" +
                            ".button.terminal-btn:pressed { -fx-background-color: " + rgbaHomeActiveBg + "; -fx-border-color: " + rgbaHomeActiveBorder + "; -fx-text-fill: " + hexHomeActiveFg + "; }\n" +
                            ".combo-box { -fx-background-color: " + hexComboBg + "; -fx-text-fill: " + hexComboFg + "; -fx-border-color: " + hexComboBorder + "; -fx-background-insets: 0; -fx-background-radius: 4px; -fx-border-radius: 4px; -fx-border-width: 1px; }\n" +
                            ".combo-box .arrow-button { -fx-background-color: transparent; -fx-background-insets: 0; -fx-background-radius: 0; }\n" +
                            ".combo-box .arrow-button .arrow { -fx-background-color: " + hexComboFg + "; -fx-background-insets: 0; }\n" +
                            ".combo-box.shell-dropdown { -fx-pref-width: 140px; -fx-max-width: 140px; }\n" +
                            ".combo-box.theme-dropdown { -fx-border-color: transparent; -fx-border-width: 0px; }\n" +
                            ".combo-box .list-cell { -fx-text-fill: " + hexComboFg + "; }\n" +
                            ".combo-box:hover { -fx-background-color: " + hexBtnHoverBg + "; }\n" +
                            ".combo-box:focused { -fx-border-color: " + hexComboBorder + "; }\n" +
                            ".text-field { -fx-background-color: " + rgbaSearchBg + "; -fx-text-fill: " + hexSearchFg + "; -fx-border-color: " + hexSearchBorder + "; }\n" +
                            ".text-field:hover { -fx-background-color: " + hexSearchHoverBg + "; }\n" +
                            ".text-field:focused { -fx-background-color: " + hexSearchActiveBg + "; -fx-border-color: " + hexSearchActiveBorder + "; }\n" +
                            ".scroll-pane { -fx-background-color: transparent; -fx-background-insets: 0; -fx-padding: 0; -fx-border-color: transparent; }\n" +
                            ".scroll-pane .viewport { -fx-background-color: transparent; }\n" +
                            ".list-view { -fx-background-color: " + hexCardsListBg + "; -fx-control-inner-background: " + hexCardsListBg + "; -fx-border-color: " + hexCardsListBorder + "; }\n" +
                            ".list-cell { -fx-background-color: transparent; -fx-padding: 4px 0px; }\n" +
                            ".list-cell:filled:hover .host-card, .list-cell:filled:hover .snippet-card, .host-card:hover, .snippet-card:hover { -fx-background-color: " + rgbaCardsHoverBg + "; -fx-border-color: " + rgbaCardsHoverBorder + "; }\n" +
                            ".list-cell:filled:selected .host-card, .list-cell:filled:selected .snippet-card, .host-card.selected, .snippet-card.selected { -fx-background-color: " + rgbaCardsSelectedBg + "; -fx-border-color: " + rgbaCardsSelectedBorder + "; -fx-effect: dropshadow(three-pass-box, " + rgbaCardsSelectedGlow + ", 8, 0, 0, 0); }\n" +
                            ".session-cell { -fx-text-fill: " + hexCardsItemTitle + "; -fx-padding: 8px; -fx-font-size: 13px; -fx-background-color: " + rgbaCardsItemBg + "; -fx-border-color: " + rgbaCardsItemBorder + "; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-background-insets: 0; -fx-border-width: 1px; }\n" +
                            ".session-cell:hover { -fx-background-color: " + rgbaCardsHoverBg + "; -fx-border-color: " + rgbaCardsHoverBorder + "; -fx-border-radius: 6px; -fx-background-radius: 6px; }\n" +
                            ".session-cell:selected, .session-cell.selected { -fx-background-color: " + rgbaCardsSelectedBg + "; -fx-border-color: " + rgbaCardsSelectedBorder + "; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-effect: dropshadow(three-pass-box, " + rgbaCardsSelectedGlow + ", 8, 0, 0, 0); }\n" +
                            ".combo-box-popup .list-view { -fx-background-color: " + hexDropListBg + "; -fx-control-inner-background: " + hexDropListBg + "; -fx-border-color: " + hexDropListBorder + "; }\n" +
                            ".combo-box-popup .list-view .list-cell { -fx-text-fill: " + hexDropItemFg + "; -fx-background-color: " + hexDropHoverBg + "; -fx-padding: 6px 12px; }\n" + /* Swapped */
                            ".combo-box-popup .list-view .list-cell:hover { -fx-background-color: " + hexDropItemBg + "; -fx-text-fill: " + hexDropHoverFg + "; }\n" + /* Swapped */
                            ".combo-box-popup .list-view .list-cell:selected { -fx-background-color: " + hexDropItemBg + "; -fx-text-fill: " + hexDropHoverFg + "; }\n" + /* Swapped */
                            ".combo-box-popup .list-view .scroll-bar { -fx-background-color: transparent; }\n" +
                            ".combo-box-popup .list-view .scroll-bar:horizontal { -fx-pref-height: 8px; -fx-max-height: 8px; }\n" +
                            ".combo-box-popup .list-view .scroll-bar:vertical { -fx-pref-width: 13px; -fx-max-width: 13px; -fx-padding: 0 0 0 5px; }\n" +
                            ".combo-box-popup .list-view .scroll-bar .track { -fx-background-color: transparent; -fx-border-color: transparent; -fx-background-insets: 0; }\n" +
                            ".combo-box-popup .list-view .scroll-bar .thumb { -fx-background-color: " + rgbaDropScrollThumb + "; -fx-background-radius: 4px; -fx-border-radius: 4px; -fx-background-insets: 1px; }\n" +
                            ".combo-box-popup .list-view .scroll-bar .thumb:hover { -fx-background-color: " + rgbaDropScrollThumbHover + "; }\n" +
                            ".combo-box-popup .list-view .scroll-bar .increment-button, .combo-box-popup .list-view .scroll-bar .decrement-button { -fx-background-color: transparent; -fx-background-radius: 0; -fx-border-color: transparent; -fx-padding: 0px; }\n" +
                            ".combo-box-popup .list-view .scroll-bar .increment-arrow, .combo-box-popup .list-view .scroll-bar .decrement-arrow { -fx-shape: \"\"; -fx-padding: 0px; -fx-max-width: 0px; -fx-max-height: 0px; }\n" +
                            ".host-card { -fx-background-color: " + rgbaCardsItemBg + "; -fx-border-color: " + rgbaCardsItemBorder + "; }\n" +
                            ".host-badge { -fx-background-color: " + rgbaCardsBadgeBg + "; -fx-text-fill: " + hexCardsBadgeFg + "; }\n" +
                            ".host-card:hover .host-badge { -fx-background-color: " + rgbaCardsBadgeHoverBg + "; -fx-text-fill: " + hexCardsBadgeHoverFg + "; }\n" +
                            ".host-name { -fx-text-fill: " + hexCardsItemTitle + "; }\n" +
                            ".host-username { -fx-text-fill: " + hexCardsItemSub + "; }\n" +
                            ".connection-card { -fx-background-color: " + rgbaCardsItemBg + "; -fx-border-color: " + rgbaCardsItemBorder + "; -fx-background-radius: 6px; -fx-border-radius: 6px; -fx-border-width: 1px; }\n" +
                            ".connection-badge { -fx-background-color: " + rgbaCardsBadgeBg + "; -fx-text-fill: " + hexCardsBadgeFg + "; -fx-background-radius: 16px; }\n" +
                            ".connection-card:hover { -fx-background-color: " + rgbaCardsHoverBg + "; -fx-border-color: " + rgbaCardsHoverBorder + "; }\n" +
                            ".connection-card:hover .connection-badge { -fx-background-color: " + rgbaCardsBadgeHoverBg + "; -fx-text-fill: " + hexCardsBadgeHoverFg + "; }\n" +
                            ".connection-card.selected { -fx-background-color: " + rgbaCardsSelectedBg + "; -fx-border-color: " + rgbaCardsSelectedBorder + "; -fx-effect: dropshadow(three-pass-box, " + rgbaCardsSelectedGlow + ", 8, 0, 0, 0); }\n" +
                            ".connection-card.selected .connection-badge { -fx-background-color: " + rgbaCardsBadgeSelectedBg + "; -fx-text-fill: " + hexCardsBadgeSelectedFg + "; }\n" +
                            ".connection-name { -fx-text-fill: " + hexCardsItemTitle + "; -fx-font-weight: bold; }\n" +
                            ".connection-username { -fx-text-fill: " + hexCardsItemSub + "; }\n" +
                            ".snippet-card { -fx-background-color: " + rgbaCardsItemBg + "; -fx-border-color: " + rgbaCardsItemBorder + "; }\n" +
                            ".snippet-badge { -fx-background-color: " + rgbaCardsBadgeBg + "; -fx-text-fill: " + hexCardsBadgeFg + "; }\n" +
                            ".snippet-card:hover .snippet-badge { -fx-background-color: " + rgbaCardsBadgeHoverBg + "; -fx-text-fill: " + hexCardsBadgeHoverFg + "; }\n" +
                            ".list-cell:filled:hover .host-badge, .list-cell:filled:hover .snippet-badge { -fx-background-color: " + rgbaCardsBadgeHoverBg + "; -fx-text-fill: " + hexCardsBadgeHoverFg + "; }\n" +
                            ".list-cell:filled:selected .host-badge, .list-cell:filled:selected .snippet-badge, .host-card.selected .host-badge, .snippet-card.selected .snippet-badge { -fx-background-color: " + rgbaCardsBadgeSelectedBg + "; -fx-text-fill: " + hexCardsBadgeSelectedFg + "; }\n" +
                            ".snippet-title { -fx-text-fill: " + hexCardsItemTitle + "; }\n" +
                            ".snippet-preview { -fx-text-fill: " + hexCardsItemSub + "; }\n" +
                            ".tab-pane .tab-header-area .tab-header-background { -fx-background-color: " + hexHeaderBg + "; }\n" +
                            ".tab-pane { -fx-tab-min-width: 20px; -fx-tab-max-width: 180px; }\n" +
                            ".tab-pane .tab-content-area { -fx-background-color: transparent; -fx-border-color: transparent; -fx-border-width: 0px; -fx-padding: 0px; }\n" +
                            ".tab-pane .tab-header-area { -fx-padding: 4px 20px 0px 0px; }\n" +
                            ".tab-pane .headers-region { -fx-padding: 0px; }\n" +
                            ".tab { -fx-background-color: " + hexTabInactiveBg + "; -fx-background-insets: 0 1 0 0; -fx-padding: 3px 12px; -fx-background-radius: 4px 4px 0 0; -fx-border-color: transparent; -fx-border-radius: 4px 4px 0 0; -fx-focus-color: transparent; -fx-faint-focus-color: transparent; }\n" +
                            ".tab .tab-label { -fx-text-fill: " + hexTabInactiveFg + "; }\n" +
                            ".tab:focused { -fx-focus-color: transparent; -fx-faint-focus-color: transparent; }\n" +
                            ".tab:selected { -fx-background-color: " + hexTabActiveBg + "; -fx-background-insets: 0 1 -1 0; -fx-border-color: " + hexTabActiveBorder + " transparent transparent transparent; -fx-border-width: 2px 0 0 0; }\n" +
                            ".tab:selected .tab-label { -fx-text-fill: " + hexTabActiveFg + "; }\n" +
                            ".tab:selected .tab-close-button { -fx-background-color: #cbd5e1; }\n" +
                            ".tab .tab-close-button:hover { -fx-background-color: #ff5555; }\n" +
                            ".tab:hover { -fx-background-color: " + hexTabHoverBg + "; }\n" +
                            ".tab-pane .tab.add-tab { -fx-background-color: transparent; -fx-border-color: transparent; -fx-cursor: hand;     -fx-padding:4px 10px; }\n" +
                            ".tab-pane .tab.add-tab:hover { -fx-background-color: " + hexPlusHoverBg + "; }\n" +
                            ".tab-pane .tab.add-tab .tab-label { -fx-text-fill: " + hexPlusFg + "; -fx-font-weight: bold; -fx-font-size: 14px; -fx-padding: 0px; -fx-alignment: center; }\n" +
                            ".tab-pane .tab.add-tab:hover .tab-label { -fx-text-fill: " + hexPlusHoverFg + "; }\n" +
                            ".tab-pane > .tab-header-area > .control-buttons-tab { -fx-padding: 0; -fx-max-width: 0; -fx-min-width: 0; -fx-pref-width: 0; -fx-opacity: 0; visibility: hidden; }\n" +
                            ".tab-pane > .tab-header-area > .control-buttons-tab > .tab-down-button { -fx-padding: 0; visibility: hidden; }\n" +
                            ".slim-nav-rail { -fx-background-color: " + hexSideBg + "; -fx-border-color: " + hexSideBorder + "; -fx-border-width: 0 1 0 0; }\n" +
                            ".nav-button { -fx-background-color: transparent; -fx-text-fill: " + hexSideNavFg + "; -fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 10px 5px; -fx-cursor: hand; -fx-background-radius: 4px; }\n" +
                            ".nav-button:hover { -fx-text-fill: " + hexSideNavHoverFg + "; -fx-background-color: " + hexSideNavHoverBg + "; }\n" +
                            ".nav-button:selected { -fx-text-fill: " + hexSideNavSelectedBorder + "; -fx-background-color: " + rgbaSideNavSelectedBg + "; -fx-border-color: transparent transparent transparent " + hexSideNavSelectedBorder + "; -fx-border-width: 0 0 0 3px; }\n" +
                            ".dashboard-view { -fx-background-color: " + hexWinBg + "; }\n" +
                            ".back-button { -fx-background-color: " + rgbaCardsSelectedBg + "; -fx-border-color: " + hexWinBg + "; -fx-text-fill: " + hexTermCursor + "; -fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 8px; -fx-cursor: hand; -fx-background-radius: 4px; }\n" +
                            ".back-button:hover { -fx-background-color: " + hexBtnHoverBg + "; -fx-border-color: " + hexTermCursor + "; }\n" +
                            ".context-menu { -fx-background-color: " + hexWinBg + "; -fx-border-color: " + hexWinBorder + "; -fx-background-radius: 6px; -fx-border-radius: 6px; -fx-border-width: 1px; -fx-padding: 4px 0px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.4), 8, 0, 0, 4); }\n" +
                            ".menu-item:hover { -fx-background-color: " + hexTabHoverBg + "; }\n" +
                            ".menu-item .label { -fx-text-fill: " + hexPlusFg + "; }\n" +
                            ".menu-item:hover .label { -fx-text-fill: " + hexPlusHoverFg + "; }\n" +
                            ".label.snippets-launcher-header { -fx-text-fill: " + hexSnippetLauncherLabelColor + "; }\n" + 
                            ".app-title { -fx-font-family: \"Impact\", \"Consolas\", monospace; -fx-font-size: 20px; -fx-text-fill: #ffffff; -fx-effect: dropshadow(three-pass-box, " + hexPlusHoverFg + ", 0, 0, 2, 2); }\n" +
                            ".label { -fx-text-fill: " + hexLabelFg + "; }\n" +
                            ".dashboard-package-section { -fx-background-color: " + rgbaPkgBg + "; -fx-border-color: " + rgbaPkgBorder + "; -fx-border-width: 1px; -fx-background-radius: 8px; -fx-border-radius: 8px; -fx-padding: 15px; }\n" +
                            ".package-titled-pane { -fx-text-fill: " + hexCardsItemTitle + "; -fx-font-weight: bold; -fx-font-size: 12px; }\n" +
                            ".package-titled-pane > .title { -fx-background-color: " + rgbaPkgBgInactive + "; -fx-border-color: " + rgbaCardsItemBorder + "; -fx-border-width: 1px; -fx-border-radius: 4px; -fx-background-radius: 4px; -fx-padding: 8px 12px; }\n" +
                            ".package-titled-pane:expanded > .title { -fx-background-color: " + rgbaPkgBgActive + "; }\n" +
                            ".package-titled-pane > .title:hover { -fx-background-color: " + rgbaCardsHoverBg + "; -fx-border-color: " + rgbaCardsHoverBorder + "; }\n" +
                            ".package-folder-icon { -fx-text-fill: " + hexPkgIcon + "; }\n" +
                            ".package-titled-pane > .title:hover .package-folder-icon { -fx-text-fill: " + hexPkgIconHover + "; }\n" +
                            ".package-titled-pane:expanded > .title .package-folder-icon { -fx-text-fill: " + hexPkgIconActive + "; }\n" +
                            ".package-title-label { -fx-text-fill: " + hexPkgTitle + "; }\n" +
                            ".package-titled-pane > .title:hover .package-title-label { -fx-text-fill: " + hexPkgTitleHover + "; }\n" +
                            ".package-titled-pane:expanded > .title .package-title-label { -fx-text-fill: " + hexPkgTitleActive + "; }\n" +
                            ".package-eye-icon { -fx-font-family: 'codicon'; -fx-font-size: 14px; -fx-cursor: hand; }\n" +
                            ".package-eye-icon.active { -fx-text-fill: " + hexPkgEyeActive + "; }\n" +
                            ".package-eye-icon.inactive { -fx-text-fill: " + hexPkgEyeInactive + "; }\n" +
                            ".package-titled-pane > .title > .arrow-button .arrow { -fx-background-color: " + hexTermCursor + "; }\n" +
                            ".package-titled-pane > .content { -fx-background-color: " + rgbaSidebarPkgContentBg + "; -fx-border-color: " + hexSideBorder + "; -fx-border-width: 0 1px 1px 1px; -fx-border-radius: 0 0 4px 4px; -fx-background-radius: 0 0 4px 4px; -fx-padding: 8px; }\n" +
                            ".dashboard-package-title { -fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + hexCardsItemTitle + "; }\n" +
                            ".dashboard-standalone-title { -fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + hexCardsItemTitle + "; }\n" +
                            ".scroll-bar { -fx-background-color: transparent; }\n" +
                            ".scroll-bar:horizontal { -fx-pref-height: 8px; -fx-max-height: 8px; }\n" +
                            ".scroll-bar:vertical { -fx-pref-width: 13px; -fx-max-width: 13px; -fx-padding: 0 0 0 5px; }\n" +
                            ".scroll-bar .track { -fx-background-color: transparent; -fx-border-color: transparent; -fx-background-insets: 0; }\n" +
                            ".scroll-bar .thumb { -fx-background-color: " + rgbaScrollThumb + "; -fx-background-radius: 4px; -fx-border-radius: 4px; -fx-background-insets: 1px; }\n" +
                            ".scroll-bar .thumb:hover { -fx-background-color: " + rgbaScrollThumbHover + "; }\n" +
                            ".scroll-bar .increment-button, .scroll-bar .decrement-button { -fx-background-color: transparent; -fx-background-radius: 0; -fx-border-color: transparent; -fx-padding: 0px; }\n" +
                            ".scroll-bar .increment-arrow, .scroll-bar .decrement-arrow { -fx-shape: \"\"; -fx-padding: 0px; -fx-max-width: 0px; -fx-max-height: 0px; }\n" +
                            ".dialog-pane { -fx-background-color: " + rgbaDialogBg + "; -fx-border-color: " + rgbaDialogBorder + "; -fx-border-width: 1px; -fx-background-radius: 8px; -fx-border-radius: 8px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.6), 20, 0, 0, 5); -fx-background-insets: 12; -fx-border-insets: 12; -fx-padding: 12px; }\n" +
                            ".dialog-pane > .header-panel { -fx-background-color: " + rgbaDialogHeaderBg + "; -fx-padding: 0; -fx-background-radius: 8px 8px 0 0; }\n" +
                            ".dialog-pane .dialog-header-title { -fx-text-fill: " + hexDialogHeaderFg + "; -fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 10px 0 10px 0; }\n" +
                            ".dialog-pane .label { -fx-text-fill: " + hexLabelFg + "; }\n" +
                            ".dialog-pane .button-bar { -fx-padding: 10px 15px; }\n" +
                            ".dialog-pane .text-field, .dialog-pane .text-area, .dialog-pane .password-field, .dialog-pane .combo-box { -fx-background-color: " + hexInputBorder + "; -fx-text-fill: " + hexInputFg + "; -fx-border-color: " + hexInputBorder + "; }\n" +
                            ".dialog-pane .text-area .content, .dialog-pane .text-area:focused .content { -fx-background-color: transparent; -fx-text-fill: " + hexInputFg + "; }\n" +
                            ".dialog-pane .text-field:disabled, .dialog-pane .text-area:disabled, .dialog-pane .password-field:disabled, .dialog-pane .combo-box:disabled { -fx-opacity: 0.5; }\n" +
                            ".dialog-pane .radio-button { -fx-text-fill: " + hexLabelFg + "; }\n" +
                            ".dialog-pane .radio-button .radio { -fx-mark-color: " + hexTermCursor + "; -fx-border-color: transparent; }\n" +
                            ".dialog-pane .check-box { -fx-text-fill: " + hexLabelFg + "; }\n" +
                            ".dialog-pane .check-box .box { -fx-background-color: " + hexInputBorder + "; -fx-border-color: " + hexInputBorder + "; -fx-background-radius: 3px; -fx-border-radius: 3px; }\n" +
                            ".dialog-pane .check-box:focused .box { -fx-border-color: " + hexTermCursor + "; }\n" +
                            ".dialog-pane .check-box:selected .mark { -fx-background-color: " + hexTermCursor + "; }\n" +
                            ".connecting-pane { -fx-background-color: " + hexConnBg + "; }\n" +
                            ".connecting-label { -fx-text-fill: " + hexConnFg + "; -fx-font-size: 13px; }\n" +
                            ".connecting-spinner { -fx-progress-color: " + hexConnIcon + "; }\n" +
                            ".split-pane { -fx-background-color: " + hexSplitPaneBg + "; }\n" +
                            ".terminal-split-pane { -fx-background-color: " + hexSplitPaneBg + "; }\n" +
                            ".split-pane *.split-pane-divider { -fx-background-color: " + hexSplitDividerBg + "; }\n" +
                            ".split-pane *.split-pane-divider:hover { -fx-background-color: " + hexSplitDividerHover + "; }\n" +
                            ".highlight-card { -fx-background-color: " + rgbaCardsItemBg + "; -fx-border-color: " + rgbaCardsItemBorder + "; -fx-border-width: 1px; -fx-border-radius: 6px; -fx-background-radius: 6px; }\n" +
                            ".highlight-card:hover { -fx-background-color: " + rgbaCardsHoverBg + "; -fx-border-color: " + rgbaCardsHoverBorder + "; }\n" +
                            ".highlight-card.selected { -fx-background-color: " + rgbaCardsSelectedBg + "; -fx-border-color: " + rgbaCardsSelectedBorder + "; -fx-effect: dropshadow(three-pass-box, " + rgbaCardsSelectedGlow + ", 8, 0, 0, 0); }\n" +
                            ".highlight-badge { -fx-background-color: " + rgbaCardsBadgeBg + "; -fx-background-radius: 16px; }\n" +
                            ".highlight-sidebar-card { -fx-background-color: " + rgbaHlSideBg + "; -fx-border-color: " + rgbaHlSideBorder + "; -fx-border-width: 1px; -fx-border-radius: 4px; -fx-background-radius: 4px; -fx-padding: 6px 10px; -fx-cursor: hand; }\n" +
                            ".highlight-sidebar-card:hover { -fx-background-color: " + rgbaHlSideHoverBg + "; -fx-border-color: " + rgbaHlSideHoverBorder + "; }\n" +
                            ".highlight-sidebar-card.active { -fx-background-color: " + rgbaHlSideActiveBg + "; -fx-border-color: " + rgbaHlSideActiveBorder + "; }\n" +
                            ".highlight-sidebar-card .label { -fx-text-fill: " + hexHlSideFg + "; }\n" +
                            ".highlight-sidebar-card.active .label { -fx-text-fill: " + hexHlSideActiveFg + "; }\n" +
                            ".highlight-sidebar-card .toggle-icon { -fx-font-family: 'codicon'; -fx-font-size: 14px; -fx-text-fill: " + hexHlSideEyeInactive + "; }\n" +
                            ".highlight-sidebar-card.active .toggle-icon { -fx-text-fill: " + hexHlSideEyeActive + "; }\n" +
                            ".color-picker { -fx-background-color: " + hexColorPickerBg + "; -fx-text-fill: " + hexColorPickerFg + "; -fx-border-color: " + hexColorPickerBorder + "; -fx-background-radius: 4px; -fx-border-radius: 4px; -fx-border-width: 1px; -fx-background-insets: 0; }\n" +
                            ".color-picker:hover { -fx-background-color: " + hexBtnHoverBg + "; }\n" +
                            ".color-picker:focused { -fx-border-color: " + hexColorPickerBorder + "; }\n" +
                            ".color-picker .color-picker-label { -fx-text-fill: " + hexColorPickerFg + "; }\n" +
                            ".color-picker .color-picker-label .text { -fx-fill: " + hexColorPickerFg + "; }\n" +
                            ".color-picker .arrow-button { -fx-background-color: transparent; -fx-background-insets: 0; -fx-background-radius: 0; }\n" +
                            ".color-picker .arrow-button .arrow { -fx-background-color: " + hexColorPickerFg + "; -fx-background-insets: 0; }\n" +
                            ".color-picker-popup { -fx-background-color: " + hexColorPickerPopupBg + "; -fx-border-color: " + hexColorPickerPopupBorder + "; -fx-border-width: 1px; -fx-background-radius: 6px; -fx-border-radius: 6px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.4), 8, 0, 0, 4); -fx-padding: 10px; }\n" +
                            ".color-picker-popup .color-palette-region { -fx-background-color: transparent; -fx-border-color: transparent; -fx-border-width: 0; -fx-background-insets: 0; -fx-padding: 0; }\n" +
                            ".color-picker-popup .label { -fx-text-fill: " + hexColorPickerFg + "; }\n" +
                            ".color-picker-popup .custom-color-link { -fx-text-fill: " + hexColorPickerFg + "; }\n" +
                            ".color-picker-popup .color-square { -fx-border-color: " + hexColorPickerPopupBorder + "; }\n" +
                            ".custom-color-dialog { -fx-background-color: " + hexColorPickerPopupBg + "; }\n" +
                            ".custom-color-dialog .label { -fx-text-fill: " + hexColorPickerFg + "; }\n" +
                            ".custom-color-dialog .button { -fx-background-color: " + hexColorPickerCustomBtnBg + "; -fx-border-color: " + hexColorPickerCustomBtnBorder + "; -fx-text-fill: " + hexColorPickerCustomBtnFg + "; -fx-border-width: 1px; -fx-background-radius: 4px; -fx-border-radius: 4px; }\n" +
                            ".custom-color-dialog .button:hover { -fx-background-color: " + hexColorPickerCustomBtnHoverBg + "; -fx-border-color: " + hexColorPickerCustomBtnHoverBorder + "; -fx-text-fill: " + hexColorPickerCustomBtnHoverFg + "; }\n" +
                            ".custom-color-dialog .toggle-button { -fx-background-color: " + hexColorPickerCustomToggleBg + "; -fx-border-color: " + hexColorPickerCustomToggleBorder + "; -fx-text-fill: " + hexColorPickerCustomToggleFg + "; -fx-border-width: 1px; -fx-background-insets: 0; }\n" +
                            ".custom-color-dialog .toggle-button:selected { -fx-background-color: " + hexColorPickerCustomToggleSelectedBg + "; -fx-border-color: " + hexColorPickerCustomToggleSelectedBorder + "; -fx-text-fill: " + hexColorPickerCustomToggleSelectedFg + "; }\n" +
                            ".custom-color-dialog .controls-pane { -fx-background-color: " + hexColorPickerCustomControlsBg + "; -fx-padding: 10px; -fx-border-color: " + hexColorPickerCustomToggleBorder + "; -fx-border-width: 1px; -fx-border-radius: 6px; -fx-background-radius: 6px; }\n" +
                            ".custom-color-dialog .customcolor-controls-background { -fx-background-color: transparent; }\n" +
                            ".sidebar-tab-button { -fx-background-color: " + rgbaBtnBg + "; -fx-border-color: " + rgbaBtnBorder + "; -fx-text-fill: " + hexBtnFg + "; -fx-background-insets: 0; -fx-border-width: 1px; -fx-background-radius: 4px; -fx-border-radius: 4px; -fx-padding: 6px 12px; -fx-cursor: hand; -fx-font-weight: bold; -fx-font-size: 11px; }\n" +
                            ".sidebar-tab-button:hover { -fx-background-color: " + rgbaBtnHoverBg + "; -fx-border-color: " + rgbaBtnHoverBorder + "; -fx-text-fill: " + hexBtnHoverFg + "; }\n" +
                            ".sidebar-tab-button:selected { -fx-background-color: " + rgbaCardsSelectedBg + "; -fx-border-color: " + hexSideNavSelectedBorder + "; -fx-text-fill: " + hexSideNavSelectedBorder + "; }\n";
        
        try {
            File cssFile = File.createTempFile("theme", ".css");
            cssFile.deleteOnExit();
            Files.write(cssFile.toPath(), dynamicCss.getBytes(StandardCharsets.UTF_8));
            
            if (root.getScene() != null) {
                String styleCssUri = getClass().getResource("/style.css").toExternalForm();
                String dynamicCssUri = cssFile.toURI().toString();

                root.getScene().getStylesheets().clear();
                root.getScene().getStylesheets().add(styleCssUri);
                root.getScene().getStylesheets().add(dynamicCssUri);

                // Update all active dialogs/stages so that they get themed instantly without closing
                for (javafx.stage.Window window : javafx.stage.Window.getWindows()) {
                    if (window != root.getScene().getWindow() && window.getScene() != null) {
                        javafx.scene.Parent sceneRoot = window.getScene().getRoot();
                        if (sceneRoot instanceof DialogPane) {
                            DialogPane dp = (DialogPane) sceneRoot;
                            dp.getStylesheets().clear();
                            dp.getStylesheets().add(styleCssUri);
                            dp.getStylesheets().add(dynamicCssUri);
                        } else {
                            window.getScene().getStylesheets().clear();
                            window.getScene().getStylesheets().add(styleCssUri);
                            window.getScene().getStylesheets().add(dynamicCssUri);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        // Dynamic DWM top line and border coloring to blend perfectly with theme title bar
        try {
            if (root != null && root.getScene() != null && root.getScene().getWindow() instanceof Stage) {
                Stage stage = (Stage) root.getScene().getWindow();
                String title = stage.getTitle();
                if (title != null && !title.isEmpty()) {
                    com.sun.jna.platform.win32.WinDef.HWND hwnd = com.sun.jna.platform.win32.User32.INSTANCE.FindWindow(null, title);
                    if (hwnd != null) {
                        int colorref = ((titleBg.getBlue() & 0xFF) << 16) | ((titleBg.getGreen() & 0xFF) << 8) | (titleBg.getRed() & 0xFF);
                        int textColor = ((labelFg.getBlue() & 0xFF) << 16) | ((labelFg.getGreen() & 0xFF) << 8) | (labelFg.getRed() & 0xFF);
                        
                        // Set caption background color and icon/text color natively.
                        // Since glass.win.minVisualBounds=true natively fixes maximized margins on High-DPI,
                        // we can safely keep these colors active in both windowed and maximized modes without any padding gaps!
                        MyDwmAPI.INSTANCE.DwmSetWindowAttribute(hwnd, 35, new com.sun.jna.ptr.IntByReference(colorref), 4); // DWMWA_CAPTION_COLOR
                        MyDwmAPI.INSTANCE.DwmSetWindowAttribute(hwnd, 36, new com.sun.jna.ptr.IntByReference(textColor), 4); // DWMWA_TEXT_COLOR
                    }
                }
            }
        } catch (Throwable t) {
            // Safe fallback if JNA fails or on non-Windows OS
        }

        repaintAllTerminals();
    }

    private void rebuildHostsGrid() {
        hostsGrid.getChildren().clear();
        String query = hostsSearchField != null ? hostsSearchField.getText().toLowerCase().trim() : "";
        for (HostProfile item : hosts) {
            if (!query.isEmpty()) {
                boolean match = (item.getName() != null && item.getName().toLowerCase().contains(query)) ||
                                (item.getIp() != null && item.getIp().toLowerCase().contains(query)) ||
                                (item.getUsername() != null && item.getUsername().toLowerCase().contains(query)) ||
                                (item.getUrl() != null && item.getUrl().toLowerCase().contains(query));
                if (!match) continue;
            }
            HBox card = new HBox(12);
            card.setPadding(new Insets(12));
            card.setAlignment(Pos.CENTER_LEFT);
            card.getStyleClass().add("host-card");
            card.setPrefWidth(220);
            card.setMaxWidth(220);
            card.setCursor(javafx.scene.Cursor.HAND);

            Label badge = new Label();
            String firstLetter = (item.getName() == null || item.getName().isEmpty()) ? "?" : item.getName().substring(0, 1).toUpperCase();
            badge.setText(firstLetter);
            badge.setAlignment(Pos.CENTER);
            badge.setPrefSize(32, 32);
            badge.setMinSize(32, 32);
            badge.setMaxSize(32, 32);
            badge.getStyleClass().add("host-badge");

            VBox details = new VBox(2);
            String pName = item.getName() != null ? item.getName() : "";
            if (pName.length() > 21) {
                pName = pName.substring(0, 19) + "...";
            }
            Label nameLabel = new Label(pName);
            nameLabel.getStyleClass().add("host-name");

            String username = item.getUsername() != null ? item.getUsername() : "";
            String ip = item.getIp() != null ? item.getIp() : "";
            String subText = username + "@" + ip;
            if (item.getUrl() != null && !item.getUrl().isEmpty()) {
                subText = username + "@" + item.getUrl();
            }
            if (subText.length() > 26) {
                subText = subText.substring(0, 24) + "...";
            }
            Label userLabel = new Label(subText);
            userLabel.getStyleClass().add("host-username");

            details.getChildren().addAll(nameLabel, userLabel);
            card.getChildren().addAll(badge, details);

            card.setOnMouseClicked(event -> {
                selectedHost.set(item);
                for (javafx.scene.Node node : hostsGrid.getChildren()) {
                    node.getStyleClass().remove("selected");
                    if (node == card) {
                        node.getStyleClass().add("selected");
                    }
                }
            });

            if (selectedHost.get() == item) {
                card.getStyleClass().add("selected");
            }

            hostsGrid.getChildren().add(card);
        }
    }

    private void clearDashboardSnippetSelection() {
        for (javafx.scene.Node section : snippetsDashboardContainer.getChildren()) {
            if (section instanceof VBox) {
                for (javafx.scene.Node child : ((VBox) section).getChildren()) {
                    if (child instanceof FlowPane) {
                        for (javafx.scene.Node node : ((FlowPane) child).getChildren()) {
                            if (node instanceof HBox) {
                                node.getStyleClass().remove("selected");
                            }
                        }
                    }
                }
            }
        }
    }

    private javafx.scene.Node createDashboardSnippetCard(Snippet item) {
        HBox card = new HBox(12);
        card.setPadding(new Insets(12));
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("snippet-card");
        card.setPrefWidth(220);
        card.setMaxWidth(220);
        card.setCursor(javafx.scene.Cursor.HAND);

        Label badge = new Label(">_");
        badge.setAlignment(Pos.CENTER);
        badge.setPrefSize(32, 32);
        badge.setMinSize(32, 32);
        badge.setMaxSize(32, 32);
        badge.getStyleClass().add("snippet-badge");

        VBox details = new VBox(2);
        Label titleLabel = new Label(item.getTitle());
        titleLabel.getStyleClass().add("snippet-title");

        String preview = item.getCode().trim().replace("\n", " ");
        if (preview.length() > 22) {
            preview = preview.substring(0, 20) + "...";
        }
        Label previewLabel = new Label(preview);
        previewLabel.getStyleClass().add("snippet-preview");

        details.getChildren().addAll(titleLabel, previewLabel);
        card.getChildren().addAll(badge, details);

        card.setOnMouseClicked(event -> {
            selectedSnippet.set(item);
            clearDashboardSnippetSelection();
            card.getStyleClass().add("selected");
        });

        if (selectedSnippet.get() == item) {
            card.getStyleClass().add("selected");
        }

        return card;
    }

    private void rebuildSnippetsGrid() {
        snippetsDashboardContainer.getChildren().clear();
        String query = snippetsSearchField != null ? snippetsSearchField.getText().toLowerCase().trim() : "";

        // 1. Render Packages on top
        for (SnippetPackage pkg : packages) {
            boolean pkgMatches = pkg.getName() != null && pkg.getName().toLowerCase().contains(query);
            
            List<Snippet> matchingSnippetsInPkg = new ArrayList<>();
            for (Snippet item : snippets) {
                if (pkg.getId().equals(item.getPackageId())) {
                    boolean snippetMatches = (item.getTitle() != null && item.getTitle().toLowerCase().contains(query)) ||
                                             (item.getCode() != null && item.getCode().toLowerCase().contains(query));
                    if (query.isEmpty() || snippetMatches || pkgMatches) {
                        matchingSnippetsInPkg.add(item);
                    }
                }
            }

            if (query.isEmpty() || pkgMatches || !matchingSnippetsInPkg.isEmpty()) {
                VBox packageSection = new VBox(10);
                packageSection.getStyleClass().add("dashboard-package-section");

                HBox header = new HBox(10);
                header.setAlignment(Pos.CENTER_LEFT);
                Label folderIcon = new Label("\uEA83");
                folderIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 16px; -fx-text-fill: #94a3b8;");
                Label title = new Label(pkg.getName());
                title.getStyleClass().add("dashboard-package-title");
                header.getChildren().addAll(folderIcon, title);

                FlowPane grid = new FlowPane(15, 15);
                grid.setPadding(new Insets(10, 0, 0, 0));

                for (Snippet item : matchingSnippetsInPkg) {
                    javafx.scene.Node card = createDashboardSnippetCard(item);
                    grid.getChildren().add(card);
                }

                if (matchingSnippetsInPkg.isEmpty()) {
                    Label emptyLabel = new Label("No snippets in this package");
                    emptyLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-style: italic; -fx-font-size: 11px; -fx-padding: 5px 0 0 5px;");
                    grid.getChildren().add(emptyLabel);
                }

                packageSection.getChildren().addAll(header, grid);
                snippetsDashboardContainer.getChildren().add(packageSection);
            }
        }

        // 2. Render Snippets without package below
        List<Snippet> matchingStandalone = new ArrayList<>();
        for (Snippet item : snippets) {
            if (item.getPackageId() == null || item.getPackageId().isEmpty()) {
                boolean snippetMatches = item.getTitle().toLowerCase().contains(query) ||
                                         item.getCode().toLowerCase().contains(query);
                if (query.isEmpty() || snippetMatches) {
                    matchingStandalone.add(item);
                }
            }
        }

        if (query.isEmpty() || !matchingStandalone.isEmpty()) {
            VBox standaloneSection = new VBox(10);
            standaloneSection.getStyleClass().add("dashboard-package-section");

            HBox standaloneHeader = new HBox(10);
            standaloneHeader.setAlignment(Pos.CENTER_LEFT);
            Label standaloneIcon = new Label("\uEAE9");
            standaloneIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 16px; -fx-text-fill: #94a3b8;");
            Label standaloneTitle = new Label("Standalone Snippets (No Package)");
            standaloneTitle.getStyleClass().add("dashboard-standalone-title");
            standaloneHeader.getChildren().addAll(standaloneIcon, standaloneTitle);

            FlowPane standaloneGrid = new FlowPane(15, 15);
            standaloneGrid.setPadding(new Insets(10, 0, 0, 0));

            for (Snippet item : matchingStandalone) {
                javafx.scene.Node card = createDashboardSnippetCard(item);
                standaloneGrid.getChildren().add(card);
            }

            if (matchingStandalone.isEmpty()) {
                Label emptyLabel = new Label("No standalone snippets");
                emptyLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-style: italic; -fx-font-size: 11px; -fx-padding: 5px 0 0 5px;");
                standaloneGrid.getChildren().add(emptyLabel);
            }

            standaloneSection.getChildren().addAll(standaloneHeader, standaloneGrid);
            snippetsDashboardContainer.getChildren().add(standaloneSection);
        }
    }

    private void rebuildSessionsGrid() {
        terminalsGrid.getChildren().clear();
        List<Tab> terminalsOnly = new ArrayList<>();
        for (Tab t : workspace.getTabPane().getTabs()) {
            if (t != workspace.getAddTab()) {
                terminalsOnly.add(t);
            }
        }

        if (terminalsOnly.isEmpty()) {
            noActiveSessionsLabel.setVisible(true);
            terminalsGridScrollPane.setVisible(false);
        } else {
            noActiveSessionsLabel.setVisible(false);
            terminalsGridScrollPane.setVisible(true);

            for (Tab tab : terminalsOnly) {
                HBox card = new HBox(12);
                card.setPadding(new Insets(12));
                card.setAlignment(Pos.CENTER_LEFT);
                card.getStyleClass().add("session-cell");
                card.setPrefWidth(220);
                card.setMaxWidth(220);
                card.setCursor(javafx.scene.Cursor.HAND);

                Label badge = new Label("S");
                badge.setAlignment(Pos.CENTER);
                badge.setPrefSize(32, 32);
                badge.setMinSize(32, 32);
                badge.setMaxSize(32, 32);
                badge.getStyleClass().add("host-badge");

                VBox details = new VBox(2);
                Label titleLabel = new Label(tab.getText());
                titleLabel.getStyleClass().add("host-name");

                Label statusLabel = new Label("Active Session");
                statusLabel.getStyleClass().add("host-username");

                details.getChildren().addAll(titleLabel, statusLabel);
                card.getChildren().addAll(badge, details);

                card.setOnMouseClicked(event -> {
                    selectedSession.set(tab);
                    for (javafx.scene.Node node : terminalsGrid.getChildren()) {
                        node.getStyleClass().remove("selected");
                        if (node == card) {
                            node.getStyleClass().add("selected");
                        }
                    }
                    if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                        workspace.getTabPane().getSelectionModel().select(tab);
                        switchMode(AppMode.TERMINAL);
                    }
                });

                if (selectedSession.get() == tab) {
                    card.getStyleClass().add("selected");
                }

                terminalsGrid.getChildren().add(card);
            }
        }
    }

    private void rebuildTerminalSnippetsSidebar() {
        if (terminalSnippetsContainer == null) return;
        terminalSnippetsContainer.getChildren().clear();
        String query = sidebarSearchField != null ? sidebarSearchField.getText().toLowerCase().trim() : "";

        // 1. Grouped by Package
        for (SnippetPackage pkg : packages) {
            boolean pkgMatches = pkg.getName() != null && pkg.getName().toLowerCase().contains(query);
            
            List<Snippet> matchingSnippetsInPkg = new ArrayList<>();
            for (Snippet item : snippets) {
                if (pkg.getId().equals(item.getPackageId())) {
                    boolean snippetMatches = (item.getTitle() != null && item.getTitle().toLowerCase().contains(query)) ||
                                             (item.getCode() != null && item.getCode().toLowerCase().contains(query));
                    if (query.isEmpty() || snippetMatches || pkgMatches) {
                        matchingSnippetsInPkg.add(item);
                    }
                }
            }

            if (query.isEmpty() || pkgMatches || !matchingSnippetsInPkg.isEmpty()) {
                TitledPane pane = new TitledPane();
                pane.setText(pkg.getName());
                // Auto-expand packages when searching to display results instantly
                pane.setExpanded(!query.isEmpty());
                pane.getStyleClass().add("package-titled-pane");

                VBox packageContent = new VBox(8);
                packageContent.setPadding(new Insets(10));
                packageContent.setStyle("-fx-background-color: transparent;");

                for (Snippet item : matchingSnippetsInPkg) {
                    javafx.scene.Node card = createSidebarSnippetCardNode(item);
                    packageContent.getChildren().add(card);
                }

                if (matchingSnippetsInPkg.isEmpty()) {
                    Label emptyLabel = new Label("No snippets inside");
                    emptyLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-style: italic; -fx-font-size: 11px;");
                    packageContent.getChildren().add(emptyLabel);
                }

                pane.setContent(packageContent);
                terminalSnippetsContainer.getChildren().add(pane);
            }
        }

        // 2. Standalone snippets
        List<Snippet> matchingStandalone = new ArrayList<>();
        for (Snippet item : snippets) {
            if (item.getPackageId() == null || item.getPackageId().isEmpty()) {
                boolean snippetMatches = item.getTitle().toLowerCase().contains(query) ||
                                         item.getCode().toLowerCase().contains(query);
                if (query.isEmpty() || snippetMatches) {
                    matchingStandalone.add(item);
                }
            }
        }

        if (query.isEmpty() || !matchingStandalone.isEmpty()) {
            TitledPane pane = new TitledPane();
            pane.setText("Standalone Snippets");
            pane.setExpanded(true); // Default open but fully collapsible
            pane.getStyleClass().add("package-titled-pane");

            VBox standaloneContent = new VBox(8);
            standaloneContent.setPadding(new Insets(10));
            standaloneContent.setStyle("-fx-background-color: transparent;");

            for (Snippet item : matchingStandalone) {
                javafx.scene.Node card = createSidebarSnippetCardNode(item);
                standaloneContent.getChildren().add(card);
            }

            if (matchingStandalone.isEmpty()) {
                Label emptyLabel = new Label("No standalone snippets");
                emptyLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-style: italic; -fx-font-size: 11px;");
                standaloneContent.getChildren().add(emptyLabel);
            }

            pane.setContent(standaloneContent);
            terminalSnippetsContainer.getChildren().add(pane);
        }
    }

    private void rebuildConnectionsGrid() {
        localConnectionsGrid.getChildren().clear();
        remoteConnectionsGrid.getChildren().clear();
        String query = connectionsSearchField != null ? connectionsSearchField.getText().toLowerCase().trim() : "";

        // 1. Gather local shells
        List<String> locals = new ArrayList<>();
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        if (isWindows) {
            locals.add("CMD");
            locals.add("PowerShell");
            if (TopToolBar.isBashAvailableOnWindows()) {
                locals.add("Bash");
            }
        } else {
            locals.add("Bash");
        }

        // Add Local Shells as connection cards
        for (String localShell : locals) {
            if (!query.isEmpty() && !localShell.toLowerCase().contains(query) && !"local shell".contains(query)) {
                continue;
            }
            TopToolBar.DropdownItem item = new TopToolBar.DropdownItem(localShell);
            HBox card = createConnectionCardNode(item, localShell, "Local Shell", ">_");
            localConnectionsGrid.getChildren().add(card);
        }

        // 2. Gather host profiles
        for (HostProfile host : hosts) {
            String subText = (host.getUsername() != null ? host.getUsername() : "") + "@" + (host.getIp() != null ? host.getIp() : "");
            if (host.getUrl() != null && !host.getUrl().isEmpty()) {
                subText = (host.getUsername() != null ? host.getUsername() : "") + "@" + host.getUrl();
            }
            if (!query.isEmpty()) {
                boolean match = (host.getName() != null && host.getName().toLowerCase().contains(query)) ||
                                (host.getIp() != null && host.getIp().toLowerCase().contains(query)) ||
                                (host.getUsername() != null && host.getUsername().toLowerCase().contains(query)) ||
                                (host.getUrl() != null && host.getUrl().toLowerCase().contains(query));
                if (!match) continue;
            }
            TopToolBar.DropdownItem item = new TopToolBar.DropdownItem(host);
            String firstLetter = (host.getName() == null || host.getName().isEmpty()) ? "?" : host.getName().substring(0, 1).toUpperCase();
            HBox card = createConnectionCardNode(item, host.getName(), subText, firstLetter);
            remoteConnectionsGrid.getChildren().add(card);
        }
    }

    private HBox createConnectionCardNode(TopToolBar.DropdownItem item, String title, String subtext, String badgeText) {
        HBox card = new HBox(12);
        card.setPadding(new Insets(12));
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("host-card");
        card.setPrefWidth(220);
        card.setMaxWidth(220);
        card.setCursor(javafx.scene.Cursor.HAND);

        Label badge = new Label(badgeText);
        badge.setAlignment(Pos.CENTER);
        badge.setPrefSize(32, 32);
        badge.setMinSize(32, 32);
        badge.setMaxSize(32, 32);
        badge.getStyleClass().add("host-badge");

        VBox details = new VBox(2);
        String displayTitle = title != null ? title : "";
        if (displayTitle.length() > 21) {
            displayTitle = displayTitle.substring(0, 19) + "...";
        }
        Label nameLabel = new Label(displayTitle);
        nameLabel.getStyleClass().add("host-name");

        String displaySub = subtext != null ? subtext : "";
        if (displaySub.length() > 26) {
            displaySub = displaySub.substring(0, 24) + "...";
        }
        Label subLabel = new Label(displaySub);
        subLabel.getStyleClass().add("host-username");

        details.getChildren().addAll(nameLabel, subLabel);
        card.getChildren().addAll(badge, details);

        card.setOnMouseClicked(event -> {
            selectedConnectionItem.set(item);
            for (javafx.scene.Node node : localConnectionsGrid.getChildren()) {
                node.getStyleClass().remove("selected");
            }
            for (javafx.scene.Node node : remoteConnectionsGrid.getChildren()) {
                node.getStyleClass().remove("selected");
            }
            card.getStyleClass().add("selected");
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                initiateConnectionFromDropdownItem(item);
            }
        });

        if (selectedConnectionItem.get() != null && selectedConnectionItem.get().equals(item)) {
            card.getStyleClass().add("selected");
        }

        return card;
    }

    private void initiateConnectionFromDropdownItem(TopToolBar.DropdownItem item) {
        if (item == null) return;
        if (item.isHost()) {
            connectToHost(item.getHostProfile());
        } else {
            openLocalTerminal(item.getLabel());
        }
        switchMode(AppMode.TERMINAL);
    }

    private void clearSidebarSelection() {
        if (currentlySelectedCardNode != null) {
            currentlySelectedCardNode.getStyleClass().remove("selected");
        }
    }

    private javafx.scene.Node createSidebarSnippetCardNode(Snippet item) {
        HBox card = new HBox(12);
        card.setPadding(new Insets(8, 10, 8, 10));
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("snippet-card");
        card.setCursor(javafx.scene.Cursor.HAND);

        Label badge = new Label(">_");
        badge.setAlignment(Pos.CENTER);
        badge.setPrefSize(32, 32);
        badge.setMinSize(32, 32);
        badge.setMaxSize(32, 32);
        badge.getStyleClass().add("snippet-badge");

        VBox details = new VBox(2);
        VBox.setVgrow(details, Priority.ALWAYS);
        details.setMinWidth(0);
        HBox.setHgrow(details, Priority.ALWAYS);

        HBox titleRow = new HBox(5);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(titleRow, Priority.ALWAYS);
        titleRow.setMinHeight(22);
        titleRow.setPrefHeight(22);
        titleRow.setMaxHeight(22);

        Label titleLabel = new Label(item.getTitle());
        titleLabel.getStyleClass().add("snippet-title");
        titleLabel.setMinWidth(0);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        // Hover buttons
        HBox hoverButtons = new HBox(4);
        hoverButtons.setAlignment(Pos.CENTER_RIGHT);
        hoverButtons.setVisible(false);
        hoverButtons.setManaged(false);

        Button runBtn = new Button("\uEAD3");
        runBtn.getStyleClass().add("snippet-hover-btn");
        runBtn.setMinWidth(22);
        runBtn.setPrefWidth(22);
        runBtn.setMaxWidth(22);
        runBtn.setMinHeight(22);
        runBtn.setPrefHeight(22);
        runBtn.setMaxHeight(22);
        runBtn.setOnAction(e -> {
            e.consume();
            runSnippet(item, true);
        });
        runBtn.setOnMouseClicked(e -> e.consume());

        Button pasteBtn = new Button("\uEC36");
        pasteBtn.getStyleClass().add("snippet-hover-btn");
        pasteBtn.setMinWidth(22);
        pasteBtn.setPrefWidth(22);
        pasteBtn.setMaxWidth(22);
        pasteBtn.setMinHeight(22);
        pasteBtn.setPrefHeight(22);
        pasteBtn.setMaxHeight(22);
        pasteBtn.setOnAction(e -> {
            e.consume();
            runSnippet(item, false);
        });
        pasteBtn.setOnMouseClicked(e -> e.consume());

        hoverButtons.getChildren().addAll(runBtn, pasteBtn);
        titleRow.getChildren().addAll(titleLabel, hoverButtons);

        String preview = item.getCode().trim().replace("\n", " ");
        if (preview.length() > 22) {
            preview = preview.substring(0, 20) + "...";
        }
        Label previewLabel = new Label(preview);
        previewLabel.getStyleClass().add("snippet-preview");

        details.getChildren().addAll(titleRow, previewLabel);
        card.getChildren().addAll(badge, details);

        // Hover behaviors
        card.setOnMouseEntered(e -> {
            hoverButtons.setVisible(true);
            hoverButtons.setManaged(true);
        });
        card.setOnMouseExited(e -> {
            hoverButtons.setVisible(false);
            hoverButtons.setManaged(false);
        });

        card.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                if (event.getClickCount() == 2) {
                    executeSnippet(item);
                } else {
                    clearSidebarSelection();
                    currentlySelectedCardNode = card;
                    card.getStyleClass().add("selected");
                }
            }
        });

        // Right click ContextMenu for Editing Snippets
        ContextMenu contextMenu = new ContextMenu();
        MenuItem editItem = new MenuItem("Edit");
        Label editIcon = new Label("\uEC68");
        editIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 12px;");
        editItem.setGraphic(editIcon);
        editItem.setOnAction(e -> {
            showAddSnippetDialog(snippets, item);
        });
        contextMenu.getItems().add(editItem);

        card.setOnContextMenuRequested(event -> {
            clearSidebarSelection();
            currentlySelectedCardNode = card;
            card.getStyleClass().add("selected");
            contextMenu.show(card, event.getScreenX(), event.getScreenY());
        });

        return card;
    }

    private String toHex(com.jediterm.core.Color color) {
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    private String toRgba(com.jediterm.core.Color color, double opacity) {
        return String.format("rgba(%d, %d, %d, %.2f)", color.getRed(), color.getGreen(), color.getBlue(), opacity);
    }

    private String getGradientStyle(com.jediterm.core.Color startColor, double startOpacity,
                                    com.jediterm.core.Color endColor, double endOpacity,
                                    String direction, String ratio) {
        String[] stops = ratio.trim().split("\\s+");
        String stop1 = stops.length > 0 ? stops[0] : "0%";
        String stop2 = stops.length > 1 ? stops[1] : "100%";
        
        return String.format("linear-gradient(%s, %s %s, %s %s)",
            direction,
            toRgba(startColor, startOpacity), stop1,
            toRgba(endColor, endOpacity), stop2
        );
    }

    private interface MyDwmAPI extends com.sun.jna.win32.StdCallLibrary {
        MyDwmAPI INSTANCE = com.sun.jna.Native.load("dwmapi", MyDwmAPI.class, com.sun.jna.win32.W32APIOptions.DEFAULT_OPTIONS);
        
        int DwmSetWindowAttribute(
            com.sun.jna.platform.win32.WinDef.HWND hwnd, 
            int dwAttribute, 
            com.sun.jna.ptr.IntByReference pvAttribute, 
            int cbAttribute
        );
    }

    private void showAddHighlightDialog(ObservableList<WordHighlight> highlights, WordHighlight existing) {
        HighlightDialog dialog = new HighlightDialog(existing, highlightPackages);
        Optional<WordHighlight> result = dialog.showAndWait();
        result.ifPresent(wh -> {
            if (existing != null) {
                com.tinsider.util.ActivityLogger.logUserAction("EDIT_HIGHLIGHT", "User edited highlight: " + wh.getWord());
                highlights.remove(existing);
                selectedHighlight.set(wh);
            } else {
                com.tinsider.util.ActivityLogger.logUserAction("ADD_HIGHLIGHT", "User added highlight: " + wh.getWord());
            }
            highlights.add(wh);
            StorageManager.saveHighlights(new ArrayList<>(highlights));
            rebuildHighlightsGrid();
            repaintAllTerminals();
        });
    }

    private void clearDashboardHighlightSelection() {
        if (highlightsDashboardContainer == null) return;
        for (javafx.scene.Node section : highlightsDashboardContainer.getChildren()) {
            if (section instanceof VBox) {
                for (javafx.scene.Node child : ((VBox) section).getChildren()) {
                    if (child instanceof FlowPane) {
                        for (javafx.scene.Node node : ((FlowPane) child).getChildren()) {
                            if (node instanceof HBox) {
                                node.getStyleClass().remove("selected");
                            }
                        }
                    }
                }
            }
        }
    }

    private javafx.scene.Node createDashboardHighlightCard(WordHighlight wh) {
        HBox card = new HBox(12);
        card.setPadding(new Insets(12));
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("highlight-card");
        card.setPrefWidth(220);
        card.setMaxWidth(220);
        card.setCursor(javafx.scene.Cursor.HAND);

        Label colorBox = new Label();
        colorBox.setPrefSize(20, 20);
        colorBox.setMinSize(20, 20);
        colorBox.setMaxSize(20, 20);
        colorBox.setStyle("-fx-background-color: " + wh.getColorHex() + "; -fx-background-radius: 10px;");

        VBox details = new VBox(2);
        String displayName = wh.getName() != null && !wh.getName().isEmpty() ? wh.getName() : wh.getWord();
        Label wordLabel = new Label(displayName);
        wordLabel.getStyleClass().add("host-name");
        wordLabel.setStyle("-fx-font-weight: bold;");

        Label patternLabel = null;
        if (wh.getName() != null && !wh.getName().isEmpty()) {
            patternLabel = new Label(wh.getWord());
            patternLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #94a3b8; -fx-font-style: italic;");
        }

        Label colorLabel = new Label(wh.getColorHex());
        colorLabel.getStyleClass().add("host-username");
        
        if (patternLabel != null) {
            details.getChildren().addAll(wordLabel, patternLabel, colorLabel);
        } else {
            details.getChildren().addAll(wordLabel, colorLabel);
        }

        card.getChildren().addAll(colorBox, details);

        card.setOnMouseClicked(event -> {
            selectedHighlight.set(wh);
            clearDashboardHighlightSelection();
            card.getStyleClass().add("selected");
        });

        if (selectedHighlight.get() == wh) {
            card.getStyleClass().add("selected");
        }

        return card;
    }

    private void rebuildHighlightsGrid() {
        if (highlightsDashboardContainer == null) return;
        highlightsDashboardContainer.getChildren().clear();
        String query = highlightsSearchField != null ? highlightsSearchField.getText().toLowerCase().trim() : "";

        // 1. Render Packages on top
        for (HighlightPackage pkg : highlightPackages) {
            boolean pkgMatches = pkg.getName() != null && pkg.getName().toLowerCase().contains(query);
            
            List<WordHighlight> matchingHighlightsInPkg = new ArrayList<>();
            for (WordHighlight item : highlights) {
                if (pkg.getId().equals(item.getPackageId())) {
                    boolean highlightMatches = (item.getWord() != null && item.getWord().toLowerCase().contains(query));
                    if (query.isEmpty() || highlightMatches || pkgMatches) {
                        matchingHighlightsInPkg.add(item);
                    }
                }
            }

            if (query.isEmpty() || pkgMatches || !matchingHighlightsInPkg.isEmpty()) {
                VBox packageSection = new VBox(10);
                packageSection.getStyleClass().add("dashboard-package-section");

                HBox header = new HBox(10);
                header.setAlignment(Pos.CENTER_LEFT);
                Label folderIcon = new Label("\uEA83");
                folderIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 16px; -fx-text-fill: #94a3b8;");
                Label title = new Label(pkg.getName());
                title.getStyleClass().add("dashboard-package-title");
                header.getChildren().addAll(folderIcon, title);

                FlowPane grid = new FlowPane(15, 15);
                grid.setPadding(new Insets(10, 0, 0, 0));

                for (WordHighlight item : matchingHighlightsInPkg) {
                    javafx.scene.Node card = createDashboardHighlightCard(item);
                    grid.getChildren().add(card);
                }

                if (matchingHighlightsInPkg.isEmpty()) {
                    Label emptyLabel = new Label("No highlights in this package");
                    emptyLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-style: italic; -fx-font-size: 11px; -fx-padding: 5px 0 0 5px;");
                    grid.getChildren().add(emptyLabel);
                }

                packageSection.getChildren().addAll(header, grid);
                highlightsDashboardContainer.getChildren().add(packageSection);
            }
        }

        // 2. Render Highlights without package below
        List<WordHighlight> matchingStandalone = new ArrayList<>();
        for (WordHighlight item : highlights) {
            if (item.getPackageId() == null || item.getPackageId().isEmpty()) {
                boolean highlightMatches = item.getWord().toLowerCase().contains(query);
                if (query.isEmpty() || highlightMatches) {
                    matchingStandalone.add(item);
                }
            }
        }

        if (query.isEmpty() || !matchingStandalone.isEmpty()) {
            VBox standaloneSection = new VBox(10);
            standaloneSection.getStyleClass().add("dashboard-package-section");

            HBox standaloneHeader = new HBox(10);
            standaloneHeader.setAlignment(Pos.CENTER_LEFT);
            Label standaloneIcon = new Label("\uEAE9");
            standaloneIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 16px; -fx-text-fill: #94a3b8;");
            Label standaloneTitle = new Label("Standalone Highlights (No Package)");
            standaloneTitle.getStyleClass().add("dashboard-standalone-title");
            standaloneHeader.getChildren().addAll(standaloneIcon, standaloneTitle);

            FlowPane standaloneGrid = new FlowPane(15, 15);
            standaloneGrid.setPadding(new Insets(10, 0, 0, 0));

            for (WordHighlight item : matchingStandalone) {
                javafx.scene.Node card = createDashboardHighlightCard(item);
                standaloneGrid.getChildren().add(card);
            }

            if (matchingStandalone.isEmpty()) {
                Label emptyLabel = new Label("No standalone highlights");
                emptyLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-style: italic; -fx-font-size: 11px; -fx-padding: 5px 0 0 5px;");
                standaloneGrid.getChildren().add(emptyLabel);
            }

            standaloneSection.getChildren().addAll(standaloneHeader, standaloneGrid);
            highlightsDashboardContainer.getChildren().add(standaloneSection);
        }
    }

    private javafx.scene.Node createSidebarHighlightCardNode(WordHighlight wh, com.tinsider.ui.components.terminal.split.TerminalPane activePane, Set<String> activeIds, com.tinsider.ui.components.terminal.split.TerminalSplitPane splitPane) {
        boolean active = activeIds.contains(wh.getId());

        HBox card = new HBox(8);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("highlight-sidebar-card");
        if (active) {
            card.getStyleClass().add("active");
        }

        Label indicator = new Label();
        indicator.setPrefSize(8, 8);
        indicator.setMinSize(8, 8);
        indicator.setMaxSize(8, 8);
        indicator.setStyle("-fx-background-color: " + wh.getColorHex() + "; -fx-background-radius: 4px;");

        String displayName = wh.getName() != null && !wh.getName().isEmpty() ? wh.getName() : wh.getWord();
        Label wordLabel = new Label(displayName);
        wordLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        
        Tooltip patternTooltip = new Tooltip(wh.getWord());
        wordLabel.setTooltip(patternTooltip);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label eyeIcon = new Label(active ? "\uEB79" : "\uEB7A");
        eyeIcon.getStyleClass().add("toggle-icon");

        Button caseIcon = new Button("\uEAB1");
        caseIcon.getStyleClass().add("snippet-hover-btn");
        caseIcon.setMinWidth(22);
        caseIcon.setPrefWidth(22);
        caseIcon.setMaxWidth(22);
        caseIcon.setMinHeight(22);
        caseIcon.setPrefHeight(22);
        caseIcon.setMaxHeight(22);
        
        // Toggle style dynamically depending on state
        Runnable updateCaseIconStyle = () -> {
            boolean isInsensitive = activePane.getCaseInsensitiveHighlightIds().contains(wh.getId());
            TerminalTheme currentTheme = splitPane.getCurrentTheme();
            com.jediterm.core.Color accentColor = currentTheme.getCursorColor();
            com.jediterm.core.Color btnBg = currentTheme.getColor("controls.snippet.hover.btn.background", currentTheme.getBackgroundColor());
            com.jediterm.core.Color btnBorder = currentTheme.getColor("controls.snippet.hover.btn.border", currentTheme.getSelectionColor());
            com.jediterm.core.Color btnHoverBg = currentTheme.getColor("controls.snippet.hover.btn.hover.background", currentTheme.getSelectionColor());
            com.jediterm.core.Color btnHoverBorder = currentTheme.getColor("controls.snippet.hover.btn.hover.border", currentTheme.getCursorColor());

            String hexAccent = toHex(accentColor);
            String hexBtnBg = toHex(btnBg);
            String hexBtnBorder = toHex(btnBorder);
            String hexBtnHoverBg = toHex(btnHoverBg);
            String hexBtnHoverBorder = toHex(btnHoverBorder);

            if (isInsensitive) {
                caseIcon.setStyle(
                    "-fx-font-family: 'codicon'; " +
                    "-fx-font-size: 11px; " +
                    "-fx-text-fill: " + hexAccent + "; " +
                    "-fx-background-color: " + hexBtnHoverBg + "; " +
                    "-fx-border-color: " + hexBtnHoverBorder + "; " +
                    "-fx-border-width: 1px; " +
                    "-fx-background-radius: 3px; " +
                    "-fx-border-radius: 3px; " +
                    "-fx-opacity: 1.0; " +
                    "-fx-cursor: hand;"
                );
            } else {
                caseIcon.setStyle(
                    "-fx-font-family: 'codicon'; " +
                    "-fx-font-size: 11px; " +
                    "-fx-text-fill: #94a3b8; " +
                    "-fx-background-color: " + hexBtnBg + "; " +
                    "-fx-border-color: " + hexBtnBorder + "; " +
                    "-fx-border-width: 1px; " +
                    "-fx-background-radius: 3px; " +
                    "-fx-border-radius: 3px; " +
                    "-fx-opacity: 0.5; " +
                    "-fx-cursor: hand;"
                );
            }
        };
        updateCaseIconStyle.run();

        caseIcon.setTooltip(new javafx.scene.control.Tooltip("Toggle Case Insensitive Match"));

        caseIcon.setOnAction(e -> e.consume());
        caseIcon.setOnMouseClicked(e -> {
            e.consume(); // Prevent toggling the card's active state
            boolean nextState = !activePane.getCaseInsensitiveHighlightIds().contains(wh.getId());
            activePane.toggleCaseInsensitive(wh.getId(), nextState);
            
            // Also update the global WordHighlight object and save it,
            // so that this preference is persisted in highlights.json for future terminals / sessions!
            wh.setCaseInsensitive(nextState);
            StorageManager.saveHighlights(new ArrayList<>(highlights));
            
            updateCaseIconStyle.run();
        });

        card.getChildren().addAll(indicator, wordLabel, spacer, caseIcon, eyeIcon);

        card.setOnMouseClicked(e -> {
            boolean currentlyActive = activePane.getActiveHighlightIds().contains(wh.getId());
            boolean nextState = !currentlyActive;
            activePane.toggleHighlight(wh.getId(), nextState);
            
            // Update UI state immediately
            if (nextState) {
                if (!card.getStyleClass().contains("active")) {
                    card.getStyleClass().add("active");
                }
                eyeIcon.setText("\uEB79");
            } else {
                card.getStyleClass().remove("active");
                eyeIcon.setText("\uEB7A");
            }
        });

        return card;
    }

    public void rebuildSidebarHighlights() {
        if (terminalHighlightsContainer == null) return;
        terminalHighlightsContainer.getChildren().clear();

        Tab activeTab = workspace.getTabPane().getSelectionModel().getSelectedItem();
        if (activeTab == null) return;

        com.tinsider.ui.components.terminal.split.TerminalSplitPane splitPane = tabSplitPaneMap.get(activeTab);
        if (splitPane == null) return;

        com.tinsider.ui.components.terminal.split.TerminalPane activePane = splitPane.getActiveTerminalPane();
        if (activePane == null) return;

        Set<String> activeIds = activePane.getActiveHighlightIds();

        String query = sidebarHighlightsSearchField != null ? sidebarHighlightsSearchField.getText().toLowerCase().trim() : "";

        // 1. Grouped by Package
        for (HighlightPackage pkg : highlightPackages) {
            boolean pkgMatches = pkg.getName() != null && pkg.getName().toLowerCase().contains(query);
            
            List<WordHighlight> packageHighlights = new ArrayList<>();
            for (WordHighlight wh : highlights) {
                if (pkg.getId().equals(wh.getPackageId())) {
                    boolean whMatches = wh.getWord() != null && wh.getWord().toLowerCase().contains(query);
                    if (query.isEmpty() || pkgMatches || whMatches) {
                        packageHighlights.add(wh);
                    }
                }
            }

            if (!packageHighlights.isEmpty()) {
                TitledPane pane = new TitledPane();
                pane.getStyleClass().add("package-titled-pane");
                pane.setExpanded(!query.isEmpty() || expandedPackageIds.contains(pkg.getId()));

                pane.expandedProperty().addListener((obs, oldVal, newVal) -> {
                    if (newVal) {
                        expandedPackageIds.add(pkg.getId());
                    } else {
                        expandedPackageIds.remove(pkg.getId());
                    }
                });

                // Calculate package active state
                int activeCount = 0;
                for (WordHighlight wh : packageHighlights) {
                    if (activeIds.contains(wh.getId())) {
                        activeCount++;
                    }
                }
                boolean pkgAllActive = (activeCount == packageHighlights.size());
                boolean pkgAnyActive = (activeCount > 0);

                if (pkgAnyActive) {
                    pane.getStyleClass().add("active-package");
                } else {
                    pane.getStyleClass().add("inactive-package");
                }

                // Build custom header with title and eye icon for package activation
                HBox header = new HBox(8);
                header.setAlignment(Pos.CENTER_LEFT);
                header.setMaxWidth(Double.MAX_VALUE);
                header.prefWidthProperty().bind(pane.widthProperty().subtract(45));

                Label folderIcon = new Label("\uEA83");
                folderIcon.getStyleClass().add("package-folder-icon");
                folderIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 14px;");

                Label titleLabel = new Label(pkg.getName());
                titleLabel.getStyleClass().add("package-title-label");
                titleLabel.setStyle("-fx-font-weight: bold;");
                titleLabel.setMinWidth(0);
                titleLabel.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(titleLabel, Priority.ALWAYS);

                Label pkgEyeIcon = new Label(pkgAnyActive ? "\uEB79" : "\uEB7A");
                pkgEyeIcon.getStyleClass().addAll("package-eye-icon", pkgAnyActive ? "active" : "inactive");
                pkgEyeIcon.setTooltip(new Tooltip(pkgAnyActive ? "Deactivate Package" : "Activate Package"));

                pkgEyeIcon.setOnMousePressed(ev -> ev.consume());
                pkgEyeIcon.setOnMouseReleased(ev -> ev.consume());
                pkgEyeIcon.setOnMouseClicked(ev -> {
                    ev.consume(); // Prevent expanding/collapsing the TitledPane
                    boolean nextState = !pkgAnyActive; // if any active, turn off; otherwise turn on
                    for (WordHighlight wh : packageHighlights) {
                        activePane.toggleHighlight(wh.getId(), nextState);
                    }
                    rebuildSidebarHighlights();
                });

                header.getChildren().addAll(folderIcon, titleLabel, pkgEyeIcon);
                pane.setGraphic(header);

                VBox packageContent = new VBox(8);
                packageContent.setPadding(new Insets(10));
                packageContent.setStyle("-fx-background-color: transparent;");

                for (WordHighlight wh : packageHighlights) {
                    javafx.scene.Node card = createSidebarHighlightCardNode(wh, activePane, activeIds, splitPane);
                    packageContent.getChildren().add(card);
                }

                pane.setContent(packageContent);
                terminalHighlightsContainer.getChildren().add(pane);
            }
        }

        // 2. Standalone highlights
        List<WordHighlight> standaloneHighlights = new ArrayList<>();
        for (WordHighlight wh : highlights) {
            if (wh.getPackageId() == null || wh.getPackageId().isEmpty()) {
                boolean whMatches = wh.getWord() != null && wh.getWord().toLowerCase().contains(query);
                if (query.isEmpty() || whMatches) {
                    standaloneHighlights.add(wh);
                }
            }
        }

        if (!standaloneHighlights.isEmpty()) {
            TitledPane pane = new TitledPane();
            pane.getStyleClass().add("package-titled-pane");
            pane.setExpanded(!query.isEmpty() || expandedPackageIds.contains("standalone"));

            pane.expandedProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal) {
                    expandedPackageIds.add("standalone");
                } else {
                    expandedPackageIds.remove("standalone");
                }
            });

            int activeCount = 0;
            for (WordHighlight wh : standaloneHighlights) {
                if (activeIds.contains(wh.getId())) {
                    activeCount++;
                }
            }
            boolean pkgAnyActive = (activeCount > 0);

            if (pkgAnyActive) {
                pane.getStyleClass().add("active-package");
            } else {
                pane.getStyleClass().add("inactive-package");
            }

            HBox header = new HBox(8);
            header.setAlignment(Pos.CENTER_LEFT);
            header.setMaxWidth(Double.MAX_VALUE);
            header.prefWidthProperty().bind(pane.widthProperty().subtract(45));

            Label standaloneIcon = new Label("\uEAE9");
            standaloneIcon.getStyleClass().add("package-folder-icon");
            standaloneIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 14px;");

            Label titleLabel = new Label("Standalone Highlights");
            titleLabel.getStyleClass().add("package-title-label");
            titleLabel.setStyle("-fx-font-weight: bold;");
            titleLabel.setMinWidth(0);
            titleLabel.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(titleLabel, Priority.ALWAYS);

            Label pkgEyeIcon = new Label(pkgAnyActive ? "\uEB79" : "\uEB7A");
            pkgEyeIcon.getStyleClass().addAll("package-eye-icon", pkgAnyActive ? "active" : "inactive");
            pkgEyeIcon.setTooltip(new Tooltip(pkgAnyActive ? "Deactivate Standalone" : "Activate Standalone"));

            pkgEyeIcon.setOnMousePressed(ev -> ev.consume());
            pkgEyeIcon.setOnMouseReleased(ev -> ev.consume());
            pkgEyeIcon.setOnMouseClicked(ev -> {
                ev.consume();
                boolean nextState = !pkgAnyActive;
                for (WordHighlight wh : standaloneHighlights) {
                    activePane.toggleHighlight(wh.getId(), nextState);
                }
                rebuildSidebarHighlights();
            });

            header.getChildren().addAll(standaloneIcon, titleLabel, pkgEyeIcon);
            pane.setGraphic(header);

            VBox standaloneContent = new VBox(8);
            standaloneContent.setPadding(new Insets(10));
            standaloneContent.setStyle("-fx-background-color: transparent;");

            for (WordHighlight wh : standaloneHighlights) {
                javafx.scene.Node card = createSidebarHighlightCardNode(wh, activePane, activeIds, splitPane);
                standaloneContent.getChildren().add(card);
            }

            pane.setContent(standaloneContent);
            terminalHighlightsContainer.getChildren().add(pane);
        }
    }

    private void styleNewWindow(javafx.stage.Window win) {
        if (win.getScene() != null) {
            applyStylesToScene(win.getScene());
        }
        win.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                applyStylesToScene(newScene);
            }
        });
        applyDwmToWindow(win);
    }

    private void applyStylesToScene(javafx.scene.Scene scene) {
        if (root != null && root.getScene() != null) {
            scene.getStylesheets().clear();
            scene.getStylesheets().addAll(root.getScene().getStylesheets());
        }
    }

    private void applyDwmToWindow(javafx.stage.Window win) {
        if (win instanceof Stage) {
            Stage stage = (Stage) win;
            stage.showingProperty().addListener((obs, wasShowing, isShowing) -> {
                if (isShowing) {
                    Platform.runLater(() -> {
                        applyNativeDwm(stage);
                    });
                }
            });
            if (stage.isShowing()) {
                Platform.runLater(() -> {
                    applyNativeDwm(stage);
                });
            }
        }
    }

    private void applyNativeDwm(Stage stage) {
        try {
            String title = stage.getTitle();
            if (title == null || title.isEmpty()) {
                title = "Custom Color";
                stage.setTitle(title);
            }
            com.sun.jna.platform.win32.WinDef.HWND hwnd = com.sun.jna.platform.win32.User32.INSTANCE.FindWindow(null, title);
            if (hwnd != null && currentTheme != null) {
                com.jediterm.core.Color titleBg = currentTheme.getColor("titlebar.background", currentTheme.getBackgroundColor());
                com.jediterm.core.Color labelFg = currentTheme.getColor("global.label.foreground", currentTheme.getTextColor());
                int colorref = ((titleBg.getBlue() & 0xFF) << 16) | ((titleBg.getGreen() & 0xFF) << 8) | (titleBg.getRed() & 0xFF);
                int textColor = ((labelFg.getBlue() & 0xFF) << 16) | ((labelFg.getGreen() & 0xFF) << 8) | (labelFg.getRed() & 0xFF);
                MyDwmAPI.INSTANCE.DwmSetWindowAttribute(hwnd, 35, new com.sun.jna.ptr.IntByReference(colorref), 4);
                MyDwmAPI.INSTANCE.DwmSetWindowAttribute(hwnd, 36, new com.sun.jna.ptr.IntByReference(textColor), 4);
            }
        } catch (Throwable ignored) {}
    }

    public static void main(String[] args) {
        // Fix JavaFX High-DPI maximized window shift and button shrinking bug on Windows
        System.setProperty("glass.win.minVisualBounds", "true");
        launch(args);
    }
}

