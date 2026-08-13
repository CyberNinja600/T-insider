package com.tinsider.ui.components.toolbar;

import com.tinsider.config.TerminalTheme;
import com.tinsider.model.HostProfile;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import java.util.List;
import java.util.function.Consumer;

public class TopToolBar extends HBox {
    
    public static class DropdownItem {
        private final boolean isHost;
        private final String label;
        private final HostProfile hostProfile;

        public DropdownItem(String label) {
            this.isHost = false;
            this.label = label;
            this.hostProfile = null;
        }

        public DropdownItem(HostProfile hostProfile) {
            this.isHost = true;
            this.label = hostProfile.getName();
            this.hostProfile = hostProfile;
        }

        public boolean isHost() {
            return isHost;
        }

        public String getLabel() {
            return label;
        }

        public HostProfile getHostProfile() {
            return hostProfile;
        }

        @Override
        public String toString() {
            return label;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DropdownItem that = (DropdownItem) o;
            if (isHost != that.isHost) return false;
            if (isHost) {
                return hostProfile != null && that.hostProfile != null &&
                        hostProfile.getId() != null && hostProfile.getId().equals(that.hostProfile.getId());
            } else {
                return label.equals(that.label);
            }
        }

        @Override
        public int hashCode() {
            int result = (isHost ? 1 : 0);
            result = 31 * result + (label != null ? label.hashCode() : 0);
            if (isHost && hostProfile != null && hostProfile.getId() != null) {
                result = 31 * result + hostProfile.getId().hashCode();
            }
            return result;
        }
    }

    private final ComboBox<DropdownItem> shellOptions;
    private final ComboBox<String> themeOptions;
    private final Label selectLabel;
    private final Button toggleSidebarBtn;
    private final Button homeBtn;
    private final Label zoomLabel;
    private final HBox zoomControls;
    private Button zoomResetBtn;

    public TopToolBar(
            List<TerminalTheme> availableThemes, 
            String initialThemeName, 
            String initialShell,
            ObservableList<HostProfile> hosts,
            Consumer<TerminalTheme> onThemeChanged,
            Consumer<String> onShellChanged,
            Runnable onZoomIn,
            Runnable onZoomOut,
            Runnable onZoomReset,
            Runnable onToggleSidebar,
            Runnable onHome) {
        
        super(10);
        setPadding(new Insets(10));
        setAlignment(Pos.CENTER_LEFT);
        getStyleClass().add("top-bar");

        selectLabel = new Label("Shell / Host:");
        selectLabel.managedProperty().bind(selectLabel.visibleProperty());

        shellOptions = new ComboBox<>();
        shellOptions.getStyleClass().add("shell-dropdown");
        shellOptions.managedProperty().bind(shellOptions.visibleProperty());

        rebuildItems(hosts, initialShell);

        shellOptions.setOnAction(e -> {
            DropdownItem selected = shellOptions.getValue();
            if (selected != null && !selected.isHost() && onShellChanged != null) {
                onShellChanged.accept(selected.getLabel());
            }
        });

        hosts.addListener((javafx.collections.ListChangeListener<HostProfile>) change -> {
            rebuildItems(hosts, initialShell);
        });

        Label themeLabel = new Label("Theme:");
        themeOptions = new ComboBox<>();
        themeOptions.getStyleClass().add("theme-dropdown");
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

        zoomLabel = new Label("Zoom:");
        zoomLabel.managedProperty().bind(zoomLabel.visibleProperty());
        zoomLabel.setVisible(false); // Initially hidden since app starts in Dashboard Mode

        Button zoomOutBtn = new Button("-");
        zoomResetBtn = new Button("100%");
        Button zoomInBtn = new Button("+");

        zoomOutBtn.getStyleClass().add("zoom-btn");
        zoomResetBtn.getStyleClass().add("zoom-btn");
        zoomResetBtn.getStyleClass().add("zoom-reset-btn");
        zoomInBtn.getStyleClass().add("zoom-btn");

        zoomOutBtn.setOnAction(e -> {
            if (onZoomOut != null) onZoomOut.run();
        });
        zoomResetBtn.setOnAction(e -> {
            if (onZoomReset != null) onZoomReset.run();
        });
        zoomInBtn.setOnAction(e -> {
            if (onZoomIn != null) onZoomIn.run();
        });

        zoomControls = new HBox(5, zoomOutBtn, zoomResetBtn, zoomInBtn);
        zoomControls.setAlignment(Pos.CENTER_LEFT);
        zoomControls.managedProperty().bind(zoomControls.visibleProperty());
        zoomControls.setVisible(false); // Initially hidden since app starts in Dashboard Mode

        // Home navigation button
        homeBtn = new Button("\uEB06");
        homeBtn.getStyleClass().add("home-btn");
        homeBtn.managedProperty().bind(homeBtn.visibleProperty());
        homeBtn.setVisible(false); // Initially hidden since app starts in Dashboard Mode
        homeBtn.setOnAction(e -> {
            if (onHome != null) onHome.run();
        });

        // Sidebar visibility toggle button
        toggleSidebarBtn = new Button("\uEC02");
        toggleSidebarBtn.getStyleClass().add("sidebar-toggle-btn");
        toggleSidebarBtn.managedProperty().bind(toggleSidebarBtn.visibleProperty());
        toggleSidebarBtn.setVisible(false); // Initially hidden since app starts in Dashboard Mode
        toggleSidebarBtn.setOnAction(e -> {
            if (onToggleSidebar != null) onToggleSidebar.run();
        });

        javafx.scene.layout.Pane spacer = new javafx.scene.layout.Pane();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        getChildren().addAll(selectLabel, shellOptions, themeLabel, themeOptions, spacer, homeBtn, toggleSidebarBtn, zoomLabel, zoomControls);
    }

