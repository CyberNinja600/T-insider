package com.tinsider.ui;

import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.ui.JediTermWidget;
import com.tinsider.config.TerminalTheme;
import com.tinsider.config.ThemeManager;
import com.tinsider.model.HostProfile;
import com.tinsider.model.Snippet;
import com.tinsider.model.SnippetPackage;
import com.tinsider.storage.StorageManager;
import com.tinsider.terminal.ConnectionManager;
import com.tinsider.terminal.TerminalTabFactory;
import com.tinsider.ui.components.sidebar.SidebarPane;
import com.tinsider.ui.components.terminal.TerminalWorkspace;
import com.tinsider.ui.components.toolbar.CustomTitleBar;
import com.tinsider.ui.components.toolbar.TopToolBar;
import com.tinsider.ui.dialogs.AlertHelper;
import com.tinsider.util.WindowResizer;
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
import com.tinsider.ui.components.sidebar.HostListCell;
import com.tinsider.ui.components.sidebar.SnippetListCell;
import com.tinsider.ui.dialogs.HostDialog;
import com.tinsider.ui.dialogs.SnippetDialog;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public class MainApp extends Application {
    public enum AppMode { DASHBOARD, TERMINAL }
    public enum DashboardTab { HOSTS, SNIPPETS, TERMINALS }

    private AppMode currentMode = AppMode.DASHBOARD;
    private DashboardTab currentDashboardTab = DashboardTab.HOSTS;

    private final ObservableList<HostProfile> hosts = FXCollections.observableArrayList();
    private final ObservableList<Snippet> snippets = FXCollections.observableArrayList();
    private final ObservableList<SnippetPackage> packages = FXCollections.observableArrayList();

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
    private VBox hostsDashboard;
    private VBox snippetsDashboard;
    private VBox terminalsDashboard;

    // Grids
    private FlowPane hostsGrid;
    private VBox snippetsDashboardContainer;
    private FlowPane terminalsGrid;
    private ScrollPane hostsGridScrollPane;
    private ScrollPane snippetsGridScrollPane;
    private ScrollPane terminalsGridScrollPane;

    // Selection Trackers
    private final ObjectProperty<HostProfile> selectedHost = new SimpleObjectProperty<>(null);
    private final ObjectProperty<Snippet> selectedSnippet = new SimpleObjectProperty<>(null);
    private final ObjectProperty<Tab> selectedSession = new SimpleObjectProperty<>(null);
    private Label noActiveSessionsLabel;

    // Search Fields
    private TextField hostsSearchField;
    private TextField snippetsSearchField;
    private TextField sidebarSearchField;


    @Override
    public void start(Stage primaryStage) {
        tabSplitPaneMapStaticInstance = tabSplitPaneMap;
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

        // Load settings and restore shell type + theme selection
        Map<String, String> settings = StorageManager.loadSettings();
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String defaultShell = isWindows ? "CMD" : "Bash";
        String savedShell = settings.getOrDefault("shell", defaultShell);
        String savedThemeName = settings.getOrDefault("theme", "Default Space Blue");

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

        ToggleButton navHostsBtn = new ToggleButton("Hosts");
        ToggleButton navSnippetsBtn = new ToggleButton("Snippets");
        ToggleButton navTerminalsBtn = new ToggleButton("Terminals");

        ToggleGroup navGroup = new ToggleGroup();
        navHostsBtn.setToggleGroup(navGroup);
        navSnippetsBtn.setToggleGroup(navGroup);
        navTerminalsBtn.setToggleGroup(navGroup);
        navHostsBtn.setSelected(true);

        navHostsBtn.setMaxWidth(Double.MAX_VALUE);
        navSnippetsBtn.setMaxWidth(Double.MAX_VALUE);
        navTerminalsBtn.setMaxWidth(Double.MAX_VALUE);

        navHostsBtn.getStyleClass().add("nav-button");
        navSnippetsBtn.getStyleClass().add("nav-button");
        navTerminalsBtn.getStyleClass().add("nav-button");

        slimNavRail.getChildren().addAll(appTitleLabel, navHostsBtn, navSnippetsBtn, navTerminalsBtn);

        // Navigation Actions
        navHostsBtn.setOnAction(e -> switchDashboardTab(DashboardTab.HOSTS));
        navSnippetsBtn.setOnAction(e -> switchDashboardTab(DashboardTab.SNIPPETS));
        navTerminalsBtn.setOnAction(e -> switchDashboardTab(DashboardTab.TERMINALS));

        // 3. Build Dashboard Center Panes (Hosts, Snippets, Terminals)
        dashboardCenterStack = new StackPane();
        dashboardCenterStack.getStyleClass().add("dashboard-center-stack");

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
        Button addHostBtn = new Button("+ Add");
        Button editHostBtn = new Button("Edit");
        Button deleteHostBtn = new Button("Delete");
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
        Button addSnippetBtn = new Button("+ Add Snippet");
        Button editSnippetBtn = new Button("Edit");
        Button deleteSnippetBtn = new Button("Delete");
        Button addPackageBtn = new Button("+ Add Package");
        Button deletePackageBtn = new Button("Delete Package");
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

        // Add to stack
        dashboardCenterStack.getChildren().addAll(hostsDashboard, snippetsDashboard, terminalsDashboard);

        // Initial Grid Renderings
        rebuildHostsGrid();
        rebuildSnippetsGrid();
        rebuildSessionsGrid();

        // 4. Build Terminal Mode - Snippet Execution Sidebar (Left)
        snippetExecutionSidebar = new VBox(15);
        snippetExecutionSidebar.setPadding(new Insets(20, 10, 20, 10));
        snippetExecutionSidebar.setPrefWidth(240);
        snippetExecutionSidebar.getStyleClass().add("sidebar");

        Button backToDashboardBtn = new Button("< Dashboard");
        backToDashboardBtn.getStyleClass().add("back-button");
        backToDashboardBtn.setMaxWidth(Double.MAX_VALUE);
        backToDashboardBtn.setOnAction(e -> switchMode(AppMode.DASHBOARD));

        Label terminalSnippetsHeader = new Label("Snippets Launcher");
        terminalSnippetsHeader.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
        terminalSnippetsHeader.getStyleClass().add("snippets-launcher-header");

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

        snippetExecutionSidebar.getChildren().addAll(backToDashboardBtn, terminalSnippetsHeader, sidebarSearchField, sidebarScrollPane);

        // 5. Root Layout & Theme Control
        topToolBar = new TopToolBar(availableThemes, savedThemeName, savedShell, hosts,
            theme -> {
                applyTheme(theme);
                Map<String, String> currentSettings = StorageManager.loadSettings();
                currentSettings.put("theme", theme.getName());
                StorageManager.saveSettings(currentSettings);
            },
            shell -> {
                Map<String, String> currentSettings = StorageManager.loadSettings();
                currentSettings.put("shell", shell);
                StorageManager.saveSettings(currentSettings);
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
            }
        );
        topToolBar.setShellSelectionVisible(false);

        root = new BorderPane();
        
        // Initial state
        switchMode(AppMode.DASHBOARD);
        switchDashboardTab(DashboardTab.HOSTS);

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

        // Start Live Reloader for themes and properties files
        Runnable themeReloader = () -> {
            Platform.runLater(() -> {
                System.out.println("[LiveReloader] Theme or config files changed! Reloading UI colors and styles...");
                com.tinsider.config.ThemeManager.reloadConfig();
                String currentThemeName = topToolBar.getSelectedTheme();
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
            for (Tab tab : new ArrayList<>(workspace.getTabPane().getTabs())) {
                closeTab(tab);
            }
            Platform.exit();
            System.exit(0);
        });
    }

    private void switchMode(AppMode mode) {
        this.currentMode = mode;
        if (mode == AppMode.DASHBOARD) {
            root.setLeft(slimNavRail);
            root.setCenter(dashboardCenterStack);
            updateActiveSessionsList();
            if (topToolBar != null) {
                topToolBar.setShellSelectionVisible(false);
            }
        } else {
            root.setLeft(snippetExecutionSidebar);
            root.setCenter(workspace);
            if (topToolBar != null) {
                topToolBar.setShellSelectionVisible(true);
            }
        }
    }

    private void switchDashboardTab(DashboardTab tab) {
        this.currentDashboardTab = tab;
        hostsDashboard.setVisible(false);
        snippetsDashboard.setVisible(false);
        terminalsDashboard.setVisible(false);

        if (tab == DashboardTab.HOSTS) {
            hostsDashboard.setVisible(true);
            hostsDashboard.toFront();
        } else if (tab == DashboardTab.SNIPPETS) {
            snippetsDashboard.setVisible(true);
            snippetsDashboard.toFront();
        } else if (tab == DashboardTab.TERMINALS) {
            terminalsDashboard.setVisible(true);
            terminalsDashboard.toFront();
            updateActiveSessionsList();
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
                hosts.remove(existing);
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
                snippets.remove(existing);
            }
            snippets.add(snippet);
            StorageManager.saveSnippets(new ArrayList<>(snippets));
        });
    }

    private void openLocalTerminal(String type) {
        try {
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
        com.tinsider.ui.components.terminal.split.TerminalSplitPane splitPane = tabSplitPaneMap.remove(tab);
        if (splitPane != null) {
            splitPane.close();
        }
        Platform.runLater(() -> {
            if (workspace.getTabPane().getTabs().contains(tab)) {
                workspace.getTabPane().getTabs().remove(tab);
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

        SwingUtilities.invokeLater(() -> {
            try {
                TtyConnector connector = widget.getTtyConnector();
                if (connector != null && connector.isConnected()) {
                    connector.write(snippet.getCode() + "\r");
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
        com.jediterm.core.Color comboBg = theme.getColor("controls.combo.background", theme.getSelectionColor());
        com.jediterm.core.Color comboBorder = theme.getColor("controls.combo.border", theme.getBackgroundColor());
        com.jediterm.core.Color comboFg = theme.getColor("controls.combo.foreground", theme.getTextColor());
        com.jediterm.core.Color inputBg = theme.getColor("controls.input.background", theme.getSelectionColor());
        com.jediterm.core.Color inputBorder = theme.getColor("controls.input.border", theme.getBackgroundColor());
        com.jediterm.core.Color inputFg = theme.getColor("controls.input.foreground", theme.getTextColor());

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
        com.jediterm.core.Color scrollThumb = theme.getColor("scrollbar.thumb", theme.getCursorColor());
        com.jediterm.core.Color scrollThumbHover = theme.getColor("scrollbar.thumb.hover", theme.getCursorColor());
        com.jediterm.core.Color dropScrollThumb = theme.getColor("dropdown.scrollbar.thumb", theme.getSelectionColor());
        com.jediterm.core.Color dropScrollThumbHover = theme.getColor("dropdown.scrollbar.thumb.hover", theme.getCursorColor());
        com.jediterm.core.Color dialogBg = theme.getColor("dialog.background", theme.getSelectionColor());
        com.jediterm.core.Color dialogBorder = theme.getColor("dialog.border", theme.getSelectionColor());
        com.jediterm.core.Color dialogHeaderBg = theme.getColor("dialog.header.background", theme.getBackgroundColor());
        com.jediterm.core.Color dialogHeaderFg = theme.getColor("dialog.header.foreground", theme.getCursorColor());

        // Format to hex strings for CSS
        String hexWinBg = toHex(winBg);
        String hexWinBorder = toHex(winBorder);
        String hexTitleBg = toHex(titleBg);
        String hexTitleBorder = toHex(titleBorder);
        String hexToolBg = toHex(toolBg);
        String hexToolBorder = toHex(toolBorder);
        String hexSideBg = toHex(sideBg);
        String hexSideBorder = toHex(sideBorder);
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
        double scrollThumbOpacity = 0.3;
        double scrollThumbHoverOpacity = 0.6;
        double dropScrollThumbOpacity = 0.4;
        double dropScrollThumbHoverOpacity = 0.7;
        double dialogBgOpacity = 1.0;
        double dialogBorderOpacity = 1.0;
        double dialogHeaderBgOpacity = 0.5;
        double searchBarBgOpacity = 1.0;


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
            scrollThumbOpacity = Double.parseDouble(ThemeManager.getProperty("scrollbar.thumb.opacity", "0.3"));
            scrollThumbHoverOpacity = Double.parseDouble(ThemeManager.getProperty("scrollbar.thumb.hover.opacity", "0.6"));
            dropScrollThumbOpacity = Double.parseDouble(ThemeManager.getProperty("dropdown.scrollbar.thumb.opacity", "0.4"));
            dropScrollThumbHoverOpacity = Double.parseDouble(ThemeManager.getProperty("dropdown.scrollbar.thumb.hover.opacity", "0.7"));
            dialogBgOpacity = Double.parseDouble(ThemeManager.getProperty("dialog.background.opacity", "1.0"));
            dialogBorderOpacity = Double.parseDouble(ThemeManager.getProperty("dialog.border.opacity", "1.0"));
            dialogHeaderBgOpacity = Double.parseDouble(ThemeManager.getProperty("dialog.header.background.opacity", "0.5"));
            searchBarBgOpacity = Double.parseDouble(ThemeManager.getProperty("search.bar.background.opacity", "1.0"));
        } catch (Exception ignored) {}

        // Format raw colors to RGBA with parsed opacities
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
        String rgbaScrollThumb = toRgba(scrollThumb, scrollThumbOpacity);
        String rgbaScrollThumbHover = toRgba(scrollThumbHover, scrollThumbHoverOpacity);
        String rgbaDropScrollThumb = toRgba(dropScrollThumb, dropScrollThumbOpacity);
        String rgbaDropScrollThumbHover = toRgba(dropScrollThumbHover, dropScrollThumbHoverOpacity);
        String rgbaDialogBg = toRgba(dialogBg, dialogBgOpacity);
        String rgbaDialogBorder = toRgba(dialogBorder, dialogBorderOpacity);
        String rgbaDialogHeaderBg = toRgba(dialogHeaderBg, dialogHeaderBgOpacity);
        String hexDialogHeaderFg = toHex(dialogHeaderFg);

        String hexCardsBadgeFg = toHex(cardsBadgeFg);
        String hexCardsBadgeHoverFg = toHex(cardsBadgeHoverFg);
        String hexCardsBadgeSelectedFg = toHex(cardsBadgeSelectedFg);
        String rgbaCardsSelectedGlow = toRgba(cardsSelectedGlow, 0.4);

        String hexBtnBg = toHex(btnBg);
        String hexBtnBorder = toHex(btnBorder);
        String hexBtnFg = toHex(btnFg);
        String hexBtnHoverBg = toHex(btnHoverBg);
        String hexComboBg = toHex(comboBg);
        String hexComboBorder = toHex(comboBorder);
        String hexComboFg = toHex(comboFg);
        String hexInputBg = toHex(inputBg);
        String hexInputBorder = toHex(inputBorder);
        String hexInputFg = toHex(inputFg);

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
                            ".top-bar { -fx-background-color: " + hexToolBg + "; -fx-border-color: " + hexToolBorder + "; }\n" +
                            ".button { -fx-background-color: " + hexBtnBg + "; -fx-border-color: " + hexBtnBorder + "; -fx-text-fill: " + hexBtnFg + "; -fx-background-insets: 0; -fx-border-width: 1px; -fx-background-radius: 4px; -fx-border-radius: 4px; }\n" +
                            ".button:hover { -fx-background-color: " + hexBtnHoverBg + "; -fx-text-fill: #ffffff; }\n" +
                            ".button:disabled { -fx-opacity: 0.4; -fx-cursor: default; }\n" +
                            ".button.zoom-btn { -fx-padding: 3px 8px; -fx-font-size: 11px; -fx-min-width: 24px; -fx-min-height: 24px; -fx-alignment: center; -fx-background-insets: 0; -fx-border-width: 1px; -fx-background-radius: 4px; -fx-border-radius: 4px; }\n" +
                            ".combo-box { -fx-background-color: " + hexComboBg + "; -fx-text-fill: " + hexComboFg + "; -fx-border-color: " + hexComboBorder + "; }\n" +
                            ".combo-box.shell-dropdown { -fx-pref-width: 140px; -fx-max-width: 140px; }\n" +
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
                            ".context-menu { -fx-background-color: " + hexWinBg + "; -fx-border-color: " + hexWinBorder + "; }\n" +
                            ".menu-item:hover { -fx-background-color: " + hexTabHoverBg + "; }\n" +
                            ".menu-item .label { -fx-text-fill: " + hexPlusFg + "; }\n" +
                            ".menu-item:hover .label { -fx-text-fill: " + hexPlusHoverFg + "; }\n" +
                            ".label.snippets-launcher-header { -fx-text-fill: " + hexSnippetLauncherLabelColor + "; }\n" + 
                            ".app-title { -fx-font-family: \"Impact\", \"Consolas\", monospace; -fx-font-size: 20px; -fx-text-fill: #ffffff; -fx-effect: dropshadow(three-pass-box, " + hexPlusHoverFg + ", 0, 0, 2, 2); }\n" +
                            ".label { -fx-text-fill: " + hexLabelFg + "; }\n" +
                            ".dashboard-package-section { -fx-background-color: " + rgbaPkgBg + "; -fx-border-color: " + rgbaPkgBorder + "; -fx-border-width: 1px; -fx-background-radius: 8px; -fx-border-radius: 8px; -fx-padding: 15px; }\n" +
                            ".package-titled-pane { -fx-text-fill: " + hexCardsItemTitle + "; -fx-font-weight: bold; -fx-font-size: 12px; }\n" +
                            ".package-titled-pane > .title { -fx-background-color: " + rgbaCardsItemBg + "; -fx-border-color: " + rgbaCardsItemBorder + "; -fx-border-width: 1px; -fx-border-radius: 4px; -fx-background-radius: 4px; -fx-padding: 8px 12px; }\n" +
                            ".package-titled-pane > .title:hover { -fx-background-color: " + rgbaCardsHoverBg + "; -fx-border-color: " + rgbaCardsHoverBorder + "; }\n" +
                            ".package-titled-pane > .title > .arrow-button .arrow { -fx-background-color: " + hexTermCursor + "; }\n" +
                            ".package-titled-pane > .content { -fx-background-color: " + hexWinBg + "; -fx-border-color: " + hexSideBorder + "; -fx-border-width: 0 1px 1px 1px; -fx-border-radius: 0 0 4px 4px; -fx-background-radius: 0 0 4px 4px; -fx-padding: 8px; }\n" +
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
                            ".dialog-pane .radio-button .radio { -fx-mark-color: " + hexTermCursor + "; -fx-border-color: transparent; }\n";
        
        try {
            File cssFile = File.createTempFile("theme", ".css");
            cssFile.deleteOnExit();
            Files.write(cssFile.toPath(), dynamicCss.getBytes(StandardCharsets.UTF_8));
            
            if (root.getScene() != null) {
                root.getScene().getStylesheets().clear();
                root.getScene().getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
                root.getScene().getStylesheets().add(cssFile.toURI().toString());
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
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    connectToHost(item);
                    switchMode(AppMode.TERMINAL);
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
                Label folderIcon = new Label("📁");
                folderIcon.setStyle("-fx-font-size: 16px;");
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
            Label standaloneIcon = new Label("📝");
            standaloneIcon.setStyle("-fx-font-size: 16px;");
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
            VBox standaloneContainer = new VBox(8);
            standaloneContainer.setPadding(new Insets(10, 0, 10, 0));
            for (Snippet item : matchingStandalone) {
                javafx.scene.Node card = createSidebarSnippetCardNode(item);
                standaloneContainer.getChildren().add(card);
            }
            Label standaloneHeader = new Label("Standalone Snippets");
            standaloneHeader.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #94a3b8; -fx-padding: 10px 0 2px 0;");
            terminalSnippetsContainer.getChildren().addAll(standaloneHeader, standaloneContainer);
        }
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

        return card;
    }

    private String toHex(com.jediterm.core.Color color) {
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    private String toRgba(com.jediterm.core.Color color, double opacity) {
        return String.format("rgba(%d, %d, %d, %.2f)", color.getRed(), color.getGreen(), color.getBlue(), opacity);
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

    public static void main(String[] args) {
        // Fix JavaFX High-DPI maximized window shift and button shrinking bug on Windows
        System.setProperty("glass.win.minVisualBounds", "true");
        launch(args);
    }
}

