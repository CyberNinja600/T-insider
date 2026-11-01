package com.tinsider.ui.dashboard;

import com.tinsider.model.HostProfile;
import com.tinsider.ui.components.toolbar.TopToolBar;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dashboard view for Quick Connections (local shells and remote SSH hosts).
 */
public class ConnectionsDashboardView extends VBox {

    private final ObservableList<HostProfile> hosts;
    private final Consumer<TopToolBar.DropdownItem> onConnectAction;

    private final TextField connectionsSearchField;
    private final FlowPane localConnectionsGrid;
    private final FlowPane remoteConnectionsGrid;
    private final ObjectProperty<TopToolBar.DropdownItem> selectedConnectionItem = new SimpleObjectProperty<>(null);

    public ConnectionsDashboardView(ObservableList<HostProfile> hosts, Consumer<TopToolBar.DropdownItem> onConnectAction) {
        super(15);
        this.hosts = hosts;
        this.onConnectAction = onConnectAction;

        setPadding(new Insets(25));
        getStyleClass().add("dashboard-view");

        Label connectionsTitle = new Label("Quick Connections");
        connectionsTitle.getStyleClass().add("dashboard-title");
        connectionsTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        connectionsSearchField = new TextField();
        connectionsSearchField.setPromptText("🔍 Search hosts or local terminals...");
        connectionsSearchField.getStyleClass().add("text-field");
        connectionsSearchField.textProperty().addListener((obs, oldVal, newVal) -> rebuild());

        VBox connectionsContainer = new VBox(20);
        connectionsContainer.setStyle("-fx-background-color: transparent;");

        // Local terminals section
        VBox localSection = new VBox(10);
        HBox localHeader = new HBox(8);
        localHeader.setAlignment(Pos.CENTER_LEFT);
        Label localIcon = new Label("\uEA85");
        localIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 15px;");
        Label localText = new Label("Local Terminals");
        localText.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        localText.getStyleClass().add("global-text");
        localHeader.getChildren().addAll(localIcon, localText);

        localConnectionsGrid = new FlowPane(15, 15);
        localConnectionsGrid.setPadding(new Insets(10, 0, 10, 0));
        localSection.getChildren().addAll(localHeader, localConnectionsGrid);

        // Remote connections section
        VBox remoteSection = new VBox(10);
        HBox remoteHeader = new HBox(8);
        remoteHeader.setAlignment(Pos.CENTER_LEFT);
        Label remoteIcon = new Label("\uEB01");
        remoteIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 15px;");
        Label remoteText = new Label("Remote Connections");
        remoteText.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        localText.getStyleClass().add("global-text");
        remoteHeader.getChildren().addAll(remoteIcon, remoteText);

        remoteConnectionsGrid = new FlowPane(15, 15);
        remoteConnectionsGrid.setPadding(new Insets(10, 0, 10, 0));
        remoteSection.getChildren().addAll(remoteHeader, remoteConnectionsGrid);

        connectionsContainer.getChildren().addAll(localSection, remoteSection);

        ScrollPane connectionsGridScrollPane = new ScrollPane(connectionsContainer);
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
            if (onConnectAction != null && selectedConnectionItem.get() != null) {
                onConnectAction.accept(selectedConnectionItem.get());
            }
        });
        connectBtn.disableProperty().bind(selectedConnectionItem.isNull());

        getChildren().addAll(connectionsTitle, connectionsSearchField, connectionsGridScrollPane, connectionsButtons);

        hosts.addListener((ListChangeListener<HostProfile>) change -> rebuild());
        rebuild();
    }

    public void rebuild() {
        if (localConnectionsGrid == null || remoteConnectionsGrid == null) return;
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
            for (Node node : localConnectionsGrid.getChildren()) {
                node.getStyleClass().remove("selected");
            }
            for (Node node : remoteConnectionsGrid.getChildren()) {
                node.getStyleClass().remove("selected");
            }
            card.getStyleClass().add("selected");
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                if (onConnectAction != null) {
                    onConnectAction.accept(item);
                }
            }
        });

        if (selectedConnectionItem.get() != null && selectedConnectionItem.get().equals(item)) {
            card.getStyleClass().add("selected");
        }

        return card;
    }
}