    private void rebuildItems(ObservableList<HostProfile> hosts, String initialShell) {
        DropdownItem previousSelection = shellOptions.getValue();

        shellOptions.getItems().clear();

        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        if (isWindows) {
            shellOptions.getItems().add(new DropdownItem("CMD"));
            shellOptions.getItems().add(new DropdownItem("PowerShell"));
            if (isBashAvailableOnWindows()) {
                shellOptions.getItems().add(new DropdownItem("Bash"));
            }
        } else {
            shellOptions.getItems().add(new DropdownItem("Bash"));
        }

        for (HostProfile host : hosts) {
            shellOptions.getItems().add(new DropdownItem(host));
        }

        if (previousSelection != null) {
            boolean restored = false;
            for (DropdownItem item : shellOptions.getItems()) {
                if (item.equals(previousSelection)) {
                    shellOptions.setValue(item);
                    restored = true;
                    break;
                }
            }
            if (!restored) {
                selectDefault(initialShell);
            }
        } else {
            selectDefault(initialShell);
        }
    }

    public static boolean isBashAvailableOnWindows() {
        // 1. Check C:\Program Files\Git\bin\bash.exe
        if (new java.io.File("C:\\Program Files\\Git\\bin\\bash.exe").exists()) return true;

        // 2. Check C:\Program Files (x86)\Git\bin\bash.exe
        if (new java.io.File("C:\\Program Files (x86)\\Git\\bin\\bash.exe").exists()) return true;

        // 3. Check user-local AppData Git Bash
        String userHome = System.getProperty("user.home");
        if (userHome != null) {
            if (new java.io.File(userHome + "\\AppData\\Local\\Programs\\Git\\bin\\bash.exe").exists()) return true;
        }

        // 4. Try to resolve via environment PATH of git.exe or bash.exe
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String part : pathEnv.split(java.io.File.pathSeparator)) {
                if (new java.io.File(part, "git.exe").exists()) {
                    java.io.File parent = new java.io.File(part).getParentFile();
                    if (parent != null) {
                        if (new java.io.File(parent, "bin\\bash.exe").exists() || 
                            new java.io.File(parent, "usr\\bin\\bash.exe").exists()) {
                            return true;
                        }
                    }
                }
                if (new java.io.File(part, "bash.exe").exists()) {
                    return true;
                }
            }
        }

        // 5. Check if WSL is available
        if (new java.io.File("C:\\Windows\\System32\\bash.exe").exists()) return true;

        return false;
    }

    private void selectDefault(String initialShell) {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String shellToSelect = initialShell != null ? initialShell : (isWindows ? "CMD" : "Bash");
        for (DropdownItem item : shellOptions.getItems()) {
            if (!item.isHost() && item.getLabel().equalsIgnoreCase(shellToSelect)) {
                shellOptions.setValue(item);
                return;
            }
        }
        if (!shellOptions.getItems().isEmpty()) {
            shellOptions.setValue(shellOptions.getItems().get(0));
        }
    }

    public void setShellSelectionVisible(boolean visible) {
        System.out.println("[TopToolBar] setShellSelectionVisible called with: " + visible);
        selectLabel.setVisible(visible);
        shellOptions.setVisible(visible);
    }

    public void setSidebarToggleVisible(boolean visible) {
        toggleSidebarBtn.setVisible(visible);
        homeBtn.setVisible(visible);
    }

    public void setZoomVisible(boolean visible) {
        zoomLabel.setVisible(visible);
        zoomControls.setVisible(visible);
    }

    public void setSidebarButtonText(String text) {
        if ("Hide Sidebar".equalsIgnoreCase(text)) {
            toggleSidebarBtn.setText("\uEBF3");
        } else {
            toggleSidebarBtn.setText("\uEC02");
        }
    }

    public DropdownItem getSelectedItem() {
        return shellOptions.getValue();
    }

    public String getSelectedShell() {
        DropdownItem selected = shellOptions.getValue();
        return (selected != null) ? selected.getLabel() : null;
    }

    public String getSelectedTheme() {
        return themeOptions.getValue();
    }

    public void selectShellOrHost(String type, HostProfile host) {
        for (DropdownItem item : shellOptions.getItems()) {
            if (host != null && item.isHost() && item.getHostProfile() != null && 
                host.getId() != null && host.getId().equals(item.getHostProfile().getId())) {
                shellOptions.setValue(item);
                return;
            } else if (host == null && !item.isHost() && item.getLabel().equalsIgnoreCase(type)) {
                shellOptions.setValue(item);
                return;
            }
        }
    }

    public void updateZoomLabel(float fontSize) {
        int percent = Math.round((fontSize / 13.0f) * 100);
        if (zoomResetBtn != null) {
            zoomResetBtn.setText(percent + "%");
        }
    }
}
